package ekuzo.echeloncore.client;

import ekuzo.echeloncore.entity.Falsekin;
import ekuzo.echeloncore.mixin.client.WalkAnimationStateAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makes a disguised Falsekin look exactly like the mob it wears: each Falsekin gets a client-only copy of that
 * mob (never added to the world) that follows its position, rotation, walk cycle and hurt flash, and the game renders
 * the copy in its place (EntityRenderDispatcherDisguiseMixin). While it twitches, the copy's head jerks around.
 */
public final class FalsekinDisguise {
    private static final Map<Falsekin, Entity> COPIES = new WeakHashMap<>();

    private FalsekinDisguise() { }

    /** The copy to render instead of {@code entity}, or null to render it normally. */
    public static Entity copyFor(Entity entity) {
        if (!(entity instanceof Falsekin falsekin)) return null;
        EntityType<?> type = falsekin.disguise();
        if (type == null) {
            COPIES.remove(falsekin);
            return null;
        }
        Entity copy = COPIES.get(falsekin);
        if (copy == null || copy.getType() != type || copy.level() != falsekin.level()) {
            copy = type.create(falsekin.level(), EntitySpawnReason.LOAD);
            if (copy == null) return null;
            // Its own negative ID, so it never matches a real entity.
            copy.setId(-1_000_000 - falsekin.getId());
            COPIES.put(falsekin, copy);
        }
        follow(falsekin, copy);
        return copy;
    }

    private static void follow(Falsekin falsekin, Entity copy) {
        copy.setPos(falsekin.getX(), falsekin.getY(), falsekin.getZ());
        copy.xo = falsekin.xo;
        copy.yo = falsekin.yo;
        copy.zo = falsekin.zo;
        copy.xOld = falsekin.xOld;
        copy.yOld = falsekin.yOld;
        copy.zOld = falsekin.zOld;
        copy.setYRot(falsekin.getYRot());
        copy.yRotO = falsekin.yRotO;
        copy.setXRot(falsekin.getXRot());
        copy.xRotO = falsekin.xRotO;
        copy.tickCount = falsekin.tickCount;
        copy.setOnGround(falsekin.onGround());
        copy.setInvisible(falsekin.isInvisible());
        if (copy instanceof LivingEntity living) {
            living.yBodyRot = falsekin.yBodyRot;
            living.yBodyRotO = falsekin.yBodyRotO;
            living.yHeadRot = falsekin.yHeadRot;
            living.yHeadRotO = falsekin.yHeadRotO;
            living.hurtTime = falsekin.hurtTime;
            living.hurtDuration = falsekin.hurtDuration;
            living.deathTime = falsekin.deathTime;
            var from = (WalkAnimationStateAccessor) falsekin.walkAnimation;
            var to = (WalkAnimationStateAccessor) living.walkAnimation;
            to.echelon$setPosition(from.echelon$position());
            to.echelon$setSpeed(from.echelon$speed());
            to.echelon$setSpeedOld(from.echelon$speedOld());
            if (falsekin.twitching()) {
                // Jerky, unnatural head snaps.
                var random = falsekin.getRandom();
                float yaw = (random.nextFloat() - 0.5F) * 140.0F;
                living.yHeadRot = falsekin.yBodyRot + yaw;
                living.yHeadRotO = living.yHeadRot;
                living.setXRot((random.nextFloat() - 0.5F) * 120.0F);
                living.xRotO = living.getXRot();
            }
        }
    }
}
