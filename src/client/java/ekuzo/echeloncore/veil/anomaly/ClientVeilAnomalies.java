package ekuzo.echeloncore.veil.anomaly;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.Collision.SOLID;
import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.BoundaryMode.KEEP_OUT;
import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.Visibility.HIDDEN;

/** Client snapshot cache split into visible geometry and block-protection prediction indexes. */
public final class ClientVeilAnomalies {
    private static final double SOLID_BLACKOUT_SAFETY_MARGIN = 0.75;
    private static volatile int veilLevel;
    private static volatile List<VisibleAnomaly> visible = List.of();
    private static volatile VeilAnomalySpatialIndex interactionIndex = new VeilAnomalySpatialIndex();

    // Prevent instantiation of the client anomaly cache.
    private ClientVeilAnomalies() {
    }

    // Register authoritative snapshot reception and clear world-specific data on disconnect.
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(VeilAnomalyNetworking.AnomalySnapshotPayload.TYPE,
                (payload, context) -> apply(VeilAnomalyNetworking.decode(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                player instanceof LocalPlayer localPlayer && protectsInteraction(localPlayer, pos)
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!(player instanceof LocalPlayer localPlayer)) return InteractionResult.PASS;
            BlockPos hit = hitResult.getBlockPos();
            return protectsInteraction(localPlayer, hit)
                    || protectsInteraction(localPlayer, hit.relative(hitResult.getDirection()))
                    ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    // Return resolved visible anomalies without exposing mutable client collections.
    public static List<VisibleAnomaly> visibleAnomalies() {
        return visible;
    }

    // Predict server-side block protection so mining cracks and ghost placements are suppressed immediately.
    public static boolean protectsInteraction(LocalPlayer player, BlockPos position) {
        return VeilAnomalyCollision.protectsBlock(player.level().dimension().identifier(), position,
                interactionIndex);
    }

    // Resolve the strongest camera effect, blacking out slightly before the view plane crosses a solid surface.
    public static AnomalyVision visionAt(Identifier dimension, Vec3 point) {
        AnomalyVision result = AnomalyVision.NONE;
        for (VisibleAnomaly visibleAnomaly : visible) {
            VeilAnomaly anomaly = visibleAnomaly.anomaly();
            if (!anomaly.dimension().equals(dimension)) continue;
            if (visibleAnomaly.collision() == SOLID && anomaly.boundaryMode() == KEEP_OUT
                    && anomaly.contains(point, SOLID_BLACKOUT_SAFETY_MARGIN, SOLID_BLACKOUT_SAFETY_MARGIN)) {
                return AnomalyVision.BLACKOUT;
            }
            if (visibleAnomaly.collision() != SOLID && visibleAnomaly.visibility() == VeilAnomaly.Visibility.OPAQUE
                    && anomaly.contains(point, 0.0, 0.0)) result = AnomalyVision.VEIL;
        }
        return result;
    }

    // Resolve all states once per snapshot and independently index explicit block-protection states.
    private static void apply(VeilAnomalyNetworking.Snapshot snapshot) {
        VeilAnomalySpatialIndex nextInteraction = new VeilAnomalySpatialIndex();
        List<VisibleAnomaly> nextVisible = new ArrayList<>();
        for (VeilAnomaly anomaly : snapshot.anomalies()) {
            VeilAnomaly.AnomalyState state = anomaly.stateAt(snapshot.veilLevel());
            if (state.protectsBlocks()) nextInteraction.add(anomaly);
            if (state.visibility() != HIDDEN || state.collision() == SOLID) {
                nextVisible.add(new VisibleAnomaly(anomaly, state.collision(), state.visibility()));
            }
        }
        veilLevel = snapshot.veilLevel();
        interactionIndex = nextInteraction;
        visible = List.copyOf(nextVisible);
    }

    // Drop stale server geometry when leaving a world.
    private static void clear() {
        veilLevel = 0;
        visible = List.of();
        interactionIndex = new VeilAnomalySpatialIndex();
    }

    public record VisibleAnomaly(VeilAnomaly anomaly, VeilAnomaly.Collision collision,
                                 VeilAnomaly.Visibility visibility) {
    }

    public enum AnomalyVision {
        NONE,
        VEIL,
        BLACKOUT
    }
}
