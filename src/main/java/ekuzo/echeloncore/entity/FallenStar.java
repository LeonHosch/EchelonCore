package ekuzo.echeloncore.entity;

import ekuzo.crazyworldprogression.events.RandomEventRegistry;
import ekuzo.crazyworldprogression.events.RandomEventService;
import ekuzo.crazyworldprogression.loot.LootPoolData;
import ekuzo.crazyworldprogression.loot.LootPoolService;
import ekuzo.crazyworldprogression.mobforge.MobForge;
import ekuzo.crazyworldprogression.mobforge.script.MobScript;
import ekuzo.crazyworldprogression.mobforge.script.MobScriptCode;
import ekuzo.crazyworldprogression.mobforge.script.ScriptSignals;
import ekuzo.crazyworldprogression.portals.PocketEditorService;
import ekuzo.crazyworldprogression.portals.PortalService;
import ekuzo.crazyworldprogression.worldboundary.AdvancedWorldBoundary;
import ekuzo.crazyworldprogression.worldboundary.BonusAreaService;
import ekuzo.crazyworldprogression.worldboundary.restriction.Restriction;
import ekuzo.crazyworldprogression.worldboundary.restriction.RestrictionService;
import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Fallen Star random event: a meteorite strikes somewhere in the overworld away from anomalies, kingdom areas,
 * portals and the Veil. Its core holds 7 to 18 ancient debris inside a shell of obsidian, blackstone, cobblestone
 * and magma. Everything that happens around it (guards, heat, shockwaves, the 30 minute burn-out and the reward) is
 * the MobForge script {@code fallen_star} (config/mobforge/scripts/fallen_star.mfs, written there on first start),
 * moved to where the star fell. The debris drops like any ancient debris (players can fight over who mines it), and
 * everyone still taking part when the core is mined out also gets the fallen_star loot pool.
 */
public final class FallenStar {
    public static final Identifier EVENT = EchelonCore.id("fallen_star");
    public static final Identifier LOOT_POOL = EchelonCore.id("fallen_star");
    /** The script triggers this when it is done; the event then ends. */
    public static final Identifier OVER = EchelonCore.id("fallen_star_over");
    public static final String SCRIPT = "fallen_star";
    /** The script's guards carry this tag. */
    private static final String GUARDS = "guards";
    /** Core: everything within this many blocks of the meteorite's center (the script's "core" event box). */
    private static final int CORE = 2;
    private static final double METEOR_RADIUS = 3.6, CRATER_RADIUS = 10;
    /** Where the current star is (also saved in the event, so a restart can still burn it out). */
    private static BlockPos center;

    private FallenStar() { }

