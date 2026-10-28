package ekuzo.echeloncore.entity.ability;

import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.Falsekin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mass Decoy (in combat): the Falsekin vanishes into a herd of 3 to 8 look-alike mobs. Hit the real one and it is
 * stunned for 2 seconds; hit a fake and that fake runs at you and explodes (see FalsekinMassDecoy). Only a Falsekin
 * can cast it: it needs the ability to wear other mobs.
 */
public final class MassDecoyAbility implements MobAbility {
    public static final Identifier ID = EchelonCore.id("mass_decoy");

    @Override public long cooldownTicks() { return 1_200; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return caster instanceof Falsekin falsekin && FalsekinAbilities.ready(falsekin)
                && context.target() instanceof LivingEntity target && target.isAlive() && falsekin.distanceTo(target) <= 16.0F;
    }

    @Override public void execute(MobAbilityContext context) {
        ((Falsekin) context.world().getEntity(context.caster().uuid())).massDecoyInCombat(context.world());
    }
}
