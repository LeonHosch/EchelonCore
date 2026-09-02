package ekuzo.echeloncore.veil.anomaly;

import com.google.gson.Gson;
import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Clientbound synchronization for authoritative anomaly geometry and resolved Veil Level state. */
public final class VeilAnomalyNetworking {
    private static final Gson GSON = new Gson();

    // Prevent instantiation of the network bridge.
    private VeilAnomalyNetworking() {
    }

    // Register the anomaly snapshot payload during common mod initialization.
    public static void registerPayloadType() {
        PayloadTypeRegistry.clientboundPlay().register(AnomalySnapshotPayload.TYPE, AnomalySnapshotPayload.CODEC);
    }

    // Send one complete authoritative snapshot to a connected player.
    static void send(ServerPlayer player, int veilLevel, List<VeilAnomaly> anomalies) {
        WireSnapshot snapshot = new WireSnapshot(veilLevel, anomalies.stream().map(WireAnomaly::from).toList());
        ServerPlayNetworking.send(player, new AnomalySnapshotPayload(GSON.toJson(snapshot)));
    }

    // Decode a received snapshot for client rendering and prediction.
    public static Snapshot decode(String json) {
        WireSnapshot snapshot = GSON.fromJson(json, WireSnapshot.class);
        return new Snapshot(snapshot.veilLevel(), snapshot.anomalies().stream().map(WireAnomaly::toAnomaly).toList());
    }

    public record Snapshot(int veilLevel, List<VeilAnomaly> anomalies) {
        // Freeze decoded anomaly lists before the client indexes them.
        public Snapshot {
            anomalies = List.copyOf(anomalies);
        }
    }

    private record WireSnapshot(int veilLevel, List<WireAnomaly> anomalies) {
    }

    private record WireAnomaly(String id, String dimension, String shape, String boundaryMode,
                               double centerX, double centerY,
                               double centerZ, double sizeX, double sizeY, double sizeZ,
                               List<WireState> states) {
        // Convert validated server geometry into a primitive JSON transport representation.
        private static WireAnomaly from(VeilAnomaly anomaly) {
            return new WireAnomaly(anomaly.id().toString(), anomaly.dimension().toString(),
                    anomaly.shape().serializedName(), anomaly.boundaryMode().serializedName(),
                    anomaly.centerX(), anomaly.centerY(), anomaly.centerZ(),
                    anomaly.sizeX(), anomaly.sizeY(), anomaly.sizeZ(),
                    anomaly.states().stream().map(WireState::from).toList());
        }

        // Reconstruct and validate one common anomaly model from clientbound primitive fields.
        private VeilAnomaly toAnomaly() {
            return new VeilAnomaly(net.minecraft.resources.Identifier.parse(id),
                    net.minecraft.resources.Identifier.parse(dimension), VeilAnomaly.Shape.parse(shape),
                    centerX, centerY, centerZ, sizeX, sizeY, sizeZ,
                    boundaryMode == null ? VeilAnomaly.BoundaryMode.KEEP_OUT
                            : VeilAnomaly.BoundaryMode.parse(boundaryMode),
                    states.stream().map(WireState::toState).toList());
        }
    }

    private record WireState(int minimumVeilLevel, String collision, String visibility, String blockInteraction) {
        // Convert one resolved-threshold definition into stable lowercase wire names.
        private static WireState from(VeilAnomaly.AnomalyState state) {
            return new WireState(state.minimumVeilLevel(), state.collision().serializedName(),
                    state.visibility().serializedName(), state.blockInteraction().serializedName());
        }

        // Reconstruct one validated level state from clientbound primitive fields.
        private VeilAnomaly.AnomalyState toState() {
            return new VeilAnomaly.AnomalyState(minimumVeilLevel, VeilAnomaly.Collision.parse(collision),
                    VeilAnomaly.Visibility.parse(visibility), blockInteraction == null
                    ? VeilAnomaly.BlockInteraction.AUTOMATIC
                    : VeilAnomaly.BlockInteraction.parse(blockInteraction));
        }
    }

    public record AnomalySnapshotPayload(String json) implements CustomPacketPayload {
        public static final Type<AnomalySnapshotPayload> TYPE = new Type<>(EchelonCore.id("veil_anomaly_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AnomalySnapshotPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(1_048_576), AnomalySnapshotPayload::json, AnomalySnapshotPayload::new);

        // Return the payload type registered by EchelonCore.
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
