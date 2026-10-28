package ekuzo.echeloncore.entity.ability;

import com.geckolib.animatable.GeoEntity;
import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.Falsekin;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Flay: a raking claw that tears skin. The target bleeds for 1 damage a second for {@link #BLEED_SECONDS} seconds
 * (ignoring armor), and the caster heals for everything Flay and the bleeding deal: it lives on stolen skin.
 */
public final class Flay implements MobAbility {
    public static final Identifier ID = EchelonCore.id("flay");
    private static final int BLEED_SECONDS = 5;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(0x8A0E12, 1.0F);

    @Override public long cooldownTicks() { return 220; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return FalsekinAbilities.ready(caster) && context.target() instanceof LivingEntity target && target.isAlive()
                && ((Mob) caster).distanceTo(target) <= 3.2F;
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        if (mob instanceof GeoEntity geo) geo.triggerAnim("abilities", "lunge");
        Busy.start(mob);
        EchelonTasks.later(7, () -> {
            Busy.end(mob);
            if (!mob.isAlive() || !target.isAlive() || mob.distanceTo(target) > 4.0F) return;
            float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (target.hurtServer(level, level.damageSources().mobAttack(mob), damage)) mob.heal(damage);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), Falsekin.soundById("entity.player.attack.sweep"),
                    SoundSource.HOSTILE, 1.0F, 0.6F);
            int[] seconds = {0};
            EchelonTasks.everyTick(() -> {
                if (!target.isAlive() || seconds[0] >= BLEED_SECONDS * 20) return true;
                if (++seconds[0] % 20 == 0) {
                    target.invulnerableTime = 0;
                    if (target.hurtServer(level, level.damageSources().magic(), 1.0F) && mob.isAlive()) mob.heal(1.0F);
                    level.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                            8, 0.25, 0.3, 0.25, 0.02);
                }
                return false;
            });
        });
    }
}
