package ekuzo.echeloncore.encounter;

import ekuzo.crazyworldprogression.admin.AdminPanelRegistry;
import ekuzo.crazyworldprogression.loot.LootPoolService;
import ekuzo.crazyworldprogression.worldboundary.restriction.Restriction;
import ekuzo.crazyworldprogression.worldboundary.restriction.RestrictionService;
import ekuzo.echeloncore.encounter.BossEncounterData.Boss;
import ekuzo.echeloncore.encounter.BossEncounterData.Encounter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ekuzo.crazyworldprogression.admin.AdminPanelRegistry.Tone.DANGER;
import static ekuzo.crazyworldprogression.admin.AdminPanelRegistry.Tone.GOOD;
import static ekuzo.crazyworldprogression.admin.AdminPanelRegistry.Tone.NORMAL;

/**
 * The Boss Encounters tab of Echelon's admin panel: create and manage every encounter (the same settings as
 * {@code /encounter}). Each encounter's area is set here directly; the anomaly behind it is managed for you.
 */
public final class BossEncounterAdmin {
    public static final String PAGE = "encounters";
    private static final String NEW = PAGE + "/new";
    private static final String EDIT = PAGE + "/edit/";
    private static final List<String> SHAPES = List.of("sphere", "cylinder", "cube", "pyramid");

    private BossEncounterAdmin() { }

    public static boolean owns(String pageId) { return pageId.equals(PAGE) || pageId.equals(NEW) || pageId.startsWith(EDIT); }

    public static AdminPanelRegistry.AdminPage page(ServerPlayer player, String pageId, List<AdminPanelRegistry.AdminTab> tabs) {
        if (pageId.equals(NEW)) return creator(player, tabs);
        if (pageId.startsWith(EDIT)) return editor(player, Identifier.parse(pageId.substring(EDIT.length())), tabs);
        MinecraftServer server = player.level().getServer();
        List<AdminPanelRegistry.AdminEntry> entries = new ArrayList<>();
        entries.add(AdminPanelRegistry.AdminEntry.editor("Create", "New boss encounter",
                "Set its area, bosses and loot.", List.of(),
                List.of(AdminPanelRegistry.AdminAction.navigate("Create", NEW))));
        for (Encounter encounter : BossEncounters.list(server)) {
            Restriction area = RestrictionService.get(server, BossEncounters.areaId(encounter.id));
            entries.add(AdminPanelRegistry.AdminEntry.editor("Encounters", encounter.displayName(),
                    encounter.id + " · " + encounter.state.serializedName() + " · " + encounter.bosses.size() + " boss(es) · "
                            + (area == null ? "no area!" : BossEncounterCommands.describe(area)), List.of(),
                    List.of(AdminPanelRegistry.AdminAction.navigate("Edit", EDIT + encounter.id))));
        }
        return new AdminPanelRegistry.AdminPage(PAGE, "Boss Encounters", BossEncounters.list(server).size() + " encounter(s)",
                tabs, entries, AdminPanelRegistry.PageLayout.EDITOR);
    }

    private static AdminPanelRegistry.AdminPage creator(ServerPlayer player, List<AdminPanelRegistry.AdminTab> tabs) {
        List<AdminPanelRegistry.AdminEntry> rows = new ArrayList<>();
        rows.add(AdminPanelRegistry.AdminEntry.editor("Identity", "New boss encounter", "The ID cannot change later; the name is shown to players.",
                List.of(text("encounter_id", "ID", "new_encounter", 64), text("name", "Display name", "", 64)), List.of()));
        rows.addAll(areaRows(player.level().dimension().identifier().toString(), "sphere", player.position(), 24, 16, 24, false));
        rows.add(AdminPanelRegistry.AdminEntry.editor("Actions", "Create", "Bosses and loot pools are added on the next page.",
                List.of(), List.of(AdminPanelRegistry.AdminAction.wide("encounter.create", "Create encounter", false),
                        AdminPanelRegistry.AdminAction.navigate("Back", PAGE))));
        return new AdminPanelRegistry.AdminPage(NEW, "Create boss encounter", "Area centered on you by default", tabs, rows,
                AdminPanelRegistry.PageLayout.EDITOR);
    }

