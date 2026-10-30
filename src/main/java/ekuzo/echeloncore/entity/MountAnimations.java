package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.constant.DefaultAnimations;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Animations of the ridden mounts (stag, unicorn): the jump pose while in the air, the walk cycle sped up to match
 * how fast the mount actually moves, and idle when standing.
 */
final class MountAnimations {
    /** Legs tucked in, tail up (move.jump in the mount's animation file). */
    static final RawAnimation JUMP = RawAnimation.begin().thenLoop("move.jump");
    /** Walk cycles per second for each block per tick of ground speed, and the limits. */
    private static final double STRIDE = 3.6, MIN_SPEED = 0.5, MAX_SPEED = 3.2;

    private MountAnimations() { }

    static <T extends LivingEntity & GeoAnimatable> AnimationController<T> controller() {
        return new AnimationController<>("Mount", 3, test -> {
            T mount = test.animatable();
            double rise = mount.getY() - mount.yo;
            if (!mount.onGround() && !mount.isInWater() && Math.abs(rise) > 0.04) {
                test.setControllerSpeed(1);
                return test.setAndContinue(JUMP);
            }
            double ground = Math.hypot(mount.getX() - mount.xo, mount.getZ() - mount.zo);
            if (ground > 0.015) {
                test.setControllerSpeed((float) Mth.clamp(ground * STRIDE, MIN_SPEED, MAX_SPEED));
                return test.setAndContinue(DefaultAnimations.WALK);
            }
            test.setControllerSpeed(1);
            return test.setAndContinue(DefaultAnimations.IDLE);
        });
    }
}
