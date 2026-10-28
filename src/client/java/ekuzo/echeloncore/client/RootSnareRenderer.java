package ekuzo.echeloncore.client;

import com.geckolib.renderer.GeoEntityRenderer;
import ekuzo.echeloncore.entity.EchelonEntities;
import ekuzo.echeloncore.entity.RootSnare;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** The ring of roots of a Root Snare (geckolib/models/entity/root_snare.geo.json). */
public final class RootSnareRenderer extends GeoEntityRenderer<RootSnare, EntityRenderState> {
    public RootSnareRenderer(EntityRendererProvider.Context context) {
        super(context, EchelonEntities.ROOT_SNARE);
    }
}
