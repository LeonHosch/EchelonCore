package ekuzo.echeloncore.client;

import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.network.RequiredClientMods;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;

/** Answers the server's version check before the player enters the world. */
public final class RequiredClientModsClient {
    private RequiredClientModsClient() {
    }

    public static void register() {
        ClientConfigurationNetworking.registerGlobalReceiver(RequiredClientMods.Request.TYPE, (request, context) ->
                ClientConfigurationNetworking.send(new RequiredClientMods.Response(
                        RequiredClientMods.version(EchelonCore.MOD_ID),
                        RequiredClientMods.version("crazy-world-progression"))));
    }
}
