package ekuzo.echeloncore.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Creates a render type for EchelonCore's registered opaque anomaly pipeline. */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    // Wrap a custom render setup in the render type expected by submit geometry nodes.
    @Invoker("create")
    static RenderType echelonCore$create(String name, RenderSetup setup) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
