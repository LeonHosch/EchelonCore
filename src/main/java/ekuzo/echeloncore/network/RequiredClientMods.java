package ekuzo.echeloncore.network;

import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;

import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;

/** Requires client copies of EC and CWP matching the versions installed on this server. */
public final class RequiredClientMods {
    private static final String CWP_ID = "crazy-world-progression";
    private static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type("echelon-core:required_client_mods");

    private RequiredClientMods() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(Response.TYPE, Response.CODEC);

        ServerConfigurationNetworking.registerGlobalReceiver(Response.TYPE, (response, context) -> {
            String expectedEc = version(EchelonCore.MOD_ID);
            String expectedCwp = version(CWP_ID);
            if (!expectedEc.equals(response.echelonCoreVersion()) || !expectedCwp.equals(response.cwpVersion())) {
                context.packetListener().disconnect(requiredMessage(expectedEc, expectedCwp));
                return;
            }
            context.packetListener().completeTask(TASK_TYPE);
        });

        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, Request.TYPE)) {
                listener.disconnect(requiredMessage(version(EchelonCore.MOD_ID), version(CWP_ID)));
                return;
            }
            listener.addTask(new CheckTask());
        });
    }

    public static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    private static Component requiredMessage(String ec, String cwp) {
        return Component.literal("Fuer diesen Server brauchst du EchelonCore " + ec
                + " und CrazyWorldProgression " + cwp + " auf deinem Client.");
    }

    private record CheckTask() implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(new Request()));
        }

        @Override
        public Type type() {
            return TASK_TYPE;
        }
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(EchelonCore.id("required_client_mods_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Response(String echelonCoreVersion, String cwpVersion) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(EchelonCore.id("required_client_mods_response"));
        public static final StreamCodec<FriendlyByteBuf, Response> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(128), Response::echelonCoreVersion,
                ByteBufCodecs.stringUtf8(128), Response::cwpVersion,
                Response::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
