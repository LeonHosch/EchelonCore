package ekuzo.echeloncore.client;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import ekuzo.echeloncore.entity.Leshen;
import net.minecraft.resources.Identifier;
import ekuzo.echeloncore.entity.EchelonEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Renders Echelon's GeckoLib mobs from geckolib/models/entity/{id}.geo.json: the _glowmask texture glows, and the
 * "head" bone turns towards where the mob looks.
 */
public final class EchelonMobRenderer<T extends Monster & GeoEntity> extends GeoEntityRenderer<T, LivingEntityRenderState> {
    public EchelonMobRenderer(EntityRendererProvider.Context context, EntityType<T> type, float shadowRadius) {
        super(context, type);
        withRenderLayer(new AutoGlowingGeoLayer<>(this) {
            /**
             * While casting, the glow comes from {id}_glowmask_aim.png (pink, still aiming) and then
             * {id}_glowmask_cast.png (purple, aim locked): the Leshen's eyes and antler tips.
             */
            @Override
            protected Identifier getTextureResource(LivingEntityRenderState renderState) {
                Identifier glow = super.getTextureResource(renderState);
                return switch (renderState.getOrDefaultGeckolibData(CAST_PHASE, 0)) {
                    case Leshen.AIMING -> glow.withPath(glow.getPath().replace("_glowmask.png", "_glowmask_aim.png"));
                    case Leshen.LOCKED -> glow.withPath(glow.getPath().replace("_glowmask.png", "_glowmask_cast.png"));
                    default -> renderState.getOrDefaultGeckolibData(RAGE, false)
                            ? glow.withPath(glow.getPath().replace("_glowmask.png", "_glowmask_rage.png")) : glow;
                };
            }
        });
        this.shadowRadius = shadowRadius;
    }

    private static final DataTicket<Integer> CAST_PHASE = DataTicket.create("echelon_cast_phase", Integer.class);
    private static final DataTicket<Boolean> RAGE = DataTicket.create("echelon_rage", Boolean.class);

    @Override
    public void addRenderData(T animatable, Void relatedObject, LivingEntityRenderState renderState, float partialTick) {
        renderState.addGeckolibData(CAST_PHASE, animatable instanceof Leshen leshen ? leshen.castPhase() : 0);
        renderState.addGeckolibData(RAGE, animatable instanceof Leshen leshen && leshen.raging());
    }

    public static void register() {
        EntityRendererRegistry.register(EchelonEntities.LESHEN,
                context -> new EchelonMobRenderer<>(context, EchelonEntities.LESHEN, 0.9F));
        EntityRendererRegistry.register(EchelonEntities.CORRUPTED_STAG,
                context -> new EchelonMobRenderer<>(context, EchelonEntities.CORRUPTED_STAG, 1.1F));
        EntityRendererRegistry.register(EchelonEntities.CROW_SWARM, CrowSwarmRenderer::new);
        EntityRendererRegistry.register(EchelonEntities.SHADOW_WOLF,
                context -> new EchelonMobRenderer<>(context, EchelonEntities.SHADOW_WOLF, 0.5F));
        EntityRendererRegistry.register(EchelonEntities.ROOT_SNARE, RootSnareRenderer::new);
        EntityRendererRegistry.register(EchelonEntities.FALSEKIN,
                context -> new EchelonMobRenderer<>(context, EchelonEntities.FALSEKIN, 0.7F));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        DefaultAnimations.hardcodedHeadRotation(renderPassInfo, snapshots, "head");
    }
}
