package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import ekuzo.crazyworldprogression.collectibles.CwpMount;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The Corrupted Stag as a mount: the stag's model and walk cycle, ridden like a horse (CWP collectibles). */
public class StagMount extends CwpMount implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public StagMount(EntityType<? extends StagMount> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
