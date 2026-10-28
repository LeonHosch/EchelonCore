package ekuzo.echeloncore.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import ekuzo.echeloncore.entity.CrowSwarm;
import ekuzo.echeloncore.entity.EchelonEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** The Crow Swarm flock (geckolib/models/entity/crow_swarm.geo.json); the crows' eyes glow. */
public final class CrowSwarmRenderer extends GeoEntityRenderer<CrowSwarm, EntityRenderState> {
    public CrowSwarmRenderer(EntityRendererProvider.Context context) {
        super(context, EchelonEntities.CROW_SWARM);
        withRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
