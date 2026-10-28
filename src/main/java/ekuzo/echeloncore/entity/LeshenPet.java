package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import ekuzo.crazyworldprogression.collectibles.CwpPet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A Leshen pet: the Leshen's model at a quarter of its size, following its owner (CWP collectibles). */
public class LeshenPet extends CwpPet implements GeoEntity {
    /** The pet's size compared to a Leshen; its renderer and hitbox use it. */
    public static final float SCALE = 0.25F;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public LeshenPet(EntityType<? extends LeshenPet> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
