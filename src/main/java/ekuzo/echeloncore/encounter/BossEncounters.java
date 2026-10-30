package ekuzo.echeloncore.encounter;

import ekuzo.crazyworldprogression.loot.LootPoolService;
import ekuzo.crazyworldprogression.worldboundary.restriction.Restriction;
import ekuzo.crazyworldprogression.worldboundary.restriction.RestrictionService;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.encounter.BossEncounterData.Boss;
import ekuzo.echeloncore.encounter.BossEncounterData.Encounter;
import ekuzo.echeloncore.encounter.BossEncounterData.State;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Boss encounters: bosses placed in an area, with the Fallen Star's rules for fighting in an area and the Veil
 * anomalies' boundary to keep leavers out. Each encounter owns its area: a Veil anomaly it creates, moves and deletes
 * itself ({@link #areaId}), hidden from the anomaly lists.
 *
 * <ul>
 *     <li>The area is hidden and passable for everyone and protects its blocks. Entering it shows the encounter's
 *     name.</li>
 *     <li>Bosses only fight players inside the area: they drop targets outside it, walk back when they stray, and
 *     players outside cannot hurt them (like the Fallen Star's guards).</li>
 *     <li>The fight starts once a player inside comes within the aggro radius of a boss or hurts one; until then
 *     the bosses leave everyone alone. During the fight each boss has a boss bar for the players inside, and whoever
 *     leaves the area cannot come back in: the anomaly turns solid and visible for that player only, and pushes them
 *     away like any solid anomaly.</li>
 *     <li>Once nobody is inside, the encounter resets: bosses come back at full health and everyone may enter.</li>
 *     <li>When all its bosses are dead, every player inside gets the encounter's loot pools. The encounter is then
 *     cleared: the area stays open and safe, and the bosses come back after its respawn time, once nobody is inside.</li>
 * </ul>
 */
public final class BossEncounters {
    /** Tag on every spawned boss; one no encounter knows any more is removed when it loads. */
    static final String TAG = "echelon_encounter_boss";
    /** Path prefix of the anomalies encounters own. */
    public static final String AREA_PREFIX = "encounter/";
    /** The area as everyone else sees it. */
    static final Restriction.RestrictionState OPEN = new Restriction.RestrictionState(0, Restriction.Collision.PASSABLE,
            Restriction.Visibility.HIDDEN, Restriction.BlockInteraction.PROTECTED);
    /** The area as a player who left a fight sees it. */
    private static final Restriction.RestrictionState SHUT = new Restriction.RestrictionState(0, Restriction.Collision.SOLID,
            Restriction.Visibility.TRANSLUCENT, Restriction.BlockInteraction.PROTECTED);
    private static final double HALF_WIDTH = 0.3, HALF_HEIGHT = 0.9;
    /** Blocks past the area's edge a boss may stray before it is put back where it stands. */
    private static final double STRAY = 8.0;

    /** Players inside each encounter's area (for the entry title, and in a fight the ones locked out on leaving). */
    private static final Map<Identifier, Set<UUID>> INSIDE = new HashMap<>();
    /** Players in each fight: those inside when it started or who came in since. */
    private static final Map<Identifier, Set<UUID>> PRESENT = new HashMap<>();
    /** Players locked out of each fight. */
    private static final Map<Identifier, Set<UUID>> LOCKED = new HashMap<>();
    /** Boss slots killed in each fight (not saved: a restart resets a fight anyway). */
    private static final Map<Identifier, Set<Integer>> DEAD = new HashMap<>();
    /** Which encounter each spawned boss belongs to. */
    private static final Map<UUID, Identifier> BOSSES = new HashMap<>();
    /** Each fighting boss's bar. */
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private BossEncounters() { }

    public static void register() {
        BossEncounterMap.register();
        BossSpawnHelpers.register();
        ServerLifecycleEvents.SERVER_STARTED.register(BossEncounters::started);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            INSIDE.clear();
            PRESENT.clear();
            LOCKED.clear();
            DEAD.clear();
            BOSSES.clear();
            BARS.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BossEncounterData data = BossEncounterData.get(server);
            for (Encounter encounter : List.copyOf(data.all().values())) {
                try { tick(server, data, encounter); }
                catch (RuntimeException exception) {
                    if (server.getTickCount() % 600 == 0)
                        EchelonCore.LOGGER.warn("Boss encounter {} failed to tick", encounter.id, exception);
                }
            }
        });
        // Players outside the area cannot hurt its bosses (their arrows neither).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Identifier id = BOSSES.get(entity.getUUID());
            if (id == null || !(source.getEntity() instanceof Player player)) return true;
            Restriction area = area(entity.level().getServer(), id);
            return area == null || inside(area, player);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            Identifier id = BOSSES.get(entity.getUUID());
            if (id != null && source.getEntity() instanceof ServerPlayer && taken > 0) engage(entity.level().getServer(), id);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Identifier id = BOSSES.remove(entity.getUUID());
            removeBar(entity.getUUID());
            if (id == null) return;
            MinecraftServer server = entity.level().getServer();
            BossEncounterData data = BossEncounterData.get(server);
            Encounter encounter = data.get(id);
            if (encounter == null) return;
            int slot = encounter.spawned.indexOf(entity.getUUID());
            if (slot < 0) return;
            encounter.spawned.set(slot, null);
            data.changed();
            // Killed in one blow: the fight started with that blow.
            if (source.getEntity() instanceof ServerPlayer) engage(server, id);
            // Outside a fight (lava, a command) the boss simply comes back; in a fight it stays dead until a reset.
            if (encounter.state != State.ACTIVE) return;
            dead(encounter).add(slot);
            if (dead(encounter).size() >= encounter.bosses.size()) clear(server, data, encounter);
        });
        // A boss nobody knows any more (its encounter was deleted or reset while it was unloaded) goes away.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !BOSSES.containsKey(entity.getUUID())) entity.discard();
        });
    }

    private static Set<Integer> dead(Encounter encounter) { return DEAD.computeIfAbsent(encounter.id, id -> new HashSet<>()); }

    // ---------------------------------------------------------------- lifecycle

    private static void started(MinecraftServer server) {
        BossEncounterData data = BossEncounterData.get(server);
        for (Encounter encounter : data.all().values()) {
            encounter.fitSpawned();
            for (UUID boss : encounter.spawned) if (boss != null) BOSSES.put(boss, encounter.id);
            // A fight does not survive a restart: start over with fresh bosses.
            if (encounter.state == State.ACTIVE) reset(server, data, encounter);
        }
    }

    private static void tick(MinecraftServer server, BossEncounterData data, Encounter encounter) {
        Restriction area = RestrictionService.get(server, areaId(encounter.id));
        if (area == null) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, area.dimension()));
        if (level == null) return;
        controlBosses(level, encounter, area);
        if (server.getTickCount() % 5 != 0) return;
        List<ServerPlayer> inside = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator() && inside(area, player)).toList();
        greet(encounter, inside);
        switch (encounter.state) {
            case IDLE -> {
                spawnMissing(level, data, encounter);
                for (Mob boss : bosses(level, encounter)) {
                    if (inside.stream().anyMatch(player -> !player.isCreative()
                            && player.distanceTo(boss) <= encounter.aggroRadius)) {
                        engage(server, encounter.id);
                        break;
                    }
                }
            }
            case ACTIVE -> {
                Set<UUID> present = PRESENT.computeIfAbsent(encounter.id, id -> new HashSet<>());
                Set<UUID> now = new HashSet<>();
                inside.forEach(player -> now.add(player.getUUID()));
                for (UUID left : List.copyOf(present)) if (!now.contains(left)) lockOut(server, encounter, left);
                present.retainAll(now);
                present.addAll(now);
                updateBars(level, encounter, inside);
                if (inside.isEmpty()) reset(server, data, encounter);
            }
            case CLEARED -> {
                if (level.getGameTime() < encounter.clearedUntil || !inside.isEmpty()) return;
                encounter.state = State.IDLE;
                data.changed();
                spawnMissing(level, data, encounter);
            }
        }
    }

    /** The encounter's name on screen, once each time a player comes into its area. */
    private static void greet(Encounter encounter, List<ServerPlayer> inside) {
        Set<UUID> before = INSIDE.computeIfAbsent(encounter.id, id -> new HashSet<>());
        Set<UUID> now = new HashSet<>();
        for (ServerPlayer player : inside) {
            now.add(player.getUUID());
            if (before.contains(player.getUUID())) continue;
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                    "title.echelon-core.encounter.entered").withStyle(ChatFormatting.GRAY)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(encounter.displayName())
                    .withStyle(ChatFormatting.DARK_PURPLE)));
        }
        before.clear();
        before.addAll(now);
    }

    /** The fight starts: everyone inside now is in it. */
    static void engage(MinecraftServer server, Identifier id) {
        BossEncounterData data = BossEncounterData.get(server);
        Encounter encounter = data.get(id);
        if (encounter == null || encounter.state != State.IDLE) return;
        Restriction area = area(server, id);
        if (area == null) return;
        encounter.state = State.ACTIVE;
        data.changed();
        Set<UUID> present = PRESENT.computeIfAbsent(id, key -> new HashSet<>());
        present.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && inside(area, player)) {
                present.add(player.getUUID());
                player.sendSystemMessage(Component.translatable("message.echelon-core.encounter.engaged")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
    }

    /** A player left the fight: the area shuts for them until it resets. */
    private static void lockOut(MinecraftServer server, Encounter encounter, UUID player) {
        LOCKED.computeIfAbsent(encounter.id, id -> new HashSet<>()).add(player);
        RestrictionService.setPlayerState(server, player, areaId(encounter.id), SHUT);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            BARS.values().forEach(bar -> bar.removePlayer(online));
            online.sendSystemMessage(Component.translatable("message.echelon-core.encounter.locked_out")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Bosses back at full health, lockouts lifted. */
    static void reset(MinecraftServer server, BossEncounterData data, Encounter encounter) {
        for (int slot = 0; slot < encounter.spawned.size(); slot++) {
            UUID boss = encounter.spawned.get(slot);
            if (boss == null) continue;
            BOSSES.remove(boss);
            removeBar(boss);
            Entity entity = find(server, boss);
            if (entity != null) entity.discard();
            encounter.spawned.set(slot, null);
        }
        open(server, encounter);
        encounter.state = State.IDLE;
        data.changed();
    }

    /** All bosses dead: the players inside get the loot pools, and the encounter waits out its respawn time. */
    private static void clear(MinecraftServer server, BossEncounterData data, Encounter encounter) {
        Restriction area = area(server, encounter.id);
        List<ServerPlayer> inside = area == null ? List.of() : server.getPlayerList().getPlayers().stream()
                .filter(player -> player.isAlive() && !player.isSpectator() && inside(area, player)).toList();
        for (ServerPlayer player : inside) {
            player.sendSystemMessage(Component.translatable("message.echelon-core.encounter.cleared",
                    encounter.displayName()).withStyle(ChatFormatting.GOLD));
            for (Identifier pool : encounter.lootPools) {
                if (LootPoolService.get(server, pool) == null) continue;
                try { LootPoolService.roll(server, player, pool); }
                catch (RuntimeException exception) {
                    EchelonCore.LOGGER.warn("Boss encounter {}: could not roll loot pool {}", encounter.id, pool, exception);
                }
            }
        }
        open(server, encounter);
        encounter.state = State.CLEARED;
        encounter.clearedUntil = server.overworld().getGameTime() + encounter.respawnSeconds * 20L;
        data.changed();
    }

    /** Everyone may come in again. */
    private static void open(MinecraftServer server, Encounter encounter) {
        RestrictionService.clearPlayerStates(server, areaId(encounter.id));
        LOCKED.remove(encounter.id);
        PRESENT.remove(encounter.id);
        DEAD.remove(encounter.id);
    }

    // ---------------------------------------------------------------- bosses

    /** Spawns the bosses that are missing (where their spot is loaded). */
    private static void spawnMissing(ServerLevel level, BossEncounterData data, Encounter encounter) {
        encounter.fitSpawned();
        for (int slot = 0; slot < encounter.bosses.size(); slot++) {
            Boss boss = encounter.bosses.get(slot);
            if (dead(encounter).contains(slot) || !level.isLoaded(BlockPos.containing(boss.x(), boss.y(), boss.z()))) continue;
            UUID existing = encounter.spawned.get(slot);
            if (existing != null && level.getEntity(existing) != null) continue;
            if (existing != null) BOSSES.remove(existing);
            Entity spawned = spawn(level, boss, encounter.id);
            encounter.spawned.set(slot, spawned == null ? null : spawned.getUUID());
            data.changed();
        }
    }

    private static Entity spawn(ServerLevel level, Boss boss, Identifier encounter) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(boss.type()).orElse(null);
        if (type == null) return null;
        Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity == null) return null;
        entity.snapTo(boss.x(), boss.y(), boss.z(), boss.yaw(), 0.0F);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            mob.setPersistenceRequired();
            mob.setYHeadRot(boss.yaw());
            mob.setYBodyRot(boss.yaw());
        }
        entity.addTag(TAG);
        // Known before it loads, so the clean-up of unknown bosses leaves it be.
        BOSSES.put(entity.getUUID(), encounter);
        if (!level.addFreshEntity(entity)) {
            BOSSES.remove(entity.getUUID());
            return null;
        }
        return entity;
    }

    /**
     * Bosses only fight players inside the area (before the fight, nobody at all: whoever comes close or hits one
     * starts it), and stay in it.
     */
    private static void controlBosses(ServerLevel level, Encounter encounter, Restriction area) {
        for (int slot = 0; slot < encounter.spawned.size() && slot < encounter.bosses.size(); slot++) {
            UUID id = encounter.spawned.get(slot);
            if (id == null || !(level.getEntity(id) instanceof Mob mob) || !mob.isAlive()) continue;
            LivingEntity target = mob.getTarget();
            if (target != null && (encounter.state != State.ACTIVE || !inside(area, target))) mob.setTarget(null);
            if (area.contains(mob.position(), 0.0, 0.0)) continue;
            Boss home = encounter.bosses.get(slot);
            if (area.contains(mob.position(), STRAY, STRAY)) {
                if (mob.tickCount % 20 == 0) mob.getNavigation().moveTo(home.x(), home.y(), home.z(), 1.2);
            } else {
                mob.teleportTo(home.x(), home.y(), home.z());
            }
        }
    }

    /** One bar per living boss, shown to the players inside. */
    private static void updateBars(ServerLevel level, Encounter encounter, List<ServerPlayer> inside) {
        for (Mob boss : bosses(level, encounter)) {
            ServerBossEvent bar = BARS.computeIfAbsent(boss.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(),
                    boss.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10));
            bar.setName(boss.getDisplayName());
            bar.setProgress(boss.getHealth() / boss.getMaxHealth());
            for (ServerPlayer player : List.copyOf(bar.getPlayers())) if (!inside.contains(player)) bar.removePlayer(player);
            for (ServerPlayer player : inside) if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        }
    }

    private static void removeBar(UUID boss) {
        ServerBossEvent bar = BARS.remove(boss);
        if (bar != null) bar.removeAllPlayers();
    }

    private static List<Mob> bosses(ServerLevel level, Encounter encounter) {
        List<Mob> out = new ArrayList<>();
        for (UUID id : encounter.spawned)
            if (id != null && level.getEntity(id) instanceof Mob mob && mob.isAlive()) out.add(mob);
        return out;
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    // ---------------------------------------------------------------- the area

    /** The ID of the anomaly an encounter owns as its area. */
    static Identifier areaId(Identifier encounter) {
        return Identifier.fromNamespaceAndPath(encounter.getNamespace(), AREA_PREFIX + encounter.getPath());
    }

    /** True for anomalies owned by an encounter (kept out of the anomaly lists and commands). */
    public static boolean ownsAnomaly(Identifier anomaly) { return anomaly.getPath().startsWith(AREA_PREFIX); }

    /** The encounter's area, or null when it (or the encounter) is gone. */
    static Restriction area(MinecraftServer server, Identifier encounterId) {
        return BossEncounterData.get(server).get(encounterId) == null ? null : RestrictionService.get(server, areaId(encounterId));
    }

    /** True when the entity's body center is inside the area (the same point anomalies test players by). */
    static boolean inside(Restriction area, Entity entity) {
        if (!entity.level().dimension().identifier().equals(area.dimension())) return false;
        return area.contains(entity.position().add(0, Math.min(HALF_HEIGHT, entity.getBbHeight() / 2), 0), HALF_WIDTH, 0);
    }

    /** An area anomaly: hidden, passable, blocks protected, keep-out for those locked out. */
    private static Restriction areaOf(Identifier encounter, Identifier dimension, Restriction.Shape shape, Vec3 center,
                                      double sizeX, double sizeY, double sizeZ) {
        return new Restriction(areaId(encounter), dimension, shape, center.x, center.y, center.z, sizeX, sizeY, sizeZ,
                Restriction.BoundaryMode.KEEP_OUT, List.of(OPEN));
    }

    // ---------------------------------------------------------------- admin

    static List<Encounter> list(MinecraftServer server) { return List.copyOf(BossEncounterData.get(server).all().values()); }

    static Encounter get(MinecraftServer server, Identifier id) { return BossEncounterData.get(server).get(id); }

    /** Creates an encounter and its area around {@code center}. Sphere and cylinder use {@code sizeX} as radius. */
    static Encounter create(MinecraftServer server, Identifier id, String name, Identifier dimension, Restriction.Shape shape,
                            Vec3 center, double sizeX, double sizeY, double sizeZ) {
        BossEncounterData data = BossEncounterData.get(server);
        if (data.get(id) != null) throw new IllegalArgumentException("Encounter already exists: " + id);
        Restriction area = areaOf(id, dimension, shape, center, sizeX, sizeY, sizeZ);
        if (RestrictionService.get(server, area.id()) != null) RestrictionService.update(server, area);
        else RestrictionService.create(server, area);
        Encounter encounter = new Encounter(id, name);
        data.put(encounter);
        return encounter;
    }

    /** Changes the area's shape, size, center or world (resets a fight). */
    static void setArea(MinecraftServer server, Encounter encounter, Identifier dimension, Restriction.Shape shape,
                        Vec3 center, double sizeX, double sizeY, double sizeZ) {
        BossEncounterData data = BossEncounterData.get(server);
        Restriction current = RestrictionService.get(server, areaId(encounter.id));
        Restriction area = areaOf(encounter.id, dimension, shape, center, sizeX, sizeY, sizeZ);
        if (current != null && current.equals(area)) return;
        reset(server, data, encounter);
        if (current != null) RestrictionService.update(server, area);
        else RestrictionService.create(server, area);
        data.changed();
    }

    static boolean delete(MinecraftServer server, Identifier id) {
        BossEncounterData data = BossEncounterData.get(server);
        Encounter encounter = data.get(id);
        if (encounter == null) return false;
        reset(server, data, encounter);
        data.remove(id);
        INSIDE.remove(id);
        RestrictionService.remove(server, areaId(id));
        return true;
    }

    static void setName(MinecraftServer server, Encounter encounter, String name) {
        encounter.name = name.trim();
        BossEncounterData.get(server).changed();
    }

    static void addBoss(MinecraftServer server, Encounter encounter, Boss boss) {
        BossEncounterData data = BossEncounterData.get(server);
        reset(server, data, encounter);
        encounter.bosses.add(boss);
        encounter.fitSpawned();
        data.changed();
    }

    static boolean removeBoss(MinecraftServer server, Encounter encounter, int index) {
        if (index < 0 || index >= encounter.bosses.size()) return false;
        BossEncounterData data = BossEncounterData.get(server);
        reset(server, data, encounter);
        encounter.bosses.remove(index);
        encounter.fitSpawned();
        data.changed();
        return true;
    }

    static boolean addLootPool(MinecraftServer server, Encounter encounter, Identifier pool) {
        if (encounter.lootPools.contains(pool)) return false;
        encounter.lootPools.add(pool);
        BossEncounterData.get(server).changed();
        return true;
    }

    static boolean removeLootPool(MinecraftServer server, Encounter encounter, Identifier pool) {
        boolean removed = encounter.lootPools.remove(pool);
        if (removed) BossEncounterData.get(server).changed();
        return removed;
    }

    static void setRespawn(MinecraftServer server, Encounter encounter, int seconds) {
        encounter.respawnSeconds = seconds;
        BossEncounterData.get(server).changed();
    }

    static void setAggroRadius(MinecraftServer server, Encounter encounter, double radius) {
        encounter.aggroRadius = radius;
        BossEncounterData.get(server).changed();
    }

    /** Resets a fight or ends a cleared encounter's wait: fresh bosses at once. */
    static void forceReset(MinecraftServer server, Encounter encounter) {
        BossEncounterData data = BossEncounterData.get(server);
        reset(server, data, encounter);
        encounter.clearedUntil = 0;
    }

    static int locked(Identifier id) { return LOCKED.getOrDefault(id, Set.of()).size(); }

    static int present(Identifier id) { return PRESENT.getOrDefault(id, Set.of()).size(); }
}
