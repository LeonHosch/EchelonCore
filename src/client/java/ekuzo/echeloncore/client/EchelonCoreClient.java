package ekuzo.echeloncore.client;

import ekuzo.echeloncore.veil.VeilRenderer;
import net.fabricmc.api.ClientModInitializer;

public final class EchelonCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VeilRenderer.register();
    }
}
