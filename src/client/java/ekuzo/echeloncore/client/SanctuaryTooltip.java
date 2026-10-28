package ekuzo.echeloncore.client;

import ekuzo.crazyworldprogression.progression.equipment.EquipmentSetRegistry;
import ekuzo.crazyworldprogression.progression.equipment.EquipmentSetService;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.item.EchelonItems;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The Oath of the Lifebound's tooltip (what Sanctuary does, and whether the Guardian of Life set unlocks it), and
 * the Steadfast Charm's effect line.
 */
final class SanctuaryTooltip {
    private SanctuaryTooltip() { }

    static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (stack.is(EchelonItems.STEADFAST_CHARM))
                lines.add(Component.translatable("tooltip.echelon-core.steadfast_charm").withStyle(ChatFormatting.GRAY));
            if (!stack.is(EchelonItems.OATH_OF_THE_LIFEBOUND)) return;
            boolean unlocked = unlocked();
            lines.add(Component.empty());
            lines.add(Component.translatable("tooltip.echelon-core.sanctuary.title")
                    .withStyle(unlocked ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            for (int line = 1; line <= 3; line++)
                lines.add(Component.translatable("tooltip.echelon-core.sanctuary.body" + line)
                        .withStyle(unlocked ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
            if (!unlocked) lines.add(Component.translatable("tooltip.echelon-core.sanctuary.locked")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        });
    }

    /** Client-side guess from the worn Guardian of Life pieces; the server decides with the real stat. */
    private static boolean unlocked() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        for (EquipmentSetRegistry.EquipmentSet set : EquipmentSetRegistry.sets()) {
            if (!set.id().equals(EchelonCore.id("guardian_of_life"))) continue;
            int worn = EquipmentSetService.worn(player, set);
            return set.bonuses().stream().anyMatch(bonus -> worn >= bonus.pieces()
                    && bonus.stats().stream().anyMatch(stat -> stat.startsWith("unlockSanctuary")));
        }
        return false;
    }
}
