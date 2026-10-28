package ekuzo.echeloncore.mixin;

import ekuzo.echeloncore.item.Sanctuary;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells Sanctuary about attacks a player blocked; the shield is read first, since blocking can break it. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBlockMixin {
    @Unique private ItemStack echelon$blockingWith = ItemStack.EMPTY;

    @Inject(method = "applyItemBlocking", at = @At("HEAD"))
    private void echelon$rememberShield(ServerLevel level, DamageSource source, float damage,
                                        CallbackInfoReturnable<Float> info) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        ItemStack stack = player.getItemBlockingWith();
        echelon$blockingWith = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    @Inject(method = "applyItemBlocking", at = @At("RETURN"))
    private void echelon$sanctuary(ServerLevel level, DamageSource source, float damage,
                                   CallbackInfoReturnable<Float> info) {
        ItemStack shield = echelon$blockingWith;
        echelon$blockingWith = ItemStack.EMPTY;
        if (info.getReturnValueF() > 0 && (Object) this instanceof ServerPlayer player) Sanctuary.onBlocked(player, shield);
    }
}
