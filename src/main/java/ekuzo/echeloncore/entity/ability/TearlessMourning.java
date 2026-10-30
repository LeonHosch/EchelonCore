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

/**
 * Tearless Mourning: about every 45 seconds the caster kneels, clutches where its face was and its shoulders heave
 * for {@link #DURATION} ticks. A Penitent takes +50% damage meanwhile (the group's burst window, see
 * HollowedPenitent#hurtServer). If it gets through without being broken it fails to cry, snaps up in rage and gains
 * an Anguish stack; dealing {@link #BREAK} damage (or a stun) breaks it instead, removes a stack and staggers it.
 */
public final class TearlessMourning implements MobAbility {
    public static final Identifier ID = EchelonCore.id("tearless_mourning");
    private static final int DURATION = 80;
    /** Damage (after armor, with the +50%) that breaks it: reachable alone with good gear over the four seconds. */
    private static final float BREAK = 30.0F;
    /** Ticks of the rage snap at the end of a failed Mourning. */
    private static final int SNAP = 20;

    @Override public long cooldownTicks() { return 900; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return PenitentAbilities.ready(caster) && context.target() instanceof LivingEntity target && target.isAlive()
                && ((Mob) caster).distanceTo(target) <= 16;
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        Busy.start(mob);
        Interrupt.watch(mob);
        PenitentAbilities.act(mob, HollowedPenitent.MOURNING);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                finish(mob);
                return true;
            }
            Busy.holdStill(mob);
            ticks[0]++;
            if (Interrupt.broken(mob, BREAK)) {
                finish(mob);
                if (mob instanceof HollowedPenitent penitent) penitent.addAnguish(-1);
                PenitentAbilities.stagger(level, mob);
                return true;
            }
            // Dry sobs: a choked breath with every heave of the shoulders, and not one tear.
            if (ticks[0] % 20 == 5) level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_NEARBY_CLOSE,
                    SoundSource.HOSTILE, 0.9F, 0.5F);
            if (ticks[0] % 5 == 0) level.sendParticles(ParticleTypes.ASH, mob.getX(), mob.getY() + 1.6, mob.getZ(), 4, 0.3, 0.2, 0.3, 0);
            if (ticks[0] < DURATION) return false;
            Interrupt.stop(mob);
            snap(level, mob);
            return true;
        });
    }

    /** He fails to cry, and the rage takes him: a voiceless roar and a new Anguish stack. */
    private static void snap(ServerLevel level, Mob mob) {
        PenitentAbilities.act(mob, HollowedPenitent.ANGUISH);
        if (mob instanceof HollowedPenitent penitent) penitent.addAnguish(1);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 2.2, mob.getZ(), 40, 0.6, 0.5, 0.6, 0.06);
        int[] left = {SNAP};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive() || --left[0] <= 0) {
                finish(mob);
                return true;
            }
            Busy.holdStill(mob);
            return false;
        });
    }

    private static void finish(Mob mob) {
        Busy.end(mob);
        Interrupt.stop(mob);
        PenitentAbilities.act(mob, HollowedPenitent.NONE);
    }
}
