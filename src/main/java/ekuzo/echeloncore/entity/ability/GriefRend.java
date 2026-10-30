package ekuzo.echeloncore.entity.ability;

import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.crazyworldprogression.mobforge.entity.MobForgeEntityData;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Grief Rend: a two-hit claw combo. Each swing cleaves everyone within {@link #REACH} blocks in a {@link #ARC} degree
 * arc in front of the caster; the first rakes for {@link #FIRST} of its attack damage, the second, heavier backhand
 * for {@link #SECOND} and throws them back. In Faceless Fury it swings faster and comes back sooner.
 */
public final class GriefRend implements MobAbility {
    public static final Identifier ID = EchelonCore.id("grief_rend");
    private static final double REACH = 4.5;
    private static final double ARC = 100.0;
    private static final float FIRST = 0.8F;
    private static final float SECOND = 1.3F;
    /** Ticks to the first and second swing (Faceless Fury: three quarters of that). */
    private static final int FIRST_HIT = 10;
    private static final int SECOND_HIT = 22;
    private static final int END = 30;
    private static final int COOLDOWN = 120;
    private static final int FURY_COOLDOWN = 80;

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return PenitentAbilities.ready(caster) && context.target() instanceof LivingEntity target && target.isAlive()
                && ((Mob) caster).distanceTo(target) <= REACH - 0.5;
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        boolean fury = PenitentAbilities.faceless(mob);
        MobForgeEntityData.setCooldown(mob, ID, level.getGameTime() + (fury ? FURY_COOLDOWN : COOLDOWN));
        float speed = fury ? 0.75F : 1.0F;
        int first = Math.round(FIRST_HIT * speed);
        int second = Math.round(SECOND_HIT * speed);
        int end = Math.round(END * speed);
        Busy.start(mob);
        PenitentAbilities.act(mob, HollowedPenitent.REND);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.6F, 0.5F);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                finish(mob);
                return true;
            }
            Busy.holdStill(mob);
            ticks[0]++;
            if (ticks[0] < first - 2 && target.isAlive()) PenitentAbilities.face(mob, target.position());
            if (ticks[0] == first) swing(level, mob, FIRST, 0.4);
            if (ticks[0] == second) swing(level, mob, SECOND, 1.1);
            if (ticks[0] < end) return false;
            finish(mob);
            return true;
        });
    }

    private static void swing(ServerLevel level, Mob mob, float share, double knockback) {
        float damage = (float) (mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * share);
        for (LivingEntity victim : PenitentAbilities.around(level, mob, mob.position(), REACH)) {
            if (!PenitentAbilities.inArc(mob, victim, ARC)) continue;
            PenitentAbilities.hit(level, mob, victim, damage);
            PenitentAbilities.push(victim, mob.position(), knockback, 0.2);
        }
        Vec3 forward = Vec3.directionFromRotation(0, mob.getYRot());
        for (int k = -2; k <= 2; k++) {
            Vec3 side = forward.yRot((float) Math.toRadians(k * ARC / 5)).scale(2.6);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, mob.getX() + side.x, mob.getY() + 1.3, mob.getZ() + side.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F, 0.5F);
    }

    private static void finish(Mob mob) {
        Busy.end(mob);
        PenitentAbilities.act(mob, HollowedPenitent.NONE);
    }
}
