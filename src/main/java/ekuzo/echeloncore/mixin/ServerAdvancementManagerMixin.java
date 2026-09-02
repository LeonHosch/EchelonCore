package ekuzo.echeloncore.mixin;

import ekuzo.echeloncore.EchelonCore;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;
import java.util.stream.Collectors;

/** Replaces the game's advancement catalog with only EchelonCore-owned definitions on every data reload. */
@Mixin(ServerAdvancementManager.class)
public abstract class ServerAdvancementManagerMixin {
    // Filter the complete prepared map before Minecraft validates or publishes its advancement tree.
    @ModifyVariable(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Map<Identifier, Advancement> echelonCore$keepOnlyEchelonAdvancements(
            Map<Identifier, Advancement> loadedAdvancements) {
        Map<Identifier, Advancement> retained = loadedAdvancements.entrySet().stream()
                .filter(entry -> EchelonCore.MOD_ID.equals(entry.getKey().getNamespace()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        EchelonCore.LOGGER.info("Retained {} Echelon advancement definition(s) and removed {} other definition(s)",
                retained.size(), loadedAdvancements.size() - retained.size());
        return retained;
    }
}
