package ekuzo.echeloncore.entity.ability;

import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.Leshen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shadow Step: the caster vanishes in smoke, moves to another spot near its target at three times its speed while
 * unseen, then reappears and resumes the fight. It cannot attack while vanished. Works for any mob that can walk.
 */
public final class ShadowStep implements MobAbility {
    public static final Identifier ID = EchelonCore.id("shadow_step");
    public static final long COOLDOWN = 240;
    private static final Identifier SPEED = EchelonCore.id("shadow_step_speed");
    private static final int MAX_TICKS = 60;
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    @Override public long cooldownTicks() { return COOLDOWN; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return caster instanceof Mob mob && !ACTIVE.contains(mob.getUUID()) && !Busy.is(mob)
                && context.target() instanceof LivingEntity target && target.isAlive() && mob.distanceTo(target) >= 3.0F;
    }

    @Override public void execute(MobAbilityContext context) {
        Mob mob = (Mob) context.world().getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        ServerLevel level = context.world();
        // Half the time a Leshen steps right behind its target and slams the ground as it reappears.
        if (mob instanceof Leshen && mob.getRandom().nextBoolean())
            start(level, mob, target, behind(level, mob, target, 2.6), () -> GroundSlam.perform(level, mob, target));
        else start(level, mob, target, destination(level, mob, target, 6, 10), () -> { });
    }

    /**
     * Vanishes, moves to {@code destination} at triple speed, reappears and runs {@code afterwards} (unless the mob died
     * on the way). The mob's target is restored when it reappears.
     */
    public static void start(ServerLevel level, Mob mob, LivingEntity target, Vec3 destination, Runnable afterwards) {
        ACTIVE.add(mob.getUUID());
        vanish(level, mob);
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED);
            speed.addTransientModifier(new AttributeModifier(SPEED, 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                ACTIVE.remove(mob.getUUID());
                return true;
            }
            ticks[0]++;
            mob.setTarget(null);                                // keeps its attack goals from pulling it straight back
            if (ticks[0] % 4 == 1) mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
            if (ticks[0] % 3 == 0) level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            if (mob.position().distanceToSqr(destination) > 2.25 && ticks[0] < MAX_TICKS) return false;
            if (speed != null) speed.removeModifier(SPEED);
            mob.getNavigation().stop();
            ACTIVE.remove(mob.getUUID());
            appear(level, mob);
            if (target.isAlive()) mob.setTarget(target);
            afterwards.run();
            return true;
        });
    }

    /** Whether the mob is in the middle of a Shadow Step (it can't attack then). */
    public static boolean active(Entity entity) { return ACTIVE.contains(entity.getUUID()); }

    /** Turns the mob invisible with a burst of smoke. */
    public static void vanish(ServerLevel level, Mob mob) {
        mob.setInvisible(true);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 30,
                0.5, mob.getBbHeight() / 3, 0.5, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 12,
                0.4, mob.getBbHeight() / 3, 0.4, 0.05);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.5F);
    }

    /** Makes the mob visible again with a burst of smoke. */
    public static void appear(ServerLevel level, Mob mob) {
        mob.setInvisible(false);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 30,
                0.5, mob.getBbHeight() / 3, 0.5, 0.02);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.4F);
    }

    /** A free spot behind the target (opposite the way it looks), {@code distance} blocks away, or any spot nearby. */
    public static Vec3 behind(ServerLevel level, Mob mob, LivingEntity target, double distance) {
        Vec3 look = Vec3.directionFromRotation(0, target.getYRot());
        for (double d : new double[]{distance, distance - 1, distance + 1}) {
            Vec3 spot = free(level, mob, BlockPos.containing(target.getX() - look.x * d, target.getY(), target.getZ() - look.z * d));
            if (spot != null) return spot;
        }
        return destination(level, mob, target, distance, distance + 2);
    }

    /** A free spot {@code min}-{@code max} blocks from the target, on solid ground with room for the mob. */
    private static Vec3 destination(ServerLevel level, Mob mob, LivingEntity target, double min, double max) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double radius = min + mob.getRandom().nextDouble() * (max - min);
            Vec3 spot = free(level, mob, BlockPos.containing(target.getX() + Math.cos(angle) * radius, target.getY(),
                    target.getZ() + Math.sin(angle) * radius));
            if (spot != null) return spot;
        }
        return mob.position();
    }

    /** A free spot {@code min}-{@code max} blocks from {@code center} with room for the mob, or null. */
    public static Vec3 spotNear(ServerLevel level, Mob mob, Vec3 center, double min, double max) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double radius = min + mob.getRandom().nextDouble() * (max - min);
            Vec3 spot = free(level, mob, BlockPos.containing(center.x + Math.cos(angle) * radius, center.y,
                    center.z + Math.sin(angle) * radius));
            if (spot != null) return spot;
        }
        return null;
    }

    private static Vec3 free(ServerLevel level, Mob mob, BlockPos base) {
        int height = (int) Math.ceil(mob.getBbHeight());
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            BlockPos pos = base.above(dy);
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
            boolean free = true;
            for (int h = 0; h < height && free; h++) free = level.getBlockState(pos.above(h)).getCollisionShape(level, pos.above(h)).isEmpty();
            if (free) return Vec3.atBottomCenterOf(pos);
        }
        return null;
    }
}