    /** Shape, size and center fields (sphere and cylinder use X as their radius). */
    private static List<AdminPanelRegistry.AdminEntry> areaRows(String dimension, String shape, Vec3 center,
                                                               double sizeX, double sizeY, double sizeZ, boolean here) {
        return List.of(
                AdminPanelRegistry.AdminEntry.editor("Area", "Shape and center", "Players inside this area take part in the fight.",
                        List.of(AdminPanelRegistry.AdminField.select("shape", "Shape", shape, SHAPES),
                                text("dimension", "World", dimension, 128),
                                text("center_x", "X", format(center.x), 32), text("center_y", "Y", format(center.y), 32),
                                text("center_z", "Z", format(center.z), 32)),
                        here ? List.of(new AdminPanelRegistry.AdminAction("encounter.here", "Use my position", false)) : List.of()),
                AdminPanelRegistry.AdminEntry.editor("Area", "Size", "Sphere: radius. Cylinder: radius and height. Cube and pyramid: width, height, depth.",
                        List.of(text("size_x", "Radius / width", format(sizeX), 16), text("size_y", "Height", format(sizeY), 16),
                                text("size_z", "Depth", format(sizeZ), 16)), List.of()));
    }

    private static AdminPanelRegistry.AdminPage editor(ServerPlayer player, Identifier id, List<AdminPanelRegistry.AdminTab> tabs) {
        MinecraftServer server = player.level().getServer();
        Encounter encounter = BossEncounters.get(server, id);
        if (encounter == null) throw new IllegalArgumentException("Unknown encounter: " + id);
        Restriction area = RestrictionService.get(server, BossEncounters.areaId(id));
        List<AdminPanelRegistry.AdminEntry> rows = new ArrayList<>();
        rows.add(AdminPanelRegistry.AdminEntry.editor("Encounter", encounter.state.serializedName(),
                BossEncounters.present(id) + " in the fight · " + BossEncounters.locked(id) + " locked out",
                List.of(AdminPanelRegistry.AdminField.readOnly("encounter_id", "ID", id.toString()),
                        text("name", "Display name", encounter.name, 64),
                        text("aggro", "Aggro radius (blocks from a boss)", format(encounter.aggroRadius), 10),
                        text("respawn", "Respawn after clear (seconds)", Integer.toString(encounter.respawnSeconds), 10)),
                List.of()));
        if (area != null) rows.addAll(areaRows(area.dimension().toString(), area.shape().serializedName(),
                new Vec3(area.centerX(), area.centerY(), area.centerZ()), area.sizeX(), area.sizeY(), area.sizeZ(), true));
        else rows.addAll(areaRows(player.level().dimension().identifier().toString(), "sphere", player.position(), 24, 16, 24, true));
        rows.add(AdminPanelRegistry.AdminEntry.editor("Encounter", "Save", "Saves the name, timers and area (changing the area resets a fight).",
                List.of(), List.of(AdminPanelRegistry.AdminAction.wide("encounter.save", "Save", false))));
        for (int i = 0; i < encounter.bosses.size(); i++) {
            Boss boss = encounter.bosses.get(i);
            rows.add(AdminPanelRegistry.AdminEntry.editor("Bosses", (i + 1) + ". " + boss.type(),
                    String.format(Locale.ROOT, "%.1f %.1f %.1f", boss.x(), boss.y(), boss.z()), List.of(),
                    List.of(new AdminPanelRegistry.AdminAction("encounter.boss.remove|" + i, "Remove", true))));
        }
        rows.add(AdminPanelRegistry.AdminEntry.editor("Bosses", "Add boss", "Spawns where you stand, facing your way.",
                List.of(new AdminPanelRegistry.AdminField("boss_type", "Entity type (type to search)", "echelon-core:hollowed_penitent",
                        "echelon-core:hollowed_penitent", 128, BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                        .map(Identifier::toString).sorted().toList())),
                List.of(new AdminPanelRegistry.AdminAction("encounter.boss.add", "Add at my position", false))));
        for (Identifier pool : encounter.lootPools) {
            boolean exists = LootPoolService.get(server, pool) != null;
            rows.add(new AdminPanelRegistry.AdminEntry(pool.toString(), exists ? "Rolled for every player inside when all bosses die"
                    : "This loot pool no longer exists", exists ? GOOD : DANGER, List.of(),
                    List.of(new AdminPanelRegistry.AdminAction("encounter.loot.remove|" + pool, "Remove", true)), "Loot pools", null));
        }
        List<String> pools = LootPoolService.all(server).keySet().stream().sorted().toList();
        rows.add(AdminPanelRegistry.AdminEntry.editor("Loot pools", "Attach loot pool", pools.isEmpty()
                        ? "No loot pools exist yet: make them in CWP's loot pool editor." : "Each attached pool is rolled once per player.",
                List.of(AdminPanelRegistry.AdminField.select("loot_pool", "Loot pool", pools.isEmpty() ? "" : pools.getFirst(), pools)),
                List.of(new AdminPanelRegistry.AdminAction("encounter.loot.add", "Attach", false))));
        rows.add(AdminPanelRegistry.AdminEntry.editor("Actions", "Reset", "Fresh bosses now, lockouts lifted (also ends a cleared wait).",
                List.of(), List.of(new AdminPanelRegistry.AdminAction("encounter.reset", "Reset", true),
                        AdminPanelRegistry.AdminAction.navigate("Back", PAGE))));
        rows.add(AdminPanelRegistry.AdminEntry.editor("Actions", "Delete", "Removes the encounter, its area and its bosses.",
                List.of(), List.of(new AdminPanelRegistry.AdminAction("encounter.delete", "Delete encounter", true))));
        return new AdminPanelRegistry.AdminPage(EDIT + id, "Edit boss encounter", encounter.displayName(), tabs, rows,
                AdminPanelRegistry.PageLayout.EDITOR);
    }

