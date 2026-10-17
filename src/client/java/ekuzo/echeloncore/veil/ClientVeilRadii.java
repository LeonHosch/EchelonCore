package ekuzo.echeloncore.veil;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Client cache of server-authoritative radius settings for the current level. */
public final class ClientVeilRadii {
    private static volatile Map<String, VeilManager.Radii> current = Map.of();

    private ClientVeilRadii() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(VeilRadiusNetworking.RadiusSnapshotPayload.TYPE,
                (payload, context) -> current = VeilRadiusNetworking.decode(payload.json()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> current = Map.of());
    }

    public static VeilManager.Radii get(Identifier dimension) {
        return current.get(dimension.toString());
    }
}
