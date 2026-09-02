package ekuzo.echeloncore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.veil.VeilManager;
import ekuzo.echeloncore.veil.anomaly.VeilAnomaly;
import ekuzo.echeloncore.veil.anomaly.VeilAnomalyService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.Collision.SOLID;
import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.BoundaryMode.KEEP_OUT;
import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.BlockInteraction.AUTOMATIC;
import static ekuzo.echeloncore.veil.anomaly.VeilAnomaly.Visibility.OPAQUE;

/** Operator placement and state editing commands for persistent Veil anomalies. */
public final class VeilCommands {
    private static final List<String> SHAPES = List.of("sphere", "cylinder", "cube", "pyramid");
    private static final List<String> COLLISIONS = List.of("solid", "passable");
    private static final List<String> VISIBILITIES = List.of("opaque", "translucent", "hidden");
    private static final List<String> BOUNDARY_MODES = List.of("keep_out", "keep_in");
    private static final List<String> BLOCK_INTERACTIONS = List.of("automatic", "protected", "allowed");

    // Prevent instantiation of the Veil command-tree owner.
    private VeilCommands() {
    }

    // Register Veil Level inspection and the game-master-only anomaly editor.
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("veil")
                .executes(VeilCommands::status)
                .then(Commands.literal("anomaly")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(VeilCommands::list)
                        .then(Commands.literal("list").executes(VeilCommands::list))
                        .then(Commands.literal("info").then(idArgument().executes(VeilCommands::info)))
                        .then(createBranch())
                        .then(Commands.literal("remove").then(idArgument().executes(VeilCommands::remove)))
                        .then(Commands.literal("move").then(idArgument()
                                .then(Commands.literal("here").executes(VeilCommands::moveHere))))
                        .then(Commands.literal("mode").then(idArgument()
                                .then(Commands.argument("boundary_mode", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                BOUNDARY_MODES, builder))
                                        .executes(VeilCommands::setBoundaryMode))))
                        .then(Commands.literal("state")
                                .then(Commands.literal("set").then(idArgument()
                                        .then(Commands.argument("minimum_level", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("collision", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(COLLISIONS, builder))
                                                        .then(Commands.argument("visibility", StringArgumentType.word())
                                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(VISIBILITIES, builder))
                                                                .executes(context -> setState(context, AUTOMATIC))
                                                                .then(Commands.argument("block_interaction", StringArgumentType.word())
                                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                                BLOCK_INTERACTIONS, builder))
                                                                        .executes(VeilCommands::setStateWithBlockInteraction)))))))
                                .then(Commands.literal("remove").then(idArgument()
                                        .then(Commands.argument("minimum_level", IntegerArgumentType.integer(1))
                                                .executes(VeilCommands::removeState)))))));
    }

    // Build shape-specific create syntax while sharing identifier and center handling.
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createBranch() {
        return Commands.literal("create").then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.literal("sphere")
                        .then(positiveDouble("radius").executes(context -> create(context, VeilAnomaly.Shape.SPHERE))))
                .then(Commands.literal("cylinder").then(positiveDouble("radius")
                        .then(positiveDouble("height").executes(context -> create(context, VeilAnomaly.Shape.CYLINDER)))))
                .then(Commands.literal("cube").then(positiveDouble("width")
                        .then(positiveDouble("height").then(positiveDouble("depth")
                                .executes(context -> create(context, VeilAnomaly.Shape.CUBE))))))
                .then(Commands.literal("pyramid").then(positiveDouble("width")
                        .then(positiveDouble("height").then(positiveDouble("depth")
                                .executes(context -> create(context, VeilAnomaly.Shape.PYRAMID)))))));
    }

    // Create a positive finite dimension argument shared by every geometry branch.
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Double> positiveDouble(String name) {
        return Commands.argument(name, DoubleArgumentType.doubleArg(0.1, 4096.0));
    }

    // Create an anomaly-ID argument with suggestions from persistent world data unavailable until execution time.
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        VeilAnomalyService.anomalies(context.getSource().getServer()).stream()
                                .map(anomaly -> anomaly.id().toString()).toList(), builder));
    }

    // Show current Veil Level and anomaly collision-index totals.
    private static int status(CommandContext<CommandSourceStack> context) {
        int level = VeilManager.getVeilLevel(context.getSource().getServer());
        int total = VeilAnomalyService.anomalies(context.getSource().getServer()).size();
        int solid = VeilAnomalyService.solidAnomalyCount(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("Veil Level " + level + ": " + total
                + " anomaly/anomalies, " + solid + " in the solid collision index.").withStyle(ChatFormatting.DARK_PURPLE), false);
        return 1;
    }

    // List all placed anomalies with resolved state at the current Veil Level.
    private static int list(CommandContext<CommandSourceStack> context) {
        List<VeilAnomaly> anomalies = VeilAnomalyService.anomalies(context.getSource().getServer());
        int level = VeilManager.getVeilLevel(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("Veil anomalies (" + anomalies.size() + ")")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        anomalies.forEach(anomaly -> {
            VeilAnomaly.AnomalyState state = anomaly.stateAt(level);
            context.getSource().sendSuccess(() -> Component.literal(anomaly.id() + " - "
                    + anomaly.shape().serializedName() + " in " + anomaly.dimension() + " ["
                    + anomaly.boundaryMode().serializedName() + ", " + state.collision().serializedName() + ", "
                    + state.visibility().serializedName() + ", " + describeBlockInteraction(state) + "]")
                    .withStyle(ChatFormatting.GRAY), false);
        });
        return anomalies.size();
    }

    // Display geometry and every configured level transition for one anomaly.
    private static int info(CommandContext<CommandSourceStack> context) {
        VeilAnomaly anomaly = require(context);
        if (anomaly == null) return 0;
        context.getSource().sendSuccess(() -> Component.literal("Veil anomaly " + anomaly.id())
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        context.getSource().sendSuccess(() -> Component.literal("Shape: " + anomaly.shape().serializedName()
                + ", center: " + format(anomaly.centerX()) + " " + format(anomaly.centerY()) + " "
                + format(anomaly.centerZ()) + ", dimension: " + anomaly.dimension()).withStyle(ChatFormatting.GRAY), false);
        context.getSource().sendSuccess(() -> Component.literal("Size: " + format(anomaly.sizeX()) + " "
                + format(anomaly.sizeY()) + " " + format(anomaly.sizeZ())).withStyle(ChatFormatting.GRAY), false);
        context.getSource().sendSuccess(() -> Component.literal("Boundary mode: "
                + anomaly.boundaryMode().serializedName()).withStyle(ChatFormatting.GRAY), false);
        anomaly.states().forEach(state -> context.getSource().sendSuccess(() -> Component.literal("Level "
                + state.minimumVeilLevel() + "+: " + state.collision().serializedName() + ", "
                + state.visibility().serializedName() + ", blocks " + describeBlockInteraction(state))
                .withStyle(ChatFormatting.GRAY), false));
        return 1;
    }

    // Create one anomaly centered at the command source with a solid opaque level-zero state.
    private static int create(CommandContext<CommandSourceStack> context, VeilAnomaly.Shape shape) {
        Identifier id = parseId(StringArgumentType.getString(context, "id"));
        Vec3 center = context.getSource().getPosition();
        double sizeX;
        double sizeY;
        double sizeZ;
        switch (shape) {
            case SPHERE -> sizeX = sizeY = sizeZ = DoubleArgumentType.getDouble(context, "radius");
            case CYLINDER -> {
                sizeX = sizeZ = DoubleArgumentType.getDouble(context, "radius");
                sizeY = DoubleArgumentType.getDouble(context, "height");
            }
            case CUBE, PYRAMID -> {
                sizeX = DoubleArgumentType.getDouble(context, "width");
                sizeY = DoubleArgumentType.getDouble(context, "height");
                sizeZ = DoubleArgumentType.getDouble(context, "depth");
            }
            default -> throw new IllegalStateException("Unhandled anomaly shape " + shape);
        }
        try {
            VeilAnomaly anomaly = new VeilAnomaly(id, context.getSource().getLevel().dimension().identifier(), shape,
                    center.x, center.y, center.z, sizeX, sizeY, sizeZ,
                    KEEP_OUT,
                    List.of(new VeilAnomaly.AnomalyState(0, SOLID, OPAQUE, AUTOMATIC)));
            VeilAnomalyService.create(context.getSource().getServer(), anomaly);
            context.getSource().sendSuccess(() -> Component.literal("Created " + shape.serializedName()
                    + " Veil anomaly " + id + " at the command position.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    // Change whether the solid boundary rejects entry or rejects escape.
    private static int setBoundaryMode(CommandContext<CommandSourceStack> context) {
        VeilAnomaly anomaly = require(context);
        if (anomaly == null) return 0;
        try {
            VeilAnomaly.BoundaryMode mode = VeilAnomaly.BoundaryMode.parse(
                    StringArgumentType.getString(context, "boundary_mode"));
            VeilAnomalyService.update(context.getSource().getServer(), anomaly.withBoundaryMode(mode));
            context.getSource().sendSuccess(() -> Component.literal("Set " + anomaly.id()
                    + " boundary mode to " + mode.serializedName()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Invalid boundary mode: " + exception.getMessage()));
            return 0;
        }
    }

    // Remove one persistent anomaly and its runtime/client representations.
    private static int remove(CommandContext<CommandSourceStack> context) {
        Identifier id = parseId(StringArgumentType.getString(context, "id"));
        if (!VeilAnomalyService.remove(context.getSource().getServer(), id)) {
            context.getSource().sendFailure(Component.literal("Unknown anomaly: " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Removed Veil anomaly " + id)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // Move one existing anomaly to the command source and its current dimension.
    private static int moveHere(CommandContext<CommandSourceStack> context) {
        VeilAnomaly anomaly = require(context);
        if (anomaly == null) return 0;
        VeilAnomaly moved = anomaly.movedTo(context.getSource().getLevel().dimension().identifier(),
                context.getSource().getPosition());
        try {
            VeilAnomalyService.update(context.getSource().getServer(), moved);
            context.getSource().sendSuccess(() -> Component.literal("Moved Veil anomaly " + anomaly.id())
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    // Parse an explicit block-interaction argument before inserting one complete state threshold.
    private static int setStateWithBlockInteraction(CommandContext<CommandSourceStack> context) {
        try {
            return setState(context, VeilAnomaly.BlockInteraction.parse(
                    StringArgumentType.getString(context, "block_interaction")));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Invalid block interaction: "
                    + exception.getMessage()));
            return 0;
        }
    }

    // Insert or replace one Veil-Level state threshold and immediately rebuild resolved indexes.
    private static int setState(CommandContext<CommandSourceStack> context,
                                VeilAnomaly.BlockInteraction blockInteraction) {
        VeilAnomaly anomaly = require(context);
        if (anomaly == null) return 0;
        try {
            int level = IntegerArgumentType.getInteger(context, "minimum_level");
            VeilAnomaly.Collision collision = VeilAnomaly.Collision.parse(
                    StringArgumentType.getString(context, "collision"));
            VeilAnomaly.Visibility visibility = VeilAnomaly.Visibility.parse(
                    StringArgumentType.getString(context, "visibility"));
            VeilAnomalyService.update(context.getSource().getServer(),
                    anomaly.withState(new VeilAnomaly.AnomalyState(level, collision, visibility, blockInteraction)));
            context.getSource().sendSuccess(() -> Component.literal("Set " + anomaly.id() + " at Veil Level "
                    + level + "+ to " + collision.serializedName() + ", " + visibility.serializedName()
                    + ", blocks " + blockInteraction.serializedName())
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Invalid anomaly state: " + exception.getMessage()));
            return 0;
        }
    }

    // Describe automatic interaction behavior together with its effective resolved value.
    private static String describeBlockInteraction(VeilAnomaly.AnomalyState state) {
        if (state.blockInteraction() != AUTOMATIC) return state.blockInteraction().serializedName();
        return "automatic (" + (state.protectsBlocks() ? "protected" : "allowed") + ")";
    }

    // Remove one nonzero state threshold and immediately restore inherited lower-level behavior.
    private static int removeState(CommandContext<CommandSourceStack> context) {
        VeilAnomaly anomaly = require(context);
        if (anomaly == null) return 0;
        int level = IntegerArgumentType.getInteger(context, "minimum_level");
        try {
            VeilAnomalyService.update(context.getSource().getServer(), anomaly.withoutState(level));
            context.getSource().sendSuccess(() -> Component.literal("Removed the Veil Level " + level
                    + " state from " + anomaly.id()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    // Resolve the command ID or send an unknown-anomaly error.
    private static VeilAnomaly require(CommandContext<CommandSourceStack> context) {
        Identifier id = parseId(StringArgumentType.getString(context, "id"));
        VeilAnomaly anomaly = VeilAnomalyService.get(context.getSource().getServer(), id);
        if (anomaly == null) context.getSource().sendFailure(Component.literal("Unknown anomaly: " + id));
        return anomaly;
    }

    // Accept convenient local IDs while preserving complete cross-namespace identifiers.
    private static Identifier parseId(String value) {
        Identifier id = value.contains(":") ? Identifier.tryParse(value) : EchelonCore.id(value);
        if (id == null) throw new IllegalArgumentException("Invalid anomaly ID: " + value);
        return id;
    }

    // Format command coordinates and dimensions without unnecessary trailing precision.
    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
