package ekuzo.echeloncore.entity;

import com.mojang.brigadier.Command;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.ability.ShadowStep;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Falsekin haunts: each one is a player the Falsekin has chosen. The haunt keeps the stalking progress (power) and the
 * Mass Decoys used; the creature itself only exists while it is near its player.
 *
 * <p>While no Falsekin of the haunt is around, it now and then ({@link #APPEAR_CHANCE} per second after a
 * {@link #COOLDOWN}) appears near its player, out of sight: next to an existing herd of wearable mobs if there is one
 * (preferred), otherwise as a lone mob that fits the biome. If the player leaves it behind and it despawns or unloads,
 * its progress pauses until it appears again.
 *
 * <p>Inside a divine field (CWP BonusAreas: the spawn field and divine spots) or with {@link #CROWD} other players within
 * {@link #CROWD_RADIUS} blocks the player is protected: the Falsekin disengages and does not appear. A haunt whose
 * player stays protected for {@link #FORGET_MILLIS} (3 minutes) is forgotten.
 */
public final class FalsekinHaunts extends SavedData {
    public static final double CROWD_RADIUS = 24.0;
    /** Other players near its player that make it leave: it hunts alone people only. */
    public static final int CROWD = 1;
    public static final long FORGET_MILLIS = 180_000L;
    /** Ticks before it may appear again after leaving or being left behind. */
    static final int COOLDOWN = 400;
    private static final int APPEAR_CHANCE = 30;

    /** One haunted player. */
    public static final class Haunt {
        static final Codec<Haunt> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.STRING_CODEC.fieldOf("target").forGetter(h -> h.target),
                Codec.INT.optionalFieldOf("power", 0).forGetter(h -> h.power),
                Codec.INT.optionalFieldOf("decoys", 0).forGetter(h -> h.decoys),
                Codec.LONG.optionalFieldOf("protected_since", 0L).forGetter(h -> h.protectedSince)
        ).apply(instance, Haunt::new));

        public final UUID target;
        /** Stalking progress in ticks, up to Falsekin.MAX_POWER. */
        public int power;
        /** Mass Decoys used while stalking (it gets two). */
        public int decoys;
        /** When the player became protected (epoch millis), 0 while not protected. */
        long protectedSince;
        int cooldown = COOLDOWN;
        private WeakReference<Falsekin> entity = new WeakReference<>(null);

        Haunt(UUID target, int power, int decoys, long protectedSince) {
            this.target = target;
            this.power = power;
            this.decoys = decoys;
            this.protectedSince = protectedSince;
        }

        public Falsekin entity() {
            Falsekin falsekin = entity.get();
            return falsekin != null && falsekin.isAlive() && !falsekin.isRemoved() ? falsekin : null;
        }

        void bind(Falsekin falsekin) { entity = new WeakReference<>(falsekin); }
    }

    static final Codec<FalsekinHaunts> CODEC = Haunt.CODEC.listOf().optionalFieldOf("haunts", List.of())
            .xmap(FalsekinHaunts::new, data -> List.copyOf(data.haunts.values())).codec();
    private static final SavedDataType<FalsekinHaunts> TYPE = new SavedDataType<>(EchelonCore.id("falsekin_haunts"),
            FalsekinHaunts::new, CODEC, null);
    private final Map<UUID, Haunt> haunts = new HashMap<>();

    private FalsekinHaunts() { }

    private FalsekinHaunts(List<Haunt> list) { list.forEach(h -> haunts.put(h.target, h)); }

    public static FalsekinHaunts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Haunt haunt(UUID target) { return haunts.get(target); }

    /** Starts haunting {@code player} (keeps an existing haunt). */
    public Haunt start(UUID player) {
        setDirty();
        return haunts.computeIfAbsent(player, id -> new Haunt(id, 0, 0, 0L));
    }

    public void end(UUID player) {
        Haunt haunt = haunts.remove(player);
        if (haunt != null && haunt.entity() != null) haunt.entity().discard();
        setDirty();
    }

    public void changed() { setDirty(); }

    /** Ends a haunt without removing its Falsekin (it leaves on its own). */
    private Haunt forget(UUID player) {
        setDirty();
        return haunts.remove(player);
    }

    /** Claims the haunt for {@code falsekin}; false when another Falsekin of the same haunt is already out. */
    boolean claim(Haunt haunt, Falsekin falsekin) {
        Falsekin current = haunt.entity();
        if (current != null && current != falsekin) return false;
        haunt.bind(falsekin);
        return true;
    }

    // ---------------------------------------------------------------- protection

    /** In a divine field (CWP's safe havens) or with another player nearby: the Falsekin leaves this player alone. */
    public static boolean protectedPlayer(ServerPlayer player) {
        if (ekuzo.crazyworldprogression.worldboundary.bonus.BonusAreas.contains(player)) return true;
        int others = 0;
        for (ServerPlayer other : player.level().players())
            if (other != player && !other.isSpectator() && other.distanceTo(player) <= CROWD_RADIUS) others++;
        return others >= CROWD;
    }

    // ---------------------------------------------------------------- appearing

    public static void initialize() {
        // The haunted player died (to the Falsekin or anything else): it heals, runs off, and the haunt is over.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            Haunt haunt = get(player.level().getServer()).forget(player.getUUID());
            if (haunt == null) return;
            Falsekin falsekin = haunt.entity();
            if (falsekin != null && falsekin.level() instanceof ServerLevel level) falsekin.afterPlayerDeath(level, player.position());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) get(server).tick(server);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("falsekin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("haunt").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                            Haunt haunt = get(context.getSource().getServer()).start(player.getUUID());
                            haunt.cooldown = 0;
                            context.getSource().sendSuccess(() -> Component.literal("The Falsekin now haunts "
                                    + player.getGameProfile().name()), true);
                            return Command.SINGLE_SUCCESS;
                        })))
                        .then(Commands.literal("stop").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                            get(context.getSource().getServer()).end(player.getUUID());
                            context.getSource().sendSuccess(() -> Component.literal("The Falsekin stopped haunting "
                                    + player.getGameProfile().name()), true);
                            return Command.SINGLE_SUCCESS;
                        })))
                        .then(Commands.literal("list").executes(context -> {
                            MinecraftServer server = context.getSource().getServer();
                            var data = get(server);
                            if (data.haunts.isEmpty()) context.getSource().sendSuccess(() -> Component.literal("Nobody is haunted"), false);
                            for (Haunt haunt : data.haunts.values()) {
                                ServerPlayer player = server.getPlayerList().getPlayer(haunt.target);
                                String name = player == null ? haunt.target + " (offline)" : player.getGameProfile().name();
                                context.getSource().sendSuccess(() -> Component.literal(name + ": " + (haunt.power * 100 / Falsekin.MAX_POWER)
                                        + "% power, " + haunt.decoys + " mass decoys used, " + (haunt.entity() != null ? "out" : "hidden")), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    private void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Haunt haunt : List.copyOf(haunts.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(haunt.target);
            if (player == null) continue;                                         // offline: paused
            if (protectedPlayer(player)) {
                if (haunt.protectedSince == 0) {
                    haunt.protectedSince = now;
                    setDirty();
                } else if (now - haunt.protectedSince > FORGET_MILLIS) {
                    end(haunt.target);
                }
                continue;
            }
            if (haunt.protectedSince != 0) {
                haunt.protectedSince = 0;
                setDirty();
            }
            if (haunt.entity() != null || player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            if ((haunt.cooldown -= 20) > 0) continue;
            if (player.getRandom().nextInt(APPEAR_CHANCE) == 0) appear((ServerLevel) player.level(), player, haunt);
        }
    }

    /** Appears near {@code player}, out of sight: by a herd if possible, else as a lone mob that fits the biome. */
    static Falsekin appear(ServerLevel level, ServerPlayer player, Haunt haunt) {
        Falsekin falsekin = EchelonEntities.FALSEKIN.create(level, EntitySpawnReason.EVENT);
        if (falsekin == null) return null;
        Vec3 spot = null;
        String kind = null;
        for (FalsekinKinds.Herd herd : FalsekinKinds.herds(level, player.position(), 48, 2)) {
            for (Mob member : herd.members()) {
                if (member.distanceTo(player) < 16) continue;
                Vec3 near = ShadowStep.spotNear(level, falsekin, member.position(), 1.5, 3.0);
                if (near != null && !FalsekinKinds.inView(level, player, near.add(0, 1, 0))) {
                    spot = near;
                    kind = herd.kind();
                    break;
                }
            }
            if (spot != null) break;
        }
        for (int attempt = 0; spot == null && attempt < 10; attempt++) {
            Vec3 near = ShadowStep.spotNear(level, falsekin, player.position(), 20, 36);
            if (near == null || FalsekinKinds.inView(level, player, near.add(0, 1, 0))) continue;
            List<String> fitting = FalsekinKinds.fitting(level, net.minecraft.core.BlockPos.containing(near));
            if (fitting.isEmpty()) continue;
            spot = near;
            kind = fitting.get(player.getRandom().nextInt(fitting.size()));
        }
        if (spot == null) {
            haunt.cooldown = 100;
            return null;
        }
        falsekin.snapTo(spot.x, spot.y, spot.z, player.getRandom().nextFloat() * 360, 0);
        falsekin.haunting(haunt.target, kind);
        haunt.bind(falsekin);
        level.addFreshEntity(falsekin);
        return falsekin;
    }

    /** All haunts, for the tests and the command. */
    public List<Haunt> all() { return new ArrayList<>(haunts.values()); }
}
