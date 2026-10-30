package ekuzo.echeloncore.entity.ability;

import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.HollowedPenitent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Grasp of the Lost: the caster reaches out for {@link #TELL} ticks (a clear tell: step aside or back), then closes
 * its hand on whoever is still in front of it within {@link #GRAB_REACH} blocks and holds them in place for up to
 * {@link #HOLD} ticks, {@link #DAMAGE} every half second. Dealing {@link #BREAK} damage to the caster meanwhile (the
 * held player can do it, or their friends), or a stun, makes it let go and stagger.
 */
public final class GraspOfTheLost implements MobAbility {
    public static final Identifier ID = EchelonCore.id("grasp_of_the_lost");
    private static final double RANGE = 5.0;
    private static final double GRAB_REACH = 3.6;
    private static final double GRAB_ARC = 70.0;
    private static final int TELL = 16;
    private static final int AIM_LOCK = 8;
    private static final int HOLD = 80;
    private static final float DAMAGE = 3.0F;
    /** Damage (after armor) that breaks the hold. */
    private static final float BREAK = 14.0F;

    @Override public long cooldownTicks() { return 300; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return PenitentAbilities.ready(caster) && context.target() instanceof LivingEntity target && target.isAlive()
                && ((Mob) caster).distanceTo(target) <= RANGE;
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        Busy.start(mob);
        PenitentAbilities.act(mob, HollowedPenitent.REACH);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.HOSTILE, 1.5F, 0.5F);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                finish(mob);
                return true;
            }
            Busy.holdStill(mob);
            ticks[0]++;
            if (ticks[0] <= AIM_LOCK && target.isAlive()) PenitentAbilities.face(mob, target.position());
            Vec3 hand = hand(mob);
            if (ticks[0] % 2 == 0) level.sendParticles(ParticleTypes.SQUID_INK, hand.x, hand.y, hand.z, 2, 0.15, 0.15, 0.15, 0.01);
            if (ticks[0] < TELL) return false;
            LivingEntity held = grabbed(level, mob, target);
            if (held == null) {
                // Missed: the hand closes on nothing.
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 1.0F, 0.5F);
                finish(mob);
                return true;
            }
            hold(level, mob, held);
            return true;
        });
    }

    /** Who the closing hand catches: the target if it is still in reach, else whoever else is. */
    private static LivingEntity grabbed(ServerLevel level, Mob mob, LivingEntity target) {
        if (caught(mob, target)) return target;
        for (LivingEntity other : PenitentAbilities.around(level, mob, mob.position(), GRAB_REACH))
            if (caught(mob, other) && other instanceof net.minecraft.world.entity.player.Player) return other;
        return null;
    }

    private static boolean caught(Mob mob, LivingEntity entity) {
        return entity.isAlive() && mob.distanceTo(entity) <= GRAB_REACH && PenitentAbilities.inArc(mob, entity, GRAB_ARC);
    }

    private static void hold(ServerLevel level, Mob mob, LivingEntity held) {
        PenitentAbilities.act(mob, HollowedPenitent.HOLD);
        Interrupt.watch(mob);
        Vec3 spot = held.position();
        level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.2F, 0.6F);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            ticks[0]++;
            if (!mob.isAlive() || !held.isAlive() || held.level() != level || ticks[0] > HOLD) {
                finish(mob);
                return true;
            }
            Busy.holdStill(mob);
            if (Interrupt.broken(mob, BREAK)) {
                finish(mob);
                PenitentAbilities.push(held, mob.position(), 0.6, 0.3);
                PenitentAbilities.stagger(level, mob);
                return true;
            }
            PenitentAbilities.face(mob, held.position());
            // Held in place: wherever they try to go, the hand keeps them where it caught them.
            if (held.position().distanceToSqr(spot) > 0.04) held.teleportTo(spot.x, spot.y, spot.z);
            held.setDeltaMovement(0, Math.min(0, held.getDeltaMovement().y), 0);
            held.hurtMarked = true;
            if (ticks[0] % 10 == 0) {
                PenitentAbilities.hit(level, mob, held, DAMAGE);
                level.sendParticles(ParticleTypes.SQUID_INK, held.getX(), held.getY() + held.getBbHeight() * 0.6, held.getZ(),
                        6, 0.25, 0.3, 0.25, 0.02);
            }
            return false;
        });
    }

    private static Vec3 hand(Mob mob) {
        return mob.position().add(Vec3.directionFromRotation(0, mob.getYRot()).scale(mob.getBbWidth() / 2 + 1.4)).add(0, 1.4, 0);
    }

    private static void finish(Mob mob) {
        Busy.end(mob);
        Interrupt.stop(mob);
        PenitentAbilities.act(mob, HollowedPenitent.NONE);
    }
}