    public static AdminPanelRegistry.ActionResult action(ServerPlayer player, String pageId, String actionId,
                                                        Map<String, String> values) {
        MinecraftServer server = player.level().getServer();
        if (actionId.equals("encounter.create")) {
            String raw = values.getOrDefault("encounter_id", "").trim();
            if (raw.isEmpty()) return AdminPanelRegistry.ActionResult.failure("Enter an ID");
            Identifier id = BossEncounterCommands.parseId(raw);
            if (BossEncounters.get(server, id) != null) return AdminPanelRegistry.ActionResult.failure("Encounter " + id + " already exists");
            String name = values.getOrDefault("name", "").trim();
            BossEncounters.create(server, id, name.isEmpty() ? raw : name, Identifier.parse(values.get("dimension").trim()),
                    Restriction.Shape.parse(values.get("shape")), center(values), number(values, "size_x"),
                    number(values, "size_y"), sizeZ(values));
            return AdminPanelRegistry.ActionResult.success("Created encounter " + id, EDIT + id);
        }
        if (!pageId.startsWith(EDIT)) return AdminPanelRegistry.ActionResult.failure("Unknown encounter action: " + actionId);
        Identifier id = Identifier.parse(pageId.substring(EDIT.length()));
        Encounter encounter = BossEncounters.get(server, id);
        if (encounter == null) return AdminPanelRegistry.ActionResult.failure("Unknown encounter " + id);
        switch (actionId) {
            case "encounter.save", "encounter.here" -> {
                BossEncounters.setName(server, encounter, values.getOrDefault("name", encounter.name));
                double aggro = number(values, "aggro");
                int respawn = Integer.parseInt(values.getOrDefault("respawn", Integer.toString(encounter.respawnSeconds)).trim());
                if (aggro < 0 || respawn < 0) return AdminPanelRegistry.ActionResult.failure("Aggro radius and respawn time cannot be negative");
                BossEncounters.setAggroRadius(server, encounter, aggro);
                BossEncounters.setRespawn(server, encounter, respawn);
                boolean here = actionId.equals("encounter.here");
                BossEncounters.setArea(server, encounter, here ? player.level().dimension().identifier()
                                : Identifier.parse(values.get("dimension").trim()), Restriction.Shape.parse(values.get("shape")),
                        here ? player.position() : center(values), number(values, "size_x"), number(values, "size_y"), sizeZ(values));
                return AdminPanelRegistry.ActionResult.success(here ? "Moved the area to you" : "Saved encounter " + id);
            }
            case "encounter.boss.add" -> {
                Identifier type = Identifier.parse(values.getOrDefault("boss_type", "").trim());
                if (BuiltInRegistries.ENTITY_TYPE.getOptional(type).isEmpty())
                    return AdminPanelRegistry.ActionResult.failure("Unknown entity type " + type);
                BossEncounters.addBoss(server, encounter, new Boss(type, player.getX(), player.getY(), player.getZ(), player.getYRot()));
                return AdminPanelRegistry.ActionResult.success("Added " + type + " as boss " + encounter.bosses.size());
            }
            case "encounter.loot.add" -> {
                String raw = values.getOrDefault("loot_pool", "").trim();
                if (raw.isEmpty()) return AdminPanelRegistry.ActionResult.failure("Pick a loot pool");
                Identifier pool = Identifier.parse(raw);
                if (LootPoolService.get(server, pool) == null) return AdminPanelRegistry.ActionResult.failure("Unknown loot pool " + pool);
                return BossEncounters.addLootPool(server, encounter, pool)
                        ? AdminPanelRegistry.ActionResult.success("Attached loot pool " + pool)
                        : AdminPanelRegistry.ActionResult.failure(pool + " is already attached");
            }
            case "encounter.reset" -> {
                BossEncounters.forceReset(server, encounter);
                return AdminPanelRegistry.ActionResult.success("Reset encounter " + id);
            }
            case "encounter.delete" -> {
                BossEncounters.delete(server, id);
                return AdminPanelRegistry.ActionResult.success("Deleted encounter " + id, PAGE);
            }
            default -> {
                if (actionId.startsWith("encounter.boss.remove|")) {
                    int index = Integer.parseInt(actionId.substring(actionId.indexOf('|') + 1));
                    return BossEncounters.removeBoss(server, encounter, index)
                            ? AdminPanelRegistry.ActionResult.success("Removed boss " + (index + 1))
                            : AdminPanelRegistry.ActionResult.failure("No such boss");
                }
                if (actionId.startsWith("encounter.loot.remove|")) {
                    Identifier pool = Identifier.parse(actionId.substring(actionId.indexOf('|') + 1));
                    BossEncounters.removeLootPool(server, encounter, pool);
                    return AdminPanelRegistry.ActionResult.success("Removed loot pool " + pool);
                }
                return AdminPanelRegistry.ActionResult.failure("Unknown encounter action: " + actionId);
            }
        }
    }

    private static Vec3 center(Map<String, String> values) {
        return new Vec3(number(values, "center_x"), number(values, "center_y"), number(values, "center_z"));
    }

    /** Sphere and cylinder are round: their depth is their radius. */
    private static double sizeZ(Map<String, String> values) {
        String shape = values.getOrDefault("shape", "sphere");
        return shape.equals("sphere") || shape.equals("cylinder") ? number(values, "size_x") : number(values, "size_z");
    }

    private static double number(Map<String, String> values, String key) {
        double value = Double.parseDouble(values.getOrDefault(key, "").trim());
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Not a number: " + key);
        return value;
    }

    private static AdminPanelRegistry.AdminField text(String id, String label, String value, int length) {
        return new AdminPanelRegistry.AdminField(id, label, "", value, length, List.of());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
