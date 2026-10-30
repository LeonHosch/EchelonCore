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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Mouthless Wail: the caster plants itself, throws its head back and tries to scream, but there is no sound, only a
 * low, muffled heave. After {@link #WIND_UP} ticks a silent shockwave rolls out {@link #RADIUS} blocks: everyone in it
 * takes {@link #DAMAGE}, is thrown back, and gets Darkness for 4 seconds and Slowness. Dealing {@link #BREAK} damage
 * during the wind-up (or a stun) chokes it off and the caster staggers for two seconds. In Faceless Fury it wails
 * every cycle.
 */
public final class MouthlessWail implements MobAbility {
    public static final Identifier ID = EchelonCore.id("mouthless_wail");
    private static final int WIND_UP = 30;
    private static final double RADIUS = 8.0;
    private static final float DAMAGE = 6.0F;
    /** Damage (after armor) that breaks the wind-up: about two strong hits from one player. */
    private static final float BREAK = 16.0F;
    private static final int COOLDOWN = 400;
    private static final int FURY_COOLDOWN = 200;

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return PenitentAbilities.ready(caster) && context.target() instanceof LivingEntity target && target.isAlive()
                && ((Mob) caster).distanceTo(target) <= RADIUS - 1;
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        MobForgeEntityData.setCooldown(mob, ID, level.getGameTime() + (PenitentAbilities.faceless(mob) ? FURY_COOLDOWN : COOLDOWN));
        Busy.start(mob);
        Interrupt.watch(mob);
        PenitentAbilities.face(mob, target.position());
        PenitentAbilities.act(mob, HollowedPenitent.WAIL);
        // The tell: no scream, only a deep, choked intake of breath.
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.HOSTILE, 1.5F, 0.4F);
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
                PenitentAbilities.stagger(level, mob);
                return true;
            }
            if (ticks[0] % 6 == 0) {
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.8F, 0.5F);
                PenitentAbilities.ring(level, mob.position(), RADIUS * ticks[0] / WIND_UP, ParticleTypes.SQUID_INK, 20);
            }
            if (ticks[0] < WIND_UP) return false;
            finish(mob);
            release(level, mob);
            return true;
        });
    }

    private static void release(ServerLevel level, Mob mob) {
        for (LivingEntity victim : PenitentAbilities.shockwave(level, mob, mob.position(), RADIUS, DAMAGE, 1.3, 0.4)) {
            victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
        }
        // Silent: only the air shuddering.
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.25F, 0.3F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, mob.getX(), mob.getEyeY(), mob.getZ(), 1, 0, 0, 0, 0);
    }

    private static void finish(Mob mob) {
        Busy.end(mob);
        Interrupt.stop(mob);
        PenitentAbilities.act(mob, HollowedPenitent.NONE);
    }
}
