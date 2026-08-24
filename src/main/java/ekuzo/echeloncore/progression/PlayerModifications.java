package ekuzo.echeloncore.progression;

import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class PlayerModifications {
    private static final Identifier MINING_SPEED = EchelonCore.id("mining_speed_multiplier");
    private static final Identifier HEALTH = EchelonCore.id("health_modifier");
    private static final Identifier MOVEMENT_SPEED = EchelonCore.id("movement_speed_modifier");

    private PlayerModifications() { }

    public static void initialize() {
        ServerPlayerEvents.JOIN.register(PlayerModifications::applyAll);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyAll(newPlayer));
    }

    private static void applyAll(ServerPlayer player) {
        apply(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), MINING_SPEED, 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED, 0.4,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        apply(player.getAttribute(Attributes.MAX_HEALTH), HEALTH, -4.0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void apply(AttributeInstance attribute, Identifier id, double amount,
                              AttributeModifier.Operation operation) {
        if (attribute != null) attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }
}
