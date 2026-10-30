package ekuzo.echeloncore.entity;

import ekuzo.echeloncore.EchelonCore;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Unbearable Heat, stacked by standing near a fallen star (one level every 2 seconds, up to 5; the Fallen Star script
 * gives it with an aura). At 5 stacks the player burns: fire that water cannot put out, 1.5 damage a second for as
 * long as the stacks last.
 */
public final class UnbearableHeat extends MobEffect {
    /** Amplifier of the fifth stack. */
    public static final int BURNING = 4;
    public static final Holder<MobEffect> EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            EchelonCore.id("unbearable_heat"), new UnbearableHeat());

    private UnbearableHeat() { super(MobEffectCategory.HARMFUL, 0xFF6A1A); }

    /** Shown as stacks on the left side of the screen (CWP's stack display), not as a vanilla effect icon. */
    public static void register() {
        ekuzo.crazyworldprogression.progression.equipment.StackedEffects.register(EchelonCore.id("unbearable_heat"));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remaining, int amplifier) { return true; }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (amplifier < BURNING) return true;
        // Water puts the flames out every tick, so they are lit again every tick and the damage comes from here.
        entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 20));
        if (entity.tickCount % 20 == 0) entity.hurtServer(level, level.damageSources().onFire(), 1.5F);
        if (entity.tickCount % 4 == 0) level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + 0.8,
                entity.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
        return true;
    }
}
