package ekuzo.echeloncore.entity;

import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Echelon's particles. Red Eye: a tiny glowing red dot, unaffected by darkness (the Falsekin's watching villager). */
public final class EchelonParticles {
    public static final SimpleParticleType RED_EYE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            EchelonCore.id("red_eye"), FabricParticleTypes.simple(true));

    private EchelonParticles() { }

    public static void register() {
        // Loading the class registers the particle types.
    }
}
