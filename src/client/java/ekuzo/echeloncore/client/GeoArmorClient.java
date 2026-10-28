package ekuzo.echeloncore.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedGeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.item.EchelonItems;
import ekuzo.echeloncore.item.GeoArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Installs the GeckoLib armor models (one shared model per set for all four slots). Hanging cloth is split into the
 * optional bones armorTabard (front) and armorCloak (back), hinged so they swing out of the way of the legs.
 */
public final class GeoArmorClient {
    private GeoArmorClient() {
    }

    public static void register() {
        attach("guardian_of_life", EchelonItems.GUARDIAN_OF_LIFE_HELMET, EchelonItems.GUARDIAN_OF_LIFE_CHESTPLATE,
                EchelonItems.GUARDIAN_OF_LIFE_LEGGINGS, EchelonItems.GUARDIAN_OF_LIFE_BOOTS);
        attach("stormcaller", EchelonItems.STORMCALLER_HELMET, EchelonItems.STORMCALLER_CHESTPLATE,
                EchelonItems.STORMCALLER_LEGGINGS, EchelonItems.STORMCALLER_BOOTS);
        attach("icarus_folly", EchelonItems.ICARUS_FOLLY_HELMET, EchelonItems.ICARUS_FOLLY_CHESTPLATE,
                EchelonItems.ICARUS_FOLLY_LEGGINGS, EchelonItems.ICARUS_FOLLY_BOOTS);
        attach("sage", EchelonItems.SAGE_HELMET, EchelonItems.SAGE_CHESTPLATE, EchelonItems.SAGE_LEGGINGS,
                EchelonItems.SAGE_BOOTS);
    }

    /** One shared model (geckolib/models/armor/{@code model}.geo.json) for all pieces of a set. */
    private static void attach(String model, GeoArmorItem... items) {
        for (GeoArmorItem item : items) attach(model, item);
    }

    private static void attach(String model, GeoArmorItem item) {
        item.setGeoRenderProvider(new GeoRenderProvider() {
            private final GeoArmorRenderer<GeoArmorItem, HumanoidRenderState> renderer = createRenderer(model);

            @Override
            public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
                return this.renderer;
            }
        });
    }

    private static GeoArmorRenderer<GeoArmorItem, HumanoidRenderState> createRenderer(String name) {
        var model = new DefaultedGeoModel<GeoArmorItem>(EchelonCore.id(name)) {
            @Override
            protected String subtype() {
                return "armor";
            }
        };
        var renderer = new GeoArmorRenderer<GeoArmorItem, HumanoidRenderState>(model) {
            @Override
            public void adjustModelBonesForRender(RenderPassInfo<HumanoidRenderState> renderPassInfo, BoneSnapshots snapshots) {
                super.adjustModelBonesForRender(renderPassInfo, snapshots);
                if (renderPassInfo.renderState().getGeckolibData(CURRENT_SLOT) != EquipmentSlot.CHEST) return;
                HumanoidModel<?> base = renderPassInfo.renderState().getGeckolibData(BASE_MODEL);
                if (base == null) return;
                swingCloth(base, snapshots);
            }
        };
        renderer.withRenderLayer(new AutoGlowingGeoLayer<>(renderer));
        return renderer;
    }

    /**
     * Vanilla leg xRot is negative when a leg swings forward; GeckoLib bone rotX is the negated value. The tabard
     * follows whichever leg is furthest forward, the cloak is pushed back by the rear leg (less, as it hangs from the
     * shoulders, twice as far from the hip).
     */
    private static void swingCloth(HumanoidModel<?> base, BoneSnapshots snapshots) {
        float forward = Math.min(base.rightLeg.xRot, base.leftLeg.xRot) - base.body.xRot;
        float back = Math.max(base.rightLeg.xRot, base.leftLeg.xRot) - base.body.xRot;
        snapshots.ifPresent("armorTabard", bone -> bone.setRotX(Math.max(0, -forward)));
        snapshots.ifPresent("armorCloak", bone -> bone.setRotX(-0.06f - Math.max(0, back) * 0.6f));
    }
}
