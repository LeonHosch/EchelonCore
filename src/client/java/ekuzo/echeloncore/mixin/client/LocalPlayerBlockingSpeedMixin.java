package ekuzo.echeloncore.mixin.client;

import ekuzo.crazyworldprogression.accessory.Accessories;
import ekuzo.echeloncore.item.EchelonItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Steadfast Charm: raising a shield (any item that blocks attacks) no longer slows the player down. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerBlockingSpeedMixin {
    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void echelon$steadfastCharm(CallbackInfoReturnable<Float> info) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!player.getUseItem().has(DataComponents.BLOCKS_ATTACKS)) return;
        if (Accessories.worn(player).stream().anyMatch(stack -> stack.is(EchelonItems.STEADFAST_CHARM)))
            info.setReturnValue(1.0F);
    }
}
