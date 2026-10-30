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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Void Lunge: the caster crouches and leaps at the farthest player within {@link #RANGE} blocks (at least
 * {@link #MIN_DISTANCE} away), so kiting it is punished. It aims where they stood when it jumped, so moving during the
 * crouch dodges it. Landing slams everyone within {@link #RADIUS} blocks for its attack damage.
 */
public final class VoidLunge implements MobAbility {
    public static final Identifier ID = EchelonCore.id("void_lunge");
    private static final double RANGE = 24.0;
    private static final double MIN_DISTANCE = 7.0;
    private static final int CROUCH = 14;
    private static final int FLIGHT = 16;
    private static final double RADIUS = 3.0;

    @Override public long cooldownTicks() { return 240; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return PenitentAbilities.ready(caster) && caster.onGround() && farthest(context.world(), (Mob) caster) != null;
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        Player victim = farthest(level, mob);
        Busy.start(mob);
        PenitentAbilities.act(mob, HollowedPenitent.LUNGE);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.2F, 0.7F);
        int[] ticks = {0};
        Vec3[] path = new Vec3[2];
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                finish(mob);
                return true;
            }
            ticks[0]++;
            if (ticks[0] <= CROUCH) {
                Busy.holdStill(mob);
                if (victim.isAlive()) PenitentAbilities.face(mob, victim.position());
                if (ticks[0] == CROUCH) {
                    path[0] = mob.position();
                    path[1] = victim.position();
                    level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.5F, 0.4F);
                }
                return false;
            }
            int t = ticks[0] - CROUCH;
            Vec3 from = path[0];
            Vec3 to = path[1];
            double apex = 2.5 + from.distanceTo(to) * 0.12;
            Vec3 next = arc(from, to, apex, Math.min(1.0, (t + 1) / (double) FLIGHT));
            mob.getNavigation().stop();
            mob.setDeltaMovement(next.subtract(mob.position()));
            mob.hurtMarked = true;
            mob.resetFallDistance();
            if (t % 2 == 0) level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 1, mob.getZ(), 3, 0.3, 0.4, 0.3, 0.01);
            if (t < 4 || t < FLIGHT && !mob.onGround()) return false;
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            finish(mob);
            slam(level, mob);
            return true;
        });
    }

    /** The point {@code progress} of the way along the leap from {@code from} to {@code to}. */
    private static Vec3 arc(Vec3 from, Vec3 to, double apex, double progress) {
        Vec3 flat = from.lerp(to, progress);
        return flat.add(0, apex * 4 * progress * (1 - progress), 0);
    }

    private static void slam(ServerLevel level, Mob mob) {
        float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        PenitentAbilities.shockwave(level, mob, mob.position(), RADIUS, damage, 0.9, 0.5);
        level.sendParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 0.3, mob.getZ(), 2, 0.6, 0.1, 0.6, 0);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.4F, 0.6F);
    }

    /** The farthest player it can see within {@link #RANGE} blocks but at least {@link #MIN_DISTANCE} away. */
    private static Player farthest(ServerLevel level, Mob mob) {
        Player best = null;
        double bestDistance = MIN_DISTANCE;
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(mob.blockPosition()).inflate(RANGE),
                player -> player.isAlive() && !player.isSpectator() && !player.isCreative() && mob.hasLineOfSight(player))) {
            double distance = mob.distanceTo(player);
            if (distance > RANGE || distance < bestDistance) continue;
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    private static void finish(Mob mob) {
        Busy.end(mob);
        PenitentAbilities.act(mob, HollowedPenitent.NONE);
    }
}
