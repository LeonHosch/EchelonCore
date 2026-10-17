package ekuzo.echeloncore.client;

import ekuzo.echeloncore.veil.VeilRenderer;
import ekuzo.echeloncore.veil.ClientVeilRadii;
import ekuzo.echeloncore.veil.anomaly.ClientVeilAnomalies;
import ekuzo.echeloncore.veil.anomaly.VeilAnomalyRenderer;
import net.fabricmc.api.ClientModInitializer;

public final class EchelonCoreClient implements ClientModInitializer {
    // Register Echelon's client-only veil rendering after Minecraft's client systems are available.
    @Override
    public void onInitializeClient() {
        VeilRenderer.register();
        ClientVeilRadii.initialize();
        ClientVeilAnomalies.initialize();
        VeilAnomalyRenderer.register();
    }
}
