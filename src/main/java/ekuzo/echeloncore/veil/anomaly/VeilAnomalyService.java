package ekuzo.echeloncore.veil.anomaly;

import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.veil.VeilManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;

import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.BoundaryMode.KEEP_IN;
import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.BoundaryMode.KEEP_OUT;
import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.Collision.SOLID;

/** Authoritative anomaly persistence, level-state indexing, synchronization, and player rejection. */
public final class VeilAnomalyService {
    private static final double PLAYER_HALF_WIDTH = 0.3;
    private static final double PLAYER_HALF_HEIGHT = 0.9;
    private static final double REJECTION_RADIUS = 6.0;
    private static final double REJECTION_VERTICAL_RANGE = 4.0;
    private static final int RANDOM_TELEPORT_ATTEMPTS = 24;
    private static final int BOUNDARY_TELEPORT_ATTEMPTS = 16;
    private static final Map<MinecraftServer, RuntimeState> RUNTIMES = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, SafePosition>> SAFE_POSITIONS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Map<Identifier, SafePosition>>> CONTAINED_POSITIONS =
            new WeakHashMap<>();

    // Prevent instantiation of the server-side anomaly service.
    private VeilAnomalyService() {
    }

    // Register lifecycle synchronization and once-per-second Veil Level transition detection.
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> rebuild(server, true));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (RUNTIMES) {
                RUNTIMES.remove(server);
            }
            synchronized (SAFE_POSITIONS) {
                SAFE_POSITIONS.remove(server);
            }
            synchronized (CONTAINED_POSITIONS) {
                CONTAINED_POSITIONS.remove(server);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendSnapshot(handler.getPlayer()));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                player instanceof ServerPlayer serverPlayer && protectsInteraction(serverPlayer, pos)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer serverPlayer) || !protectsInteraction(serverPlayer, pos));
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            BlockPos hit = hitResult.getBlockPos();
            return protectsInteraction(serverPlayer, hit)
                    || protectsInteraction(serverPlayer, hit.relative(hitResult.getDirection()))
                    ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            enforcePlayerBoundaries(server);
            if (server.getTickCount() % 20 == 0) rebuild(server, false);
        });
    }

    // Return all persisted anomaly definitions in deterministic identifier order.
    public static List<VeilAnomaly> anomalies(MinecraftServer server) {
        return VeilAnomalyData.get(server).values().values().stream()
                .sorted(Comparator.comparing(anomaly -> anomaly.id().toString())).toList();
    }

    // Resolve one anomaly by its complete identifier.
    public static VeilAnomaly get(MinecraftServer server, Identifier id) {
        return VeilAnomalyData.get(server).values().get(id.toString());
    }

    // Create a new anomaly, rejecting an identifier already present in world data.
    public static void create(MinecraftServer server, VeilAnomaly anomaly) {
        if (get(server, anomaly.id()) != null) throw new IllegalArgumentException("Anomaly already exists: " + anomaly.id());
        validateBoundaryLayout(server, anomaly);
        VeilAnomalyData.get(server).put(anomaly);
        rebuild(server, true);
    }

    // Replace an existing anomaly after validating that its identifier is already stored.
    public static void update(MinecraftServer server, VeilAnomaly anomaly) {
        VeilAnomaly existing = get(server, anomaly.id());
        if (existing == null) throw new IllegalArgumentException("Unknown anomaly: " + anomaly.id());
        if (!existing.dimension().equals(anomaly.dimension()) || existing.boundaryMode() != anomaly.boundaryMode()) {
            validateBoundaryLayout(server, anomaly);
        }
        VeilAnomalyData.get(server).put(anomaly);
        rebuild(server, true);
    }

    // Delete an anomaly and immediately remove it from collision and client state.
    public static boolean remove(MinecraftServer server, Identifier id) {
        if (VeilAnomalyData.get(server).remove(id) == null) return false;
        rebuild(server, true);
        return true;
    }

    // Return how many anomalies are present in the current server collision index.
    public static int solidAnomalyCount(MinecraftServer server) {
        return runtime(server).solidIndex().anomalyCount();
    }

    // Immediately rebuild resolved indexes when CWP reapplies a changed global Veil Level.
    public static void refreshVeilLevel(MinecraftServer server) {
        rebuild(server, false);
    }

    // Reject block use, breaking, and ordinary adjacent placement within one block of a solid anomaly box.
    public static boolean protectsInteraction(ServerPlayer player, BlockPos position) {
        MinecraftServer server = player.level().getServer();
        RuntimeState runtime = runtime(server);
        Identifier dimension = player.level().dimension().identifier();
        return VeilAnomalyCollision.protectsBlock(dimension, position, runtime.interactionIndex());
    }

    // Prevent ambiguous opposing one-way boundaries from being configured in the same dimension.
    private static void validateBoundaryLayout(MinecraftServer server, VeilAnomaly candidate) {
        for (VeilAnomaly existing : anomalies(server)) {
            if (existing.id().equals(candidate.id()) || !existing.dimension().equals(candidate.dimension())) continue;
            if (candidate.boundaryMode() == KEEP_IN) {
                String reason = existing.boundaryMode() == KEEP_IN
                        ? "only one keep_in anomaly is allowed" : "a keep_out anomaly already exists";
                throw new IllegalArgumentException("Cannot use keep_in in " + candidate.dimension() + ": " + reason);
            }
            if (existing.boundaryMode() == KEEP_IN) {
                throw new IllegalArgumentException("Cannot add a keep_out anomaly to " + candidate.dimension()
                        + " while keep_in anomaly " + existing.id() + " exists");
            }
        }
    }

    // Enforce both one-way boundary modes and retain the latest valid position on the permitted side.
    private static void enforcePlayerBoundaries(MinecraftServer server) {
        RuntimeState runtime = runtime(server);
        Map<UUID, SafePosition> safePositions;
        synchronized (SAFE_POSITIONS) {
            safePositions = SAFE_POSITIONS.computeIfAbsent(server, ignored -> new HashMap<>());
        }
        Map<UUID, Map<Identifier, SafePosition>> containedPositions;
        synchronized (CONTAINED_POSITIONS) {
            containedPositions = CONTAINED_POSITIONS.computeIfAbsent(server, ignored -> new HashMap<>());
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Identifier dimension = player.level().dimension().identifier();
            Vec3 bodyCenter = player.position().add(0.0, 0.9, 0.0);
            VeilAnomaly keepOut = containing(runtime.keepOutIndex(), dimension, bodyCenter);
            if (keepOut == null) {
                safePositions.put(player.getUUID(), new SafePosition(dimension, player.position()));
            } else {
                player.stopRiding();
                boolean teleported = tryNearbyRejection(player, runtime.solidIndex(), dimension);
                if (!teleported) {
                    SafePosition remembered = safePositions.get(player.getUUID());
                    teleported = tryRememberedPosition(player, runtime.solidIndex(), dimension, remembered);
                }
                if (!teleported) {
                    teleported = tryHorizontalBoundaryRejection(player, runtime.solidIndex(), dimension, keepOut);
                }
                if (!teleported) forceOutside(player, runtime.solidIndex(), dimension, keepOut, bodyCenter);
                safePositions.put(player.getUUID(), new SafePosition(dimension, player.position()));
                EchelonCore.LOGGER.info("Veil anomaly {} rejected {} with a chorus teleport",
                        keepOut.id(), player.getGameProfile().name());
                continue;
            }

            trackAndEnforceContainment(player, runtime, containedPositions
                    .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()));
        }
    }

    // Remember entered keep-in anomalies, return outward crossings, and release membership on dimension travel.
    private static void trackAndEnforceContainment(ServerPlayer player, RuntimeState runtime,
                                                   Map<Identifier, SafePosition> memberships) {
        Identifier dimension = player.level().dimension().identifier();
        Vec3 bodyCenter = player.position().add(0.0, PLAYER_HALF_HEIGHT, 0.0);
        memberships.entrySet().removeIf(entry -> {
            VeilAnomaly anomaly = runtime.keepInById().get(entry.getKey());
            SafePosition remembered = entry.getValue();
            return anomaly == null || !dimension.equals(anomaly.dimension())
                    || !remembered.dimension().equals(anomaly.dimension())
                    || !fullyContainsPlayer(anomaly,
                    remembered.feet().add(0.0, PLAYER_HALF_HEIGHT, 0.0));
        });

        Set<Identifier> currentlyInside = new HashSet<>();
        for (VeilAnomaly anomaly : fullyContainingAll(runtime.keepInIndex(), dimension, bodyCenter)) {
            currentlyInside.add(anomaly.id());
            memberships.put(anomaly.id(), new SafePosition(dimension, player.position()));
        }
        for (Map.Entry<Identifier, SafePosition> entry : new ArrayList<>(memberships.entrySet())) {
            if (currentlyInside.contains(entry.getKey())) continue;
            VeilAnomaly anomaly = runtime.keepInById().get(entry.getKey());
            if (anomaly == null) continue;
            player.stopRiding();
            returnInside(player, runtime.keepOutIndex(), anomaly, entry.getValue());
            memberships.put(anomaly.id(), new SafePosition(anomaly.dimension(), player.position()));
            EchelonCore.LOGGER.info("Veil anomaly {} contained {} with a chorus teleport",
                    anomaly.id(), player.getGameProfile().name());
            break;
        }
    }

    // Find a safe chorus landing inside the containing anomaly, falling back to its last known inside position.
    private static void returnInside(ServerPlayer player, VeilAnomalySpatialIndex keepOutIndex,
                                     VeilAnomaly anomaly, SafePosition remembered) {
        Vec3 origin = player.position();
        for (int attempt = 0; attempt < RANDOM_TELEPORT_ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(player.getRandom().nextDouble()) * REJECTION_RADIUS;
            Vec3 requested = new Vec3(origin.x + Math.cos(angle) * distance,
                    origin.y + (player.getRandom().nextDouble() * 2.0 - 1.0) * REJECTION_VERTICAL_RANGE,
                    origin.z + Math.sin(angle) * distance);
            if (tryContainedChorusTeleport(player, keepOutIndex, anomaly, requested, origin)) return;
        }
        if (tryContainedChorusTeleport(player, keepOutIndex, anomaly, remembered.feet(), origin)) return;
        forceInside(player, keepOutIndex, anomaly, remembered);
    }

    // Validate that vanilla's final landing remains inside the selected anomaly and outside every keep-out anomaly.
    private static boolean tryContainedChorusTeleport(ServerPlayer player, VeilAnomalySpatialIndex keepOutIndex,
                                                      VeilAnomaly anomaly, Vec3 requested, Vec3 effectOrigin) {
        Vec3 landing = findLanding(player.level(), requested);
        Vec3 landingBody = landing == null ? null : landing.add(0.0, PLAYER_HALF_HEIGHT, 0.0);
        if (landingBody == null || !fullyContainsPlayer(anomaly, landingBody)
                || containing(keepOutIndex, anomaly.dimension(), landingBody) != null) return false;
        if (!player.randomTeleport(requested.x, requested.y, requested.z, true)) return false;
        Vec3 finalBody = player.position().add(0.0, PLAYER_HALF_HEIGHT, 0.0);
        if (!fullyContainsPlayer(anomaly, finalBody)
                || containing(keepOutIndex, anomaly.dimension(), finalBody) != null) {
            player.teleportTo(effectOrigin.x, effectOrigin.y, effectOrigin.z);
            return false;
        }
        finishChorusEffect(player, effectOrigin);
        return true;
    }

    // Guarantee same-dimension containment at the last server-observed inside position.
    private static void forceInside(ServerPlayer player, VeilAnomalySpatialIndex keepOutIndex,
                                    VeilAnomaly anomaly, SafePosition remembered) {
        Vec3 bodyCenter = remembered.feet().add(0.0, PLAYER_HALF_HEIGHT, 0.0);
        if (!fullyContainsPlayer(anomaly, bodyCenter)
                || containing(keepOutIndex, anomaly.dimension(), bodyCenter) != null) {
            throw new IllegalStateException("No valid remembered inside point for Veil anomaly " + anomaly.id());
        }
        player.teleportTo(remembered.feet().x, remembered.feet().y, remembered.feet().z);
        finishChorusEffect(player, player.position());
    }

    // Try chorus-like destinations within a six-block horizontal radius and four vertical blocks of entry.
    private static boolean tryNearbyRejection(ServerPlayer player, VeilAnomalySpatialIndex index,
                                              Identifier dimension) {
        Vec3 origin = player.position();
        ServerLevel level = player.level();
        for (int attempt = 0; attempt < RANDOM_TELEPORT_ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(player.getRandom().nextDouble()) * REJECTION_RADIUS;
            double x = origin.x + Math.cos(angle) * distance;
            double y = Mth.clamp(origin.y
                            + (player.getRandom().nextDouble() * 2.0 - 1.0) * REJECTION_VERTICAL_RANGE,
                    level.getMinY(), level.getMinY() + level.getLogicalHeight() - 1.0);
            double z = origin.z + Math.sin(angle) * distance;
            if (trySafeChorusTeleport(player, index, dimension, new Vec3(x, y, z), origin)) return true;
        }
        return false;
    }

    // Use the latest nearby outside position only when random landing searches cannot find safe terrain.
    private static boolean tryRememberedPosition(ServerPlayer player, VeilAnomalySpatialIndex index,
                                                 Identifier dimension, SafePosition remembered) {
        if (remembered == null || !remembered.dimension().equals(dimension)
                || remembered.feet().distanceToSqr(player.position()) > 144.0) return false;
        return trySafeChorusTeleport(player, index, dimension, remembered.feet(), player.position());
    }

    // Search just beyond the anomaly's horizontal footprint when a large roof area covers every local candidate.
    private static boolean tryHorizontalBoundaryRejection(ServerPlayer player, VeilAnomalySpatialIndex index,
                                                          Identifier dimension, VeilAnomaly anomaly) {
        AABB bounds = anomaly.bounds();
        double preferredX = player.getX() - anomaly.centerX();
        double preferredZ = player.getZ() - anomaly.centerZ();
        double preferredAngle = preferredX * preferredX + preferredZ * preferredZ < 1.0E-8
                ? player.getRandom().nextDouble() * Math.PI * 2.0 : Math.atan2(preferredZ, preferredX);
        Vec3 origin = player.position();
        for (int attempt = 0; attempt < BOUNDARY_TELEPORT_ATTEMPTS; attempt++) {
            double angle = attempt == 0 ? preferredAngle : player.getRandom().nextDouble() * Math.PI * 2.0;
            double directionX = Math.cos(angle);
            double directionZ = Math.sin(angle);
            double toXFace = Math.abs(directionX) < 1.0E-8 ? Double.POSITIVE_INFINITY
                    : ((bounds.maxX - bounds.minX) * 0.5 + PLAYER_HALF_WIDTH + 1.0)
                    / Math.abs(directionX);
            double toZFace = Math.abs(directionZ) < 1.0E-8 ? Double.POSITIVE_INFINITY
                    : ((bounds.maxZ - bounds.minZ) * 0.5 + PLAYER_HALF_WIDTH + 1.0)
                    / Math.abs(directionZ);
            double distance = Math.min(toXFace, toZFace);
            Vec3 requested = new Vec3(anomaly.centerX() + directionX * distance,
                    Math.min(player.getY() + REJECTION_VERTICAL_RANGE,
                            player.level().getMinY() + player.level().getLogicalHeight() - 1.0),
                    anomaly.centerZ() + directionZ * distance);
            if (trySafeChorusTeleport(player, index, dimension, requested, origin)) return true;
        }
        return false;
    }

    // Validate vanilla's eventual ground landing before invoking its teleport and particle implementation.
    private static boolean trySafeChorusTeleport(ServerPlayer player, VeilAnomalySpatialIndex index,
                                                 Identifier dimension, Vec3 requested, Vec3 effectOrigin) {
        Vec3 landing = findLanding(player.level(), requested);
        if (landing == null || containing(index, dimension, landing.add(0.0, PLAYER_HALF_HEIGHT, 0.0)) != null) {
            return false;
        }
        if (!player.randomTeleport(requested.x, requested.y, requested.z, true)) return false;
        if (containing(index, dimension, player.position().add(0.0, PLAYER_HALF_HEIGHT, 0.0)) != null) {
            player.teleportTo(effectOrigin.x, effectOrigin.y, effectOrigin.z);
            return false;
        }
        finishChorusEffect(player, effectOrigin);
        return true;
    }

    // Mirror vanilla chorus-fruit ground search without loading new chunks so anomaly validation sees the final point.
    private static Vec3 findLanding(ServerLevel level, Vec3 requested) {
        BlockPos cursor = BlockPos.containing(requested);
        if (!level.hasChunkAt(cursor)) return null;
        double landingY = requested.y;
        while (cursor.getY() > level.getMinY()) {
            BlockPos below = cursor.below();
            if (level.getBlockState(below).blocksMotion()) return new Vec3(requested.x, landingY, requested.z);
            landingY -= 1.0;
            cursor = below;
        }
        return null;
    }

    // Add the chorus sound, game event, fall reset, and impulse reset after a validated teleport succeeds.
    private static void finishChorusEffect(ServerPlayer player, Vec3 origin) {
        ServerLevel level = player.level();
        level.gameEvent(GameEvent.TELEPORT, origin, GameEvent.Context.of(player));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.PLAYERS);
        player.resetFallDistance();
        player.resetCurrentImpulseContext();
        player.setDeltaMovement(Vec3.ZERO);
    }

    // Guarantee removal from protected geometry if random and remembered chorus destinations both fail.
    private static void forceOutside(ServerPlayer player, VeilAnomalySpatialIndex index, Identifier dimension,
                                     VeilAnomaly firstContaining, Vec3 bodyCenter) {
        AABB escapeBounds = firstContaining.bounds().inflate(PLAYER_HALF_WIDTH, PLAYER_HALF_HEIGHT,
                PLAYER_HALF_WIDTH);
        Vec3 outsideCenter = outsideHorizontally(escapeBounds, bodyCenter, 1.0);
        for (int attempt = 0; attempt <= index.anomalyCount(); attempt++) {
            double requestedY = Math.min(player.getY() + REJECTION_VERTICAL_RANGE,
                    player.level().getMinY() + player.level().getLogicalHeight() - 1.0);
            Vec3 requested = new Vec3(outsideCenter.x, requestedY, outsideCenter.z);
            Vec3 landing = findLanding(player.level(), requested);
            Vec3 destination = landing == null ? new Vec3(outsideCenter.x, player.getY(), outsideCenter.z) : landing;
            VeilAnomaly overlap = containing(index, dimension,
                    destination.add(0.0, PLAYER_HALF_HEIGHT, 0.0));
            if (overlap == null) {
                Vec3 origin = player.position();
                player.teleportTo(destination.x, destination.y, destination.z);
                finishChorusEffect(player, origin);
                return;
            }
            AABB other = overlap.bounds().inflate(PLAYER_HALF_WIDTH, PLAYER_HALF_HEIGHT, PLAYER_HALF_WIDTH);
            escapeBounds = new AABB(Math.min(escapeBounds.minX, other.minX), Math.min(escapeBounds.minY, other.minY),
                    Math.min(escapeBounds.minZ, other.minZ), Math.max(escapeBounds.maxX, other.maxX),
                    Math.max(escapeBounds.maxY, other.maxY), Math.max(escapeBounds.maxZ, other.maxZ));
            outsideCenter = outsideHorizontally(escapeBounds, bodyCenter, 1.0);
        }
        throw new IllegalStateException("Could not calculate an outside point for solid Veil anomalies");
    }

    // Select the nearest horizontal face so gravity cannot drop the player back through an upper escape point.
    private static Vec3 outsideHorizontally(AABB box, Vec3 point, double clearance) {
        double toMinX = Math.abs(point.x - box.minX);
        double toMaxX = Math.abs(box.maxX - point.x);
        double toMinZ = Math.abs(point.z - box.minZ);
        double toMaxZ = Math.abs(box.maxZ - point.z);
        double nearest = Math.min(Math.min(toMinX, toMaxX), Math.min(toMinZ, toMaxZ));
        if (nearest == toMinX) return new Vec3(box.minX - clearance, point.y, point.z);
        if (nearest == toMaxX) return new Vec3(box.maxX + clearance, point.y, point.z);
        if (nearest == toMinZ) return new Vec3(point.x, point.y, box.minZ - clearance);
        return new Vec3(point.x, point.y, box.maxZ + clearance);
    }

    // Resolve one containing solid anomaly through a nearby query covering the complete fixed player body.
    private static VeilAnomaly containing(VeilAnomalySpatialIndex index, Identifier dimension, Vec3 bodyCenter) {
        AABB bodyBounds = AABB.ofSize(bodyCenter, PLAYER_HALF_WIDTH * 2.0, PLAYER_HALF_HEIGHT * 2.0,
                PLAYER_HALF_WIDTH * 2.0);
        for (VeilAnomaly anomaly : index.query(dimension, bodyBounds)) {
            if (anomaly.contains(bodyCenter, PLAYER_HALF_WIDTH, PLAYER_HALF_HEIGHT)) return anomaly;
        }
        return null;
    }

    // Resolve regions fully enclosing the standing-size body so their last stored position never crosses the shell.
    private static List<VeilAnomaly> fullyContainingAll(VeilAnomalySpatialIndex index, Identifier dimension,
                                                        Vec3 bodyCenter) {
        AABB bodyBounds = AABB.ofSize(bodyCenter, PLAYER_HALF_WIDTH * 2.0, PLAYER_HALF_HEIGHT * 2.0,
                PLAYER_HALF_WIDTH * 2.0);
        List<VeilAnomaly> matches = new ArrayList<>();
        for (VeilAnomaly anomaly : index.query(dimension, bodyBounds)) {
            if (fullyContainsPlayer(anomaly, bodyCenter)) matches.add(anomaly);
        }
        return matches;
    }

    // Test all standing-body corners so even curved and sloped shells only remember fully inside positions.
    private static boolean fullyContainsPlayer(VeilAnomaly anomaly, Vec3 bodyCenter) {
        for (int xSign = -1; xSign <= 1; xSign += 2) {
            for (int ySign = -1; ySign <= 1; ySign += 2) {
                for (int zSign = -1; zSign <= 1; zSign += 2) {
                    Vec3 corner = bodyCenter.add(xSign * PLAYER_HALF_WIDTH, ySign * PLAYER_HALF_HEIGHT,
                            zSign * PLAYER_HALF_WIDTH);
                    if (!anomaly.contains(corner, 0.0, 0.0)) return false;
                }
            }
        }
        return true;
    }

    // Rebuild resolved runtime collections only when definitions changed or the Veil Level crossed a threshold.
    private static void rebuild(MinecraftServer server, boolean force) {
        int veilLevel = VeilManager.getVeilLevel(server);
        RuntimeState previous;
        synchronized (RUNTIMES) {
            previous = RUNTIMES.get(server);
        }
        if (!force && previous != null && previous.veilLevel() == veilLevel) return;
        List<VeilAnomaly> anomalies = normalizeBoundaryLayouts(anomalies(server));
        VeilAnomalySpatialIndex solidIndex = new VeilAnomalySpatialIndex();
        VeilAnomalySpatialIndex interactionIndex = new VeilAnomalySpatialIndex();
        VeilAnomalySpatialIndex keepOutIndex = new VeilAnomalySpatialIndex();
        VeilAnomalySpatialIndex keepInIndex = new VeilAnomalySpatialIndex();
        Map<Identifier, VeilAnomaly> keepInById = new HashMap<>();
        for (VeilAnomaly anomaly : anomalies) {
            VeilAnomaly.AnomalyState state = anomaly.stateAt(veilLevel);
            if (state.protectsBlocks()) interactionIndex.add(anomaly);
            if (state.collision() != SOLID) continue;
            solidIndex.add(anomaly);
            if (anomaly.boundaryMode() == KEEP_OUT) {
                keepOutIndex.add(anomaly);
            } else if (anomaly.boundaryMode() == KEEP_IN) {
                keepInIndex.add(anomaly);
                keepInById.put(anomaly.id(), anomaly);
            }
        }
        RuntimeState current = new RuntimeState(veilLevel, anomalies, solidIndex, interactionIndex, keepOutIndex,
                keepInIndex, keepInById);
        synchronized (RUNTIMES) {
            RUNTIMES.put(server, current);
        }
        server.getPlayerList().getPlayers().forEach(player ->
                VeilAnomalyNetworking.send(player, current.veilLevel(), current.anomalies()));
        EchelonCore.LOGGER.info("Indexed {} solid Veil anomaly/anomalies ({} keep-out, {} keep-in) and {} "
                        + "block-protected anomaly/anomalies out of {} at Veil Level {}", solidIndex.anomalyCount(),
                keepOutIndex.anomalyCount(), keepInIndex.anomalyCount(), interactionIndex.anomalyCount(),
                anomalies.size(), veilLevel);
    }

    // Safely treat legacy conflicting keep-in layouts as keep-out until an administrator fixes their definitions.
    private static List<VeilAnomaly> normalizeBoundaryLayouts(List<VeilAnomaly> definitions) {
        Map<Identifier, Integer> keepInCounts = new HashMap<>();
        Set<Identifier> keepOutDimensions = new HashSet<>();
        for (VeilAnomaly anomaly : definitions) {
            if (anomaly.boundaryMode() == KEEP_IN) {
                keepInCounts.merge(anomaly.dimension(), 1, Integer::sum);
            } else {
                keepOutDimensions.add(anomaly.dimension());
            }
        }
        Set<Identifier> invalidDimensions = new HashSet<>();
        keepInCounts.forEach((dimension, count) -> {
            if (count > 1 || keepOutDimensions.contains(dimension)) invalidDimensions.add(dimension);
        });
        if (invalidDimensions.isEmpty()) return definitions;
        EchelonCore.LOGGER.error("Invalid Veil anomaly boundary layout in {}; treating its keep-in anomalies as "
                + "keep-out until the conflict is fixed", invalidDimensions);
        return definitions.stream().map(anomaly -> anomaly.boundaryMode() == KEEP_IN
                        && invalidDimensions.contains(anomaly.dimension())
                ? anomaly.withBoundaryMode(KEEP_OUT) : anomaly).toList();
    }

    // Send the current resolved level and complete definitions to one newly connected client.
    private static void sendSnapshot(ServerPlayer player) {
        RuntimeState runtime = runtime(player.level().getServer());
        VeilAnomalyNetworking.send(player, runtime.veilLevel(), runtime.anomalies());
    }

    // Return initialized runtime state even if a movement hook runs unusually early during startup.
    private static RuntimeState runtime(MinecraftServer server) {
        synchronized (RUNTIMES) {
            RuntimeState state = RUNTIMES.get(server);
            if (state != null) return state;
        }
        rebuild(server, true);
        synchronized (RUNTIMES) {
            return RUNTIMES.get(server);
        }
    }

    private record RuntimeState(int veilLevel, List<VeilAnomaly> anomalies,
                                VeilAnomalySpatialIndex solidIndex, VeilAnomalySpatialIndex interactionIndex,
                                VeilAnomalySpatialIndex keepOutIndex, VeilAnomalySpatialIndex keepInIndex,
                                Map<Identifier, VeilAnomaly> keepInById) {
        // Freeze definition snapshots independently from persistent mutable storage.
        private RuntimeState {
            anomalies = List.copyOf(anomalies);
            keepInById = Map.copyOf(keepInById);
        }
    }

    /** A server-observed player feet position on the permitted side of a one-way anomaly boundary. */
    private record SafePosition(Identifier dimension, Vec3 feet) {
    }
}
