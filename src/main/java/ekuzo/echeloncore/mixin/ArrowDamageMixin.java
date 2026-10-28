package ekuzo.echeloncore.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ekuzo.echeloncore.balance.VanillaWeapons;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weaker player bows and crossbows: a fully charged bow arrow deals 4 (it scales with charge and Power like
 * vanilla), a crossbow arrow 5-8. Their critical arrows add no random extra damage on top.
 */
@Mixin(AbstractArrow.class)
public abstract class ArrowDamageMixin {
    @ModifyExpressionValue(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;ceil(D)I"))
    private int echelon$weaponDamage(int damage) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!(arrow.getOwner() instanceof Player)) return damage;
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null) return damage;
        if (weapon.getItem() instanceof BowItem) return (int) Math.ceil(damage * VanillaWeapons.BOW_FACTOR);
        if (weapon.getItem() instanceof CrossbowItem)
            return VanillaWeapons.CROSSBOW_MIN + arrow.getRandom().nextInt(VanillaWeapons.CROSSBOW_MAX - VanillaWeapons.CROSSBOW_MIN + 1);
        return damage;
    }

    @ModifyExpressionValue(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;isCritArrow()Z"))
    private boolean echelon$noRandomCrit(boolean crit) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        ItemStack weapon = arrow.getWeaponItem();
        return crit && !(arrow.getOwner() instanceof Player && weapon != null
                && (weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem));
    }
}