    public static void register() {
        RandomEventRegistry.registerHandler(EVENT, new RandomEventRegistry.RandomEventHandler() {
            @Override
            public void onStarted(MinecraftServer server, RandomEventService.ActiveRandomEvent event) {
                if (!start(server)) server.execute(() -> RandomEventService.stop(server, EVENT));
            }

            @Override
            public void onRestored(MinecraftServer server, RandomEventService.ActiveRandomEvent event) {
                // Scripts do not survive a restart, so a star from before it simply burns out.
                center = saved(event.metadata());
                server.execute(() -> RandomEventService.stop(server, EVENT));
            }

            @Override
            public void onEnded(MinecraftServer server, RandomEventService.ActiveRandomEvent event,
                                RandomEventRegistry.EndReason reason) {
                BlockPos star = center != null ? center : saved(event.metadata());
                MobForge.SCRIPT_EXECUTOR.cancel(SCRIPT);
                if (star != null) burnOut(server.overworld(), star);
                center = null;
            }
        });
        ScriptSignals.register(new ScriptSignals.Listener() {
            @Override
            public void onTrigger(java.util.UUID run, Identifier event, ServerLevel world,
                                  Map<String, ekuzo.crazyworldprogression.api.mobforge.ScriptEntityHandle> handles) {
                if (event.equals(OVER)) world.getServer().execute(() -> {
                    center = null;
                    RandomEventService.stop(world.getServer(), EVENT);
                });
            }

            /** A broken script must not leave a star without guards, heat or ending: it burns out right away. */
            @Override
            public void onFailed(java.util.UUID run, String script, ServerLevel world, String reason) {
                if (!script.equals(SCRIPT)) return;
                EchelonCore.LOGGER.error("Fallen Star: the fallen_star script stopped ({}), the star burns out", reason);
                world.getServer().execute(() -> {
                    if (RandomEventService.isActive(world.getServer(), EVENT)) RandomEventService.stop(world.getServer(), EVENT);
                });
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            installScript();
            ensureLootPool(server);
        });
    }

    private static BlockPos saved(Map<String, String> metadata) {
        try {
            return new BlockPos(Integer.parseInt(metadata.get("x")), Integer.parseInt(metadata.get("y")),
                    Integer.parseInt(metadata.get("z")));
        } catch (RuntimeException missing) {
            return null;
        }
    }

    /** Picks a place, builds the crater and meteorite and starts the script. False when no place was found. */
    private static boolean start(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) return false;
        ServerLevel level = server.overworld();
        MobScript script = script();
        if (script == null) return false;
        BlockPos site = findSite(server, level);
        if (site == null) {
            EchelonCore.LOGGER.warn("Fallen Star: found no free place for the meteorite");
            return false;
        }
        center = build(level, site);
        RandomEventService.putMetadata(server, EVENT, "x", Integer.toString(center.getX()));
        RandomEventService.putMetadata(server, EVENT, "y", Integer.toString(center.getY()));
        RandomEventService.putMetadata(server, EVENT, "z", Integer.toString(center.getZ()));
        MobForge.SCRIPT_EXECUTOR.run(PocketEditorService.translate(script, center), level,
                Map.of("origin", Vec3.atCenterOf(center)), null, null, true);
        int x = Math.round(center.getX() / 25f) * 25, z = Math.round(center.getZ() / 25f) * 25;
        for (var player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("★ A star has fallen from the sky near " + x + ", " + z
                    + "! Its core holds ancient debris, but it burns out in 30 minutes.").withStyle(ChatFormatting.LIGHT_PURPLE));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.LIGHTNING_BOLT_THUNDER),
                    SoundSource.WEATHER, player.getX(), player.getY(), player.getZ(), 0.6F, 0.6F, level.getRandom().nextLong()));
        }
        EchelonCore.LOGGER.info("Fallen Star at {}", center.toShortString());
        return true;
    }

    /** The edited script from the config folder, or the one shipped with Echelon. */
    private static MobScript script() {
        var configured = MobForge.SCRIPTS.get(SCRIPT);
        if (configured.isPresent()) return configured.get();
        try {
            return new MobScriptCode().decode(bundledScript());
        } catch (IOException | RuntimeException exception) {
            EchelonCore.LOGGER.error("Fallen Star: could not read the fallen_star script", exception);
            return null;
        }
    }

    private static String bundledScript() throws IOException {
        try (InputStream stream = FallenStar.class.getResourceAsStream("/echelon-core/scripts/fallen_star.mfs")) {
            if (stream == null) throw new IOException("fallen_star.mfs is missing from the Echelon jar");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes the script into the config folder, so admins can edit it in /cwp admin. A newer shipped version replaces
     * it only while nobody has edited it (the last shipped text is remembered next to it).
     */
    private static void installScript() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("mobforge/scripts/" + SCRIPT + ".mfs");
        Path shipped = file.resolveSibling("." + SCRIPT + ".shipped");
        try {
            String bundled = bundledScript();
            if (Files.exists(file)) {
                String current = Files.readString(file, StandardCharsets.UTF_8);
                if (current.equals(bundled)) return;
                // Edited by an admin: keep it. (Before the shipped copy was remembered, every file was unedited.)
                if (Files.exists(shipped) && !current.equals(Files.readString(shipped, StandardCharsets.UTF_8))) return;
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, bundled, StandardCharsets.UTF_8);
            Files.writeString(shipped, bundled, StandardCharsets.UTF_8);
            MobForge.SCRIPTS.reload();
        } catch (IOException exception) {
            EchelonCore.LOGGER.warn("Fallen Star: could not write {}", file, exception);
        }
    }

    /** The reward pool, made once; admins can change it in /cwp admin → Loot pools. */
    private static void ensureLootPool(MinecraftServer server) {
        if (LootPoolService.get(server, LOOT_POOL) != null) return;
        List<LootPoolData.Entry> entries = List.of(
                new LootPoolData.Entry("item", Identifier.parse("minecraft:netherite_scrap"), 10000, 1, 1, 3, true),
                new LootPoolData.Entry("item", Identifier.parse("minecraft:gold_ingot"), 6000, 1, 2, 6),
                new LootPoolData.Entry("item", Identifier.parse("minecraft:iron_ingot"), 6000, 1, 3, 8),
                new LootPoolData.Entry("item", Identifier.parse("minecraft:obsidian"), 5000, 1, 2, 6),
                new LootPoolData.Entry("item", Identifier.parse("minecraft:blaze_powder"), 4000, 1, 2, 5),
                new LootPoolData.Entry("item", Identifier.parse("minecraft:magma_cream"), 3000, 1, 1, 3),
                new LootPoolData.Entry("item", Identifier.parse("minecraft:experience_bottle"), 5000, 1, 2, 5),
                new LootPoolData.Entry("pet", EchelonCore.id("mini_void_wraith"), 200, 1, 1, 1));
        try {
            LootPoolService.save(server, new LootPoolData.Pool(LOOT_POOL, "Fallen Star", "all", entries));
        } catch (RuntimeException exception) {
            EchelonCore.LOGGER.warn("Fallen Star: could not create the loot pool", exception);
        }
    }

    /**
     * A random place inside the Veil's inner radius (1500 blocks without a Veil), at least 48 blocks from anomalies
     * and kingdom areas and 64 from portals, on dry, fairly flat ground.
     */
    private static BlockPos findSite(MinecraftServer server, ServerLevel level) {
        Identifier overworld = level.dimension().identifier();
        BlockPos spawn = server.getRespawnData().pos();
        AdvancedWorldBoundary.Radii radii = AdvancedWorldBoundary.getRadii(server, overworld, AdvancedWorldBoundary.getLevel(server));
        double max = radii == null ? 1500 : radii.inner() - 60;
        double min = Math.min(250, max / 3);
        if (max <= min) return null;
        List<Restriction> anomalies = RestrictionService.restrictions(server).stream()
                .filter(anomaly -> anomaly.dimension().equals(overworld)).toList();
        List<BonusAreaService.Area> kingdom = BonusAreaService.areas(server).stream()
                .filter(area -> area.dimension().equals(overworld)).toList();
        List<Vec3> portals = new ArrayList<>();
        for (var portal : PortalService.portals(server)) {
            if (portal.dimension().equals(overworld)) portals.add(new Vec3(portal.centerX(), portal.centerY(), portal.centerZ()));
            for (var placement : PortalService.locations(server, portal.id()).values())
                if (placement.dimension().equals(overworld)) portals.add(new Vec3(placement.x(), placement.y(), placement.z()));
        }
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = min + random.nextDouble() * (max - min);
            int x = spawn.getX() + (int) (Math.cos(angle) * distance), z = spawn.getZ() + (int) (Math.sin(angle) * distance);
            if (anomalies.stream().anyMatch(anomaly -> Math.hypot(anomaly.centerX() - x, anomaly.centerZ() - z)
                    < Math.max(anomaly.sizeX(), anomaly.sizeZ()) / 2 + 48)) continue;
            if (kingdom.stream().anyMatch(area -> Math.hypot(area.x() - x, area.z() - z) < area.radius() + 48)) continue;
            if (portals.stream().anyMatch(portal -> Math.hypot(portal.x - x, portal.z - z) < 64)) continue;
            level.getChunk(x >> 4, z >> 4);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos ground = new BlockPos(x, surface - 1, z);
            if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) continue;
            if (surface <= level.getSeaLevel() - 4 || surface > level.getMaxY() - 30) continue;
            boolean flat = true;
            for (int[] corner : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
                level.getChunk((x + corner[0]) >> 4, (z + corner[1]) >> 4);
                if (Math.abs(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + corner[0], z + corner[1]) - surface) > 6)
                    flat = false;
            }
            if (flat) return ground;
        }
        return null;
    }

    /** Carves the scorched crater and builds the meteorite in it; returns the meteorite's center. */
    private static BlockPos build(ServerLevel level, BlockPos ground) {
        RandomSource random = level.getRandom();
        int r = (int) Math.ceil(CRATER_RADIUS) + 1;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz) / CRATER_RADIUS;
            if (d > 1.1) continue;
            int x = ground.getX() + dx, z = ground.getZ() + dz;
            if (d <= 1.0) {
                int depth = (int) Math.round(4 * (1 - d * d));
                int floor = ground.getY() - depth;
                for (int y = floor + 1; y <= ground.getY() + 14; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, floor, z), scorched(random), 2);
                if (random.nextFloat() < 0.06) level.setBlock(new BlockPos(x, floor + 1, z), Blocks.FIRE.defaultBlockState(), 2);
            } else if (random.nextFloat() < 0.45) {
                // Thrown-up rubble around the rim.
                level.setBlock(new BlockPos(x, ground.getY() + 1, z), random.nextBoolean()
                        ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), 2);
            }
        }
        BlockPos center = ground.below();
        List<BlockPos> core = new ArrayList<>();
        int m = (int) Math.ceil(METEOR_RADIUS);
        for (int dx = -m; dx <= m; dx++) for (int dy = -m; dy <= m; dy++) for (int dz = -m; dz <= m; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > METEOR_RADIUS) continue;
            BlockPos pos = center.offset(dx, dy, dz);
            if (d <= 1.75) core.add(pos);
            else level.setBlock(pos, d > 2.6 ? outerShell(random) : innerShell(random), 2);
        }
        Collections.shuffle(core, new java.util.Random(random.nextLong()));
        int debris = 7 + random.nextInt(12);
        for (int i = 0; i < core.size(); i++)
            level.setBlock(core.get(i), i < debris ? Blocks.ANCIENT_DEBRIS.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState(), 2);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 3,
                center.getZ() + 0.5, 3, 2, 1, 2, 0);
        return center;
    }

    private static BlockState scorched(RandomSource random) {
        float roll = random.nextFloat();
        Block block = roll < 0.3 ? Blocks.BLACKSTONE : roll < 0.45 ? Blocks.MAGMA_BLOCK : roll < 0.7 ? Blocks.COARSE_DIRT
                : roll < 0.85 ? Blocks.NETHERRACK : Blocks.BASALT;
        return block.defaultBlockState();
    }

    private static BlockState outerShell(RandomSource random) {
        float roll = random.nextFloat();
        Block block = roll < 0.35 ? Blocks.OBSIDIAN : roll < 0.45 ? Blocks.CRYING_OBSIDIAN : roll < 0.65 ? Blocks.BLACKSTONE
                : roll < 0.8 ? Blocks.COBBLESTONE : roll < 0.92 ? Blocks.MAGMA_BLOCK : roll < 0.97 ? Blocks.NETHERRACK
                : Blocks.GILDED_BLACKSTONE;
        return block.defaultBlockState();
    }

    private static BlockState innerShell(RandomSource random) {
        float roll = random.nextFloat();
        return (roll < 0.6 ? Blocks.OBSIDIAN : roll < 0.85 ? Blocks.BLACKSTONE : Blocks.COBBLESTONE).defaultBlockState();
    }

    /** The star goes cold: its debris turns to cobblestone and the guards that are left vanish. */
    private static void burnOut(ServerLevel level, BlockPos star) {
        for (BlockPos pos : BlockPos.betweenClosed(star.offset(-CORE, -CORE, -CORE), star.offset(CORE, CORE, CORE)))
            if (level.getBlockState(pos).is(Blocks.ANCIENT_DEBRIS)) level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(star).inflate(64),
                entity -> entity.entityTags().contains(GUARDS))) entity.discard();
    }
}
