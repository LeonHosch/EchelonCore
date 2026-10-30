package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import ekuzo.crazyworldprogression.collectibles.CwpMount;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The Unicorn mount: a white horse with a rainbow mane and a glowing spiral horn, ridden like a horse (CWP
 * collectibles; source art/unicorn.bbmodel). In the air it can jump a second time on a rainbow fart (UnicornBoost).
 */
public class UnicornMount extends CwpMount implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public UnicornMount(EntityType<? extends UnicornMount> type, Level level) {
        super(type, level);
    }

    /** Lands softly, however high the boost carried it. */
    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(MountAnimations.controller());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
