package ekuzo.echeloncore.client;

import ekuzo.echeloncore.entity.EchelonParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;

/** A tiny red dot that glows at full brightness for a few ticks and doesn't move: one eye of the watching villager. */
public final class RedEyeParticle extends SingleQuadParticle {
    private RedEyeParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.lifetime = 3;
        this.gravity = 0;
        this.hasPhysics = false;
        this.quadSize = 0.04F;
        setColor(1.0F, 0.08F, 0.05F);
    }

    @Override
    protected Layer getLayer() { return Layer.OPAQUE; }

    @Override
    public int getLightCoords(float partialTick) { return 0xF000F0; }

    public static void register() {
        ParticleProviderRegistry.getInstance().register(EchelonParticles.RED_EYE, sprites ->
                (ParticleProvider<SimpleParticleType>) (type, level, x, y, z, dx, dy, dz, random) ->
                        new RedEyeParticle(level, x, y, z, sprites.first()));
    }
}
