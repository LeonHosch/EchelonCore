package ekuzo.echeloncore.client;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonEntities;
import ekuzo.echeloncore.entity.LeshenPet;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/** Mounts and pets drawn with an existing Echelon mob's model, texture and glow (the stag mount, the Leshen pet). */
public final class EchelonCollectibleRenderer<T extends LivingEntity & GeoEntity> extends GeoEntityRenderer<T, LivingEntityRenderState> {
    public EchelonCollectibleRenderer(EntityRendererProvider.Context context, String model, float scale, float shadowRadius) {
        super(context, new DefaultedEntityGeoModel<>(EchelonCore.id(model)));
        withRenderLayer(new AutoGlowingGeoLayer<>(this));
        withScale(scale);
        this.shadowRadius = shadowRadius;
    }

    public static void register() {
        EntityRendererRegistry.register(EchelonEntities.STAG_MOUNT,
                context -> new EchelonCollectibleRenderer<>(context, "corrupted_stag", 1.0F, 1.1F));
        EntityRendererRegistry.register(EchelonEntities.LESHEN_PET,
                context -> new EchelonCollectibleRenderer<>(context, "leshen", LeshenPet.SCALE, 0.25F));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        DefaultAnimations.hardcodedHeadRotation(renderPassInfo, snapshots, "head");
    }
}
