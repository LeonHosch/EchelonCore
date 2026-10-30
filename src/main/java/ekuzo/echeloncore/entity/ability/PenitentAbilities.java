package ekuzo.echeloncore.entity.ability;

import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.HollowedPenitent;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Shared pieces of the Hollowed Penitent's abilities (Grief Rend, Mouthless Wail, Tearless Mourning, Void Lunge, Grasp
 * of the Lost). Any mob may use them; on a Penitent they also set what he is doing ({@link HollowedPenitent#action()}),
 * which picks his animation.
 */
public final class PenitentAbilities {
    /** Ticks a broken Wail, Mourning or Grasp staggers the caster for. */
    static final int STAGGER_TICKS = 40;

    private PenitentAbilities() { }

    /** True when {@code caster} is a mob that is free to start an ability now. */
    static boolean ready(Entity caster) {
        return caster instanceof Mob mob && !Busy.is(mob) && mob.isAlive() && !mob.isDeadOrDying();
    }

    static boolean faceless(Mob mob) { return mob instanceof HollowedPenitent penitent && penitent.phase() >= 1; }

    /** Shows {@code action} on a Penitent (no-op on other mobs). */
    static void act(Mob mob, int action) {
        if (mob instanceof HollowedPenitent penitent) penitent.setAction(action);
    }

    /**
     * Holds the mob still for {@code ticks} while it shows {@code action}, unless it is in the middle of something
     * (then it just carries on). Used for the Penitent's phase change.
     */
    public static void transition(ServerLevel level, Mob mob, int action, int ticks) {
        if (Busy.is(mob)) return;
        Busy.start(mob);
        act(mob, action);
        int[] left = {ticks};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive() || --left[0] <= 0) {
                Busy.end(mob);
                act(mob, HollowedPenitent.NONE);
                return true;
            }
            Busy.holdStill(mob);
            return false;
        });
    }

    /** Broken out of an ability: the caster reels for {@link #STAGGER_TICKS} ticks and can do nothing. */
    static void stagger(ServerLevel level, Mob mob) {
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 1.4F, 0.5F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(),
                20, 0.5, 0.3, 0.5, 0.02);
        Busy.start(mob);
        act(mob, HollowedPenitent.STAGGER);
        int[] left = {STAGGER_TICKS};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive() || --left[0] <= 0) {
                Busy.end(mob);
                act(mob, HollowedPenitent.NONE);
                return true;
            }
            Busy.holdStill(mob);
            return false;
        });
    }

    /** Turns body and head towards {@code point}. */
    static void face(Mob mob, Vec3 point) {
        float yaw = (float) (Mth.atan2(point.z - mob.getZ(), point.x - mob.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
    }

    /** Damages {@code victim} as the caster's attack, even while the caster is busy (busy mobs' own hits are blocked). */
    static void hit(ServerLevel level, Mob mob, LivingEntity victim, float damage) {
        boolean busy = Busy.is(mob);
        Busy.end(mob);
        victim.hurtServer(level, level.damageSources().mobAttack(mob), damage);
        if (busy) Busy.start(mob);
    }

    /** Pushes {@code victim} away from {@code from}. */
    static void push(LivingEntity victim, Vec3 from, double strength, double up) {
        Vec3 away = victim.position().subtract(from).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
        victim.push(away.x * strength, up, away.z * strength);
        victim.hurtMarked = true;
    }

    /** Living things (no spectators) within {@code radius} of {@code center} other than the caster. */
    static List<LivingEntity> around(ServerLevel level, Mob mob, Vec3 center, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2.5, radius),
                entity -> entity != mob && entity.isAlive() && !(entity instanceof Player player && player.isSpectator())
                        && entity.position().multiply(1, 0, 1).distanceTo(center.multiply(1, 0, 1)) <= radius);
    }

    /** True when {@code entity} stands within {@code degrees} (the full arc) in front of {@code mob}. */
    static boolean inArc(Mob mob, Entity entity, double degrees) {
        Vec3 forward = Vec3.directionFromRotation(0, mob.getYRot());
        Vec3 to = entity.position().subtract(mob.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 0.25) return true;
        return forward.dot(to.normalize()) >= Math.cos(Math.toRadians(degrees / 2));
    }

    /**
     * A shockwave from {@code center}: everyone within {@code radius} takes {@code damage} and is thrown back. Used by
     * the Wail (silent), the Lunge's slam and Last Sorrow's pulses.
     */
    public static List<LivingEntity> shockwave(ServerLevel level, Mob mob, Vec3 center, double radius, float damage,
                                               double knockback, double up) {
        List<LivingEntity> hit = around(level, mob, center, radius);
        for (LivingEntity victim : hit) {
            hit(level, mob, victim, damage);
            push(victim, center, knockback, Math.max(up, 0.25));
        }
        for (double r = 1.0; r <= radius; r += 1.5) ring(level, center, r, net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                (int) (r * 6));
        return hit;
    }

    /** A flat ring of particles around {@code center}. */
    public static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions particle, int points) {
        for (int k = 0; k < points; k++) {
            double angle = Math.PI * 2 * k / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + 0.2, center.z + Math.sin(angle) * radius,
                    1, 0, 0.05, 0, 0.01);
        }
    }
}
