package ekuzo.echeloncore.mixin.client;

import ekuzo.echeloncore.client.FalsekinDisguise;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A disguised Falsekin is drawn as the mob it wears (its client-only copy). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherDisguiseMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void echelon$disguise(E entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        Entity copy = FalsekinDisguise.copyFor(entity);
        if (copy != null) cir.setReturnValue(((EntityRenderDispatcher) (Object) this).extractEntity(copy, partialTick));
    }
}
