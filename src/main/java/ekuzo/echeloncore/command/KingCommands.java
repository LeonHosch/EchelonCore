package ekuzo.echeloncore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import ekuzo.crazyworldprogression.worldboundary.WorldLeaderService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public final class KingCommands {
    // Prevent instantiation of this command utility.
    private KingCommands() { }

    // Register king inspection plus operator-only appointment and clearing branches.
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("king").executes(KingCommands::showKing)
                .then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(KingCommands::setKing)))
                .then(Commands.literal("clear").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(KingCommands::clearKing)));
    }

    // Display the current king, resolving an online name when possible.
    private static int showKing(CommandContext<CommandSourceStack> context) {
        Optional<UUID> king = WorldLeaderService.getLeader(context.getSource().getServer());
        context.getSource().sendSuccess(() -> king
                .map(uuid -> Component.literal("The current king is " + displayName(context.getSource(), uuid) + ".")
                        .withStyle(ChatFormatting.GOLD))
                .orElseGet(() -> Component.literal("There is currently no king.").withStyle(ChatFormatting.YELLOW)), false);
        return king.isPresent() ? 1 : 0;
    }

    // Require exactly one selected profile and persist it as the new king.
    private static int setKing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            context.getSource().sendFailure(Component.literal("Exactly one player must be selected."));
            return 0;
        }
        NameAndId player = profiles.iterator().next();
        WorldLeaderService.setLeader(context.getSource().getServer(), player.id());
        context.getSource().sendSuccess(() -> Component.literal(player.name() + " is now the king.")
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    // Remove the current appointment without changing any progression unlocks or balances.
    private static int clearKing(CommandContext<CommandSourceStack> context) {
        WorldLeaderService.clearLeader(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("The current king was cleared.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // Prefer an online player's current name and fall back to the persisted UUID for offline kings.
    private static String displayName(CommandSourceStack source, UUID playerUuid) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(playerUuid);
        return player == null ? playerUuid.toString() : player.getName().getString();
    }
}
