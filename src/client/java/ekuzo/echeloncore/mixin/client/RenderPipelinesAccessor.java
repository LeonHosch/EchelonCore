package ekuzo.echeloncore.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes Minecraft's pipeline registry for EchelonCore's depth-writing anomaly pipeline. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    // Register a custom pipeline alongside Minecraft's static world-rendering pipelines.
    @Invoker("register")
    static RenderPipeline echelonCore$register(RenderPipeline pipeline) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
