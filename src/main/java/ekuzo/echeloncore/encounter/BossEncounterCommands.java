package ekuzo.echeloncore.encounter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import ekuzo.crazyworldprogression.loot.LootPoolService;
import ekuzo.crazyworldprogression.worldboundary.restriction.Restriction;
import ekuzo.crazyworldprogression.worldboundary.restriction.RestrictionService;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.encounter.BossEncounterData.Boss;
import ekuzo.echeloncore.encounter.BossEncounterData.Encounter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * {@code /encounter}: game-master commands for boss encounters, the same settings as the admin panel's Boss
 * Encounters tab. An encounter's area is made with it ({@code /encounter create <id> sphere <radius>} around the
 * command's position) and changed with {@code /encounter shape}.
 */
public final class BossEncounterCommands {
    private BossEncounterCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("encounter")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(BossEncounterCommands::list)
                .then(Commands.literal("list").executes(BossEncounterCommands::list))
                .then(Commands.literal("info").then(encounterArgument().executes(BossEncounterCommands::info)))
                .then(Commands.literal("create").then(shapes(Commands.argument("id", StringArgumentType.word()),
                        BossEncounterCommands::create)))
                .then(Commands.literal("shape").then(shapes(encounterArgument(), BossEncounterCommands::reshape)))
                .then(Commands.literal("move").then(encounterArgument().then(Commands.literal("here")
                        .executes(BossEncounterCommands::moveHere))))
                .then(Commands.literal("name").then(encounterArgument()
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(BossEncounterCommands::rename))))
                .then(Commands.literal("delete").then(encounterArgument().executes(BossEncounterCommands::delete)))
                .then(Commands.literal("boss")
                        .then(Commands.literal("add").then(encounterArgument()
                                .then(Commands.argument("type", IdentifierArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                                BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                        .executes(BossEncounterCommands::addBoss))))
                        .then(Commands.literal("remove").then(encounterArgument()
                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(BossEncounterCommands::removeBoss)))))
                .then(Commands.literal("loot")
                        .then(Commands.literal("add").then(encounterArgument().then(poolArgument()
                                .executes(context -> loot(context, true)))))
                        .then(Commands.literal("remove").then(encounterArgument().then(poolArgument()
                                .executes(context -> loot(context, false))))))
                .then(Commands.literal("aggro").then(encounterArgument()
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.0, 512.0))
                                .executes(BossEncounterCommands::setAggro))))
                .then(Commands.literal("respawn").then(encounterArgument()
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 1_000_000))
                                .executes(BossEncounterCommands::setRespawn))))
                .then(Commands.literal("reset").then(encounterArgument().executes(BossEncounterCommands::reset))));
    }

    /** The shape branches ({@code sphere <radius>}, {@code cylinder <radius> <height>}, {@code cube|pyramid <w> <h> <d>}). */
    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> shapes(
            RequiredArgumentBuilder<CommandSourceStack, T> parent, ShapeCommand command) {
        return parent
                .then(Commands.literal("sphere").then(size("radius").executes(context ->
                        command.run(context, Restriction.Shape.SPHERE, get(context, "radius"), get(context, "radius"), get(context, "radius")))))
                .then(Commands.literal("cylinder").then(size("radius").then(size("height").executes(context ->
                        command.run(context, Restriction.Shape.CYLINDER, get(context, "radius"), get(context, "height"), get(context, "radius"))))))
                .then(shape3("cube", Restriction.Shape.CUBE, command))
                .then(shape3("pyramid", Restriction.Shape.PYRAMID, command));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shape3(String name, Restriction.Shape shape, ShapeCommand command) {
        return Commands.literal(name).then(size("width").then(size("height").then(size("depth").executes(context ->
                command.run(context, shape, get(context, "width"), get(context, "height"), get(context, "depth"))))));
    }

    private interface ShapeCommand {
        int run(CommandContext<CommandSourceStack> context, Restriction.Shape shape, double x, double y, double z);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> size(String name) {
        return Commands.argument(name, DoubleArgumentType.doubleArg(0.5, 4096.0));
    }

    private static double get(CommandContext<CommandSourceStack> context, String name) {
        return DoubleArgumentType.getDouble(context, name);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> encounterArgument() {
        return Commands.argument("encounter", IdentifierArgument.id()).suggests((context, builder) ->
                SharedSuggestionProvider.suggest(BossEncounters.list(context.getSource().getServer()).stream()
                        .map(encounter -> encounter.id.toString()), builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> poolArgument() {
        return Commands.argument("pool", IdentifierArgument.id()).suggests((context, builder) ->
                SharedSuggestionProvider.suggest(LootPoolService.all(context.getSource().getServer()).keySet(), builder));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var encounters = BossEncounters.list(context.getSource().getServer());
        reply(context, Component.translatable("commands.echelon-core.encounter.list", encounters.size()));
        for (Encounter encounter : encounters)
            reply(context, Component.literal(" - " + encounter.id + " (" + encounter.displayName() + ") · "
                    + encounter.state.serializedName() + " · " + encounter.bosses.size() + " boss(es)"));
        return encounters.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        MinecraftServer server = context.getSource().getServer();
        Restriction area = RestrictionService.get(server, BossEncounters.areaId(encounter.id));
        reply(context, Component.translatable("commands.echelon-core.encounter.info", encounter.displayName(),
                encounter.state.serializedName(), area == null ? "-" : describe(area),
                encounter.lootPools.isEmpty() ? "-" : String.join(", ", encounter.lootPools.stream().map(Identifier::toString).toList()),
                format(encounter.aggroRadius), encounter.respawnSeconds,
                BossEncounters.present(encounter.id), BossEncounters.locked(encounter.id)));
        for (int i = 0; i < encounter.bosses.size(); i++) {
            Boss boss = encounter.bosses.get(i);
            reply(context, Component.literal(" " + (i + 1) + ". " + boss.type() + " at " + format(boss.x()) + " "
                    + format(boss.y()) + " " + format(boss.z())));
        }
        return 1;
    }

    /** Creates the encounter with its area centered on the command's position. */
    private static int create(CommandContext<CommandSourceStack> context, Restriction.Shape shape, double x, double y, double z) {
        MinecraftServer server = context.getSource().getServer();
        String raw = StringArgumentType.getString(context, "id");
        Identifier id = parseId(raw);
        if (BossEncounters.get(server, id) != null) {
            context.getSource().sendFailure(Component.translatable("commands.echelon-core.encounter.exists", id.toString()));
            return 0;
        }
        BossEncounters.create(server, id, raw, context.getSource().getLevel().dimension().identifier(), shape,
                context.getSource().getPosition(), x, y, z);
        reply(context, Component.translatable("commands.echelon-core.encounter.created", id.toString(), shape.serializedName()));
        return 1;
    }

    /** New shape and size, keeping the area's center. */
    private static int reshape(CommandContext<CommandSourceStack> context, Restriction.Shape shape, double x, double y, double z) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        MinecraftServer server = context.getSource().getServer();
        Restriction area = RestrictionService.get(server, BossEncounters.areaId(encounter.id));
        Vec3 center = area == null ? context.getSource().getPosition() : new Vec3(area.centerX(), area.centerY(), area.centerZ());
        Identifier dimension = area == null ? context.getSource().getLevel().dimension().identifier() : area.dimension();
        BossEncounters.setArea(server, encounter, dimension, shape, center, x, y, z);
        reply(context, Component.translatable("commands.echelon-core.encounter.area_set", encounter.id.toString(),
                shape.serializedName()));
        return 1;
    }

    private static int moveHere(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        MinecraftServer server = context.getSource().getServer();
        Restriction area = RestrictionService.get(server, BossEncounters.areaId(encounter.id));
        if (area == null) return 0;
        BossEncounters.setArea(server, encounter, context.getSource().getLevel().dimension().identifier(), area.shape(),
                context.getSource().getPosition(), area.sizeX(), area.sizeY(), area.sizeZ());
        reply(context, Component.translatable("commands.echelon-core.encounter.moved", encounter.id.toString()));
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        BossEncounters.setName(context.getSource().getServer(), encounter, StringArgumentType.getString(context, "name"));
        reply(context, Component.translatable("commands.echelon-core.encounter.renamed", encounter.id.toString(),
                encounter.displayName()));
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        BossEncounters.delete(context.getSource().getServer(), encounter.id);
        reply(context, Component.translatable("commands.echelon-core.encounter.deleted", encounter.id.toString()));
        return 1;
    }

    /** Adds a boss standing where (and facing where) the command's user stands. */
    private static int addBoss(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        Identifier type = IdentifierArgument.getId(context, "type");
        if (BuiltInRegistries.ENTITY_TYPE.getOptional(type).isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.echelon-core.encounter.unknown_type", type.toString()));
            return 0;
        }
        Vec3 position = context.getSource().getPosition();
        float yaw = context.getSource().getEntity() != null ? context.getSource().getEntity().getYRot() : 0.0F;
        Restriction area = RestrictionService.get(context.getSource().getServer(), BossEncounters.areaId(encounter.id));
        if (area != null && !area.contains(position, 0.0, 0.0))
            context.getSource().sendFailure(Component.translatable("commands.echelon-core.encounter.boss_outside"));
        BossEncounters.addBoss(context.getSource().getServer(), encounter, new Boss(type, position.x, position.y, position.z, yaw));
        reply(context, Component.translatable("commands.echelon-core.encounter.boss_added", type.toString(),
                encounter.bosses.size(), encounter.id.toString()));
        return 1;
    }

    private static int removeBoss(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        int index = IntegerArgumentType.getInteger(context, "index");
        if (!BossEncounters.removeBoss(context.getSource().getServer(), encounter, index - 1)) {
            context.getSource().sendFailure(Component.translatable("commands.echelon-core.encounter.no_boss", index));
            return 0;
        }
        reply(context, Component.translatable("commands.echelon-core.encounter.boss_removed", index, encounter.id.toString()));
        return 1;
    }

    private static int loot(CommandContext<CommandSourceStack> context, boolean add) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        MinecraftServer server = context.getSource().getServer();
        Identifier pool = IdentifierArgument.getId(context, "pool");
        if (add && LootPoolService.get(server, pool) == null) {
            context.getSource().sendFailure(Component.translatable("commands.echelon-core.encounter.unknown_pool", pool.toString()));
            return 0;
        }
        BiConsumer<String, Object[]> say = (key, args) -> reply(context, Component.translatable(key, args));
        if (add ? BossEncounters.addLootPool(server, encounter, pool) : BossEncounters.removeLootPool(server, encounter, pool))
            say.accept(add ? "commands.echelon-core.encounter.loot_added" : "commands.echelon-core.encounter.loot_removed",
                    new Object[]{pool.toString(), encounter.id.toString()});
        return 1;
    }

    private static int setAggro(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        double radius = DoubleArgumentType.getDouble(context, "radius");
        BossEncounters.setAggroRadius(context.getSource().getServer(), encounter, radius);
        reply(context, Component.translatable("commands.echelon-core.encounter.aggro_set", encounter.id.toString(), format(radius)));
        return 1;
    }

    private static int setRespawn(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        BossEncounters.setRespawn(context.getSource().getServer(), encounter, seconds);
        reply(context, Component.translatable("commands.echelon-core.encounter.respawn_set", encounter.id.toString(), seconds));
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        Encounter encounter = require(context);
        if (encounter == null) return 0;
        BossEncounters.forceReset(context.getSource().getServer(), encounter);
        reply(context, Component.translatable("commands.echelon-core.encounter.reset", encounter.id.toString()));
        return 1;
    }

    private static Encounter require(CommandContext<CommandSourceStack> context) {
        Identifier id = IdentifierArgument.getId(context, "encounter");
        Encounter encounter = BossEncounters.get(context.getSource().getServer(), id);
        if (encounter == null) context.getSource().sendFailure(Component.translatable("commands.echelon-core.encounter.unknown", id.toString()));
        return encounter;
    }

    // Accept convenient local IDs while preserving complete cross-namespace identifiers (like the anomaly commands).
    static Identifier parseId(String value) {
        Identifier id = value.contains(":") ? Identifier.tryParse(value) : EchelonCore.id(value.toLowerCase(Locale.ROOT));
        if (id == null) throw new IllegalArgumentException("Invalid ID: " + value);
        return id;
    }

    static String describe(Restriction area) {
        String size = switch (area.shape()) {
            case SPHERE -> "radius " + format(area.sizeX());
            case CYLINDER -> "radius " + format(area.sizeX()) + ", height " + format(area.sizeY());
            default -> format(area.sizeX()) + " x " + format(area.sizeY()) + " x " + format(area.sizeZ());
        };
        return area.shape().serializedName() + " (" + size + ") at " + format(area.centerX()) + " " + format(area.centerY())
                + " " + format(area.centerZ()) + " in " + area.dimension();
    }

    private static void reply(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
