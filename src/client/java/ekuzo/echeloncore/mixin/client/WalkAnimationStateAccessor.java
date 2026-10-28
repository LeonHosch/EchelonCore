package ekuzo.echeloncore.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a disguise copy share its Falsekin's walk cycle. */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("position") float echelon$position();
    @Accessor("position") void echelon$setPosition(float value);
    @Accessor("speed") float echelon$speed();
    @Accessor("speed") void echelon$setSpeed(float value);
    @Accessor("speedOld") float echelon$speedOld();
    @Accessor("speedOld") void echelon$setSpeedOld(float value);
}
