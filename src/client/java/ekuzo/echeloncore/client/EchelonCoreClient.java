package ekuzo.echeloncore.client;

import ekuzo.echeloncore.veil.VeilRenderer;
import ekuzo.crazyworldprogression.worldboundary.ClientBoundaryRadii;
import ekuzo.crazyworldprogression.worldboundary.restriction.ClientRestrictions;
import ekuzo.echeloncore.veil.anomaly.VeilAnomalyRenderer;
import net.fabricmc.api.ClientModInitializer;

public final class EchelonCoreClient implements ClientModInitializer {
    // Register Echelon's client-only veil rendering after Minecraft's client systems are available.
    @Override
    public void onInitializeClient() {
        RequiredClientModsClient.register();
        GeoArmorClient.register();
        EchelonMobRenderer.register();
        EchelonCollectibleRenderer.register();
        RedEyeParticle.register();
        // CWP's reforge window, styled as Echelon's Soulforging.
        ekuzo.crazyworldprogression.client.reforge.ReforgeScreenStyle.set(new SoulforgingStyle());
        ekuzo.crazyworldprogression.client.style.PanelStyle.set(new MedievalPanelStyle());
        ekuzo.crazyworldprogression.client.CwpClientSettings.setMenuTitle(
                net.minecraft.network.chat.Component.translatable("menu.echelon-core.settings"));
        // Maps (CWP's optional JourneyMap integration): nothing beyond the Veil border can be seen on the map.
        ekuzo.crazyworldprogression.client.map.MapData.setFogProvider(dimension -> {
            var radii = ekuzo.crazyworldprogression.worldboundary.ClientBoundaryRadii.get(dimension.identifier());
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (radii == null || level == null) return null;
            var center = dimension == net.minecraft.world.level.Level.OVERWORLD ? level.getRespawnData().pos() : net.minecraft.core.BlockPos.ZERO;
            return new ekuzo.crazyworldprogression.client.map.MapData.Fog(center.getX() + 0.5, center.getZ() + 0.5, radii.inner());
        });
        VeilRenderer.register();
        VeilAnomalyRenderer.register();
        SanctuaryTooltip.initialize();
    }
}
