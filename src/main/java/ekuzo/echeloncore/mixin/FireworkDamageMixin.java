package ekuzo.echeloncore.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import ekuzo.echeloncore.balance.VanillaWeapons;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fireworks shot from a crossbow deal 5-8 like crossbow arrows (vanilla: 5 + 2 per firework star). A direct hit deals
 * the full roll; the splash falls off with distance from the target's body instead of its feet.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkDamageMixin {
    @ModifyVariable(method = "dealExplosionDamage", at = @At(value = "STORE", ordinal = 1), ordinal = 0)
    private float echelon$crossbowFirework(float damage) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        if (damage <= 0 || !rocket.isShotAtAngle()) return damage;
        return VanillaWeapons.CROSSBOW_MIN + rocket.getRandom().nextInt(VanillaWeapons.CROSSBOW_MAX - VanillaWeapons.CROSSBOW_MIN + 1);
    }

    @ModifyExpressionValue(method = "dealExplosionDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;distanceTo(Lnet/minecraft/world/entity/Entity;)F"))
    private float echelon$distanceToBody(float distance, @Local LivingEntity target) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        if (!rocket.isShotAtAngle()) return distance;
        return (float) Math.sqrt(target.getBoundingBox().distanceToSqr(rocket.position()));
    }
}
