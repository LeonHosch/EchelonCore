package ekuzo.echeloncore.admin;

import ekuzo.crazyworldprogression.admin.AdminCategories;
import ekuzo.crazyworldprogression.admin.AdminPanelRegistry;
import ekuzo.crazyworldprogression.currency.CurrencyRegistry;
import ekuzo.crazyworldprogression.currency.CurrencyService;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.crazyworldprogression.worldboundary.WorldLeaderService;
import ekuzo.crazyworldprogression.worldboundary.AdvancedWorldBoundary;
import ekuzo.crazyworldprogression.worldboundary.restriction.Restriction;
import ekuzo.crazyworldprogression.worldboundary.restriction.RestrictionService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import static ekuzo.crazyworldprogression.admin.AdminPanelRegistry.Tone.DANGER;
import static ekuzo.crazyworldprogression.admin.AdminPanelRegistry.Tone.GOOD;
import static ekuzo.crazyworldprogression.admin.AdminPanelRegistry.Tone.NORMAL;

/** Echelon-owned pages contributed to CWP's generic administration dashboard. */
public final class EchelonAdminModule {
    private static final String OVERVIEW = "overview";
    private static final String KINGDOM = "kingdom";
    private static final String VEIL = "veil";
    private static final String ANOMALIES = "anomalies";
    private static final String ANOMALY_NEW = ANOMALIES + "/new";
    private static final String ANOMALY_EDIT = ANOMALIES + "/edit/";
    private static final String ADVANCEMENTS = "advancements";
    private static final List<AdminPanelRegistry.AdminTab> TABS = List.of(
            new AdminPanelRegistry.AdminTab(KINGDOM, "Kingdom", AdminPanelRegistry.MODERATE),
            new AdminPanelRegistry.AdminTab(ANOMALIES, "Anomalies", AdminPanelRegistry.BUILD),
            new AdminPanelRegistry.AdminTab(ekuzo.echeloncore.encounter.BossEncounterAdmin.PAGE, "Boss Encounters", AdminPanelRegistry.BUILD),
            new AdminPanelRegistry.AdminTab(ADVANCEMENTS, "Advancements", AdminPanelRegistry.LOGS),
            new AdminPanelRegistry.AdminTab(OVERVIEW, "Echelon status", AdminPanelRegistry.LOGS));

    // Prevent construction of the Echelon module registrar.
    private EchelonAdminModule() {
    }

    // Add one EchelonCore button and its server-owned pages to CWP's dashboard.
    public static void register() {
        AdminPanelRegistry.register(new AdminPanelRegistry.AdminModule(
                EchelonCore.id("admin"), "EchelonCore",
                "Kingdom leadership, Veil state, anomalies and Echelon progression.",
                "minecraft:golden_helmet", OVERVIEW, EchelonAdminModule::page, EchelonAdminModule::action, TABS));
    }

    /** The same entry filed under a section heading. */
    private static AdminPanelRegistry.AdminEntry section(String section, AdminPanelRegistry.AdminEntry entry) {
        return new AdminPanelRegistry.AdminEntry(entry.title(), entry.detail(), entry.tone(), entry.fields(),
                entry.actions(), section, entry.preview());
    }

    // Route a requested Echelon tab to freshly calculated server state.
    private static AdminPanelRegistry.AdminPage page(ServerPlayer player, String pageId) {
        if (AdminCategories.owns(pageId)) return AdminCategories.page(player, pageId, TABS);
        if (ekuzo.echeloncore.encounter.BossEncounterAdmin.owns(pageId)) return ekuzo.echeloncore.encounter.BossEncounterAdmin.page(player, pageId, TABS);
        if (pageId.equals(ANOMALY_NEW)) return newAnomalyPage(player);
        if (pageId.startsWith(ANOMALY_EDIT)) {
            return anomalyEditorPage(player, Identifier.parse(pageId.substring(ANOMALY_EDIT.length())));
        }
        return switch (pageId) {
            case KINGDOM -> kingdomPage(player);
            case VEIL -> kingdomPage(player);
            case ANOMALIES -> anomaliesPage(player);
            case ADVANCEMENTS -> advancementsPage(player);
            default -> overviewPage(player);
        };
    }

    // Summarize the major Echelon systems without mixing their management controls together.
    private static AdminPanelRegistry.AdminPage overviewPage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Optional<UUID> king = WorldLeaderService.getLeader(server);
        List<AdminPanelRegistry.AdminEntry> entries = List.of(
                new AdminPanelRegistry.AdminEntry("The Crown", king.map(id -> kingName(server, id))
                        .orElse("No king is appointed"), king.isPresent() ? GOOD : NORMAL, List.of()),
                new AdminPanelRegistry.AdminEntry("Veil Level", Integer.toString(AdvancedWorldBoundary.getLevel(server)),
                        GOOD, List.of()),
                AdminPanelRegistry.AdminEntry.info("Veil anomalies",
                        RestrictionService.restrictions(server).size() + " placed · "
                                + RestrictionService.solidRestrictionCount(server) + " currently solid"),
                AdminPanelRegistry.AdminEntry.info("Echelon currencies",
                        CurrencyRegistry.values().stream().filter(currency ->
                                currency.id().getNamespace().equals(EchelonCore.MOD_ID)).count() + " registered"),
                AdminPanelRegistry.AdminEntry.info("Advancement policy",
                        "Echelon-only advancement trees and completion point rewards are active"));
        return page(OVERVIEW, "EchelonCore", "Current kingdom and world-state overview", entries);
    }

    // Show the current king and let an administrator appoint an online player or clear the crown.
    /** Kingdom: the king (search to appoint, clear to change) and the Veil level and radii. */
    private static AdminPanelRegistry.AdminPage kingdomPage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Optional<UUID> king = WorldLeaderService.getLeader(server);
        List<AdminPanelRegistry.AdminEntry> entries = new ArrayList<>();
        if (king.isPresent()) {
            entries.add(AdminPanelRegistry.AdminEntry.editor("King", "👑 " + kingName(server, king.get()),
                    "Clear the king to appoint someone else.", List.of(),
                    List.of(new AdminPanelRegistry.AdminAction("king.clear", "Clear king", true))));
        } else {
            entries.add(AdminPanelRegistry.AdminEntry.editor("King", "Appoint a king", "The throne is vacant.",
                    List.of(new AdminPanelRegistry.AdminField("king_name", "Player (type to search)", "name", "", 32,
                            server.getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().name())
                                    .sorted(String.CASE_INSENSITIVE_ORDER).toList())),
                    List.of(new AdminPanelRegistry.AdminAction("king.appoint", "Appoint", true))));
        }
        int level = AdvancedWorldBoundary.getLevel(server);
        entries.add(AdminPanelRegistry.AdminEntry.editor("Veil", "Veil level " + level,
                "Comes from global skill-tree effects; it is not set here.", List.of(),
                List.of(new AdminPanelRegistry.AdminAction("veil.refresh", "Recalculate", false))));
        AdvancedWorldBoundary.Radii current = AdvancedWorldBoundary.getRadii(server, player.level().dimension().identifier(), level);
        entries.add(AdminPanelRegistry.AdminEntry.editor("Veil radii", "Add a radius",
                "Inner and outer boundary for one world at one Veil level. Saving an existing world and level replaces it.",
                List.of(new AdminPanelRegistry.AdminField("veil_dimension", "World", "minecraft:overworld",
                                player.level().dimension().identifier().toString(), 128,
                                server.levelKeys().stream().map(key -> key.identifier().toString()).sorted().toList()),
                        new AdminPanelRegistry.AdminField("veil_level", "Level", "0", Integer.toString(level), 10, List.of()),
                        new AdminPanelRegistry.AdminField("veil_inner", "Inner radius", "1000",
                                current == null ? "1000" : Double.toString(current.inner()), 12, List.of()),
                        new AdminPanelRegistry.AdminField("veil_outer", "Outer radius", "1200",
                                current == null ? "1200" : Double.toString(current.outer()), 12, List.of())),
                List.of(new AdminPanelRegistry.AdminAction("veil.set_radii", "Add", false))));
        var radii = AdvancedWorldBoundary.configuredRadii(server);
        if (radii.isEmpty()) entries.add(AdminPanelRegistry.AdminEntry.editor("Veil radii", "No radii",
                "Without a radius a world has no Veil.", List.of(), List.of()));
        radii.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entries.add(
                AdminPanelRegistry.AdminEntry.editor("Veil radii", entry.getKey(),
                        "inner " + entry.getValue().inner() + " · outer " + entry.getValue().outer(), List.of(),
                        List.of(new AdminPanelRegistry.AdminAction("veil.remove_radii|" + entry.getKey(), "Delete", true)))));
        entries.add(AdminPanelRegistry.AdminEntry.editor("Veil", "Anomalies with collision",
                RestrictionService.solidRestrictionCount(server) + " push players out right now", List.of(), List.of()));
        // CWP's divine fields: the kingdom's safe havens.
        entries.addAll(ekuzo.crazyworldprogression.admin.AdminBonusAreaEditor.entries(player,
                "Kingdom areas", "Kingdom area level"));
        return new AdminPanelRegistry.AdminPage(KINGDOM, "Kingdom", "King, the Veil and Kingdom areas", TABS, entries,
                AdminPanelRegistry.PageLayout.EDITOR);
    }

    // List every placed anomaly, its current state, and a confirmed removal control.
    private static AdminPanelRegistry.AdminPage anomaliesPage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        int veilLevel = AdvancedWorldBoundary.getLevel(server);
        List<AdminPanelRegistry.AdminEntry> entries = new ArrayList<>();
        entries.add(section("Create", new AdminPanelRegistry.AdminEntry("Create anomaly", "Place a persistent Veil anomaly.", GOOD,
                List.of(), List.of(AdminPanelRegistry.AdminAction.navigate("Create", ANOMALY_NEW)), null)));
        List<AdminPanelRegistry.AdminEntry> active = new ArrayList<>();
        Map<String, AdminPanelRegistry.AdminEntry> all = new java.util.LinkedHashMap<>();
        for (Restriction anomaly : RestrictionService.restrictions(server)) {
            // Boss encounter areas are managed on the Boss Encounters tab.
            if (ekuzo.echeloncore.encounter.BossEncounters.ownsAnomaly(anomaly.id())) continue;
            Restriction.RestrictionState state = anomaly.stateAt(veilLevel);
            AdminPanelRegistry.AdminEntry entry = new AdminPanelRegistry.AdminEntry(anomaly.id().toString(),
                    anomaly.shape().serializedName() + " · " + anomaly.boundaryMode().serializedName() + " · "
                            + state.collision().serializedName() + "/" + state.visibility().serializedName()
                            + " · " + anomaly.dimension(), state.collision() == Restriction.Collision.SOLID
                    ? DANGER : NORMAL, List.of(),
                    List.of(AdminPanelRegistry.AdminAction.navigate("Edit", ANOMALY_EDIT + anomaly.id()),
                            new AdminPanelRegistry.AdminAction("anomaly.remove|" + anomaly.id(), "Delete", true)), null);
            if (state.collision() == Restriction.Collision.SOLID || state.visibility() != Restriction.Visibility.HIDDEN)
                active.add(section("Active at Veil level " + veilLevel, entry));
            all.put(anomaly.id().toString(), entry);
        }
        entries.addAll(active);
        entries.addAll(AdminCategories.sectioned(server, "anomaly", "All anomalies", all, ANOMALIES));
        return new AdminPanelRegistry.AdminPage(ANOMALIES, "Veil anomalies",
                RestrictionService.restrictions(server).size() + " persistent definition(s)", TABS, entries,
                AdminPanelRegistry.PageLayout.PREVIEW_CARDS);
    }

    private static AdminPanelRegistry.AdminPage newAnomalyPage(ServerPlayer player) {
        List<AdminPanelRegistry.AdminEntry> rows = List.of(
                editor("Identity", "New anomaly", "Unique namespaced ID.",
                        List.of(text("anomaly_id", "ID", "echeloncore:new_anomaly", 128),
                                text("dimension", "World", player.level().dimension().identifier().toString(), 128)),
                        List.of()),
                editor("Geometry", "Shape and center", "Center defaults to your current position.",
                        List.of(select("shape", "Shape", "sphere", List.of("sphere", "cylinder", "pyramid", "cube")),
                                text("center_x", "X", Double.toString(player.getX()), 32),
                                text("center_y", "Y", Double.toString(player.getY()), 32),
                                text("center_z", "Z", Double.toString(player.getZ()), 32)), List.of()),
                editor("Geometry", "Size", "Sphere and cylinder use X as radius.",
                        List.of(text("size_x", "X / radius", "8", 32), text("size_y", "Y", "8", 32),
                                text("size_z", "Z", "8", 32)), List.of()),
                editor("Actions", "Create", "Creates a level-zero solid opaque state.", List.of(),
                        List.of(AdminPanelRegistry.AdminAction.wide("anomaly.create", "Create anomaly", false),
                                AdminPanelRegistry.AdminAction.navigate("Back", ANOMALIES))));
        return new AdminPanelRegistry.AdminPage(ANOMALY_NEW, "Create anomaly", "Persistent Veil geometry",
                TABS, rows, AdminPanelRegistry.PageLayout.EDITOR);
    }

    private static AdminPanelRegistry.AdminPage anomalyEditorPage(ServerPlayer player, Identifier id) {
        Restriction anomaly = RestrictionService.get(player.level().getServer(), id);
        if (anomaly == null) throw new IllegalArgumentException("Unknown anomaly: " + id);
        List<AdminPanelRegistry.AdminEntry> rows = new ArrayList<>();
        rows.add(editor("Identity", "Anomaly", "ID cannot change after creation.",
                List.of(AdminPanelRegistry.AdminField.readOnly("anomaly_id", "ID", id.toString()),
                        text("dimension", "World", anomaly.dimension().toString(), 128)), List.of()));
        rows.add(editor("Geometry", "Shape and center", "Coordinates can be adjusted precisely.",
                List.of(select("shape", "Shape", anomaly.shape().serializedName(),
                                List.of("sphere", "cylinder", "pyramid", "cube")),
                        text("center_x", "X", Double.toString(anomaly.centerX()), 32),
                        text("center_y", "Y", Double.toString(anomaly.centerY()), 32),
                        text("center_z", "Z", Double.toString(anomaly.centerZ()), 32)),
                List.of(new AdminPanelRegistry.AdminAction("anomaly.here", "Use my position", false))));
        rows.add(editor("Geometry", "Size and boundary", "Sphere and cylinder use X as radius.",
                List.of(text("size_x", "X / radius", Double.toString(anomaly.sizeX()), 32),
                        text("size_y", "Y", Double.toString(anomaly.sizeY()), 32),
                        text("size_z", "Z", Double.toString(anomaly.sizeZ()), 32),
                        select("boundary", "Boundary", anomaly.boundaryMode().serializedName(),
                                List.of("keep_out", "keep_in"))), List.of()));
        for (int i = 0; i < anomaly.states().size(); i++) {
            Restriction.RestrictionState state = anomaly.states().get(i);
            String prefix = "state_" + i + "_";
            rows.add(editor("Veil states", "Level " + state.minimumBoundaryLevel(),
                    "The highest matching threshold controls this anomaly.",
                    List.of(text(prefix + "level", "Minimum level", Integer.toString(state.minimumBoundaryLevel()), 10),
                            select(prefix + "collision", "Collision", state.collision().serializedName(),
                                    List.of("solid", "passable")),
                            select(prefix + "visibility", "Visibility", state.visibility().serializedName(),
                                    List.of("opaque", "translucent", "hidden")),
                            select(prefix + "blocks", "Block interaction", state.blockInteraction().serializedName(),
                                    List.of("automatic", "protected", "allowed"))),
                    i == 0 ? List.of() : List.of(new AdminPanelRegistry.AdminAction(
                            "anomaly.state.remove|" + state.minimumBoundaryLevel(), "Remove", true))));
        }
        rows.add(editor("Veil states", "Add level", "Add a new threshold with its own collision and appearance.",
                List.of(text("new_state_level", "Minimum level", "1", 10),
                        select("new_state_collision", "Collision", "passable", List.of("solid", "passable")),
                        select("new_state_visibility", "Visibility", "hidden",
                                List.of("opaque", "translucent", "hidden")),
                        select("new_state_blocks", "Block interaction", "automatic",
                                List.of("automatic", "protected", "allowed"))),
                List.of(new AdminPanelRegistry.AdminAction("anomaly.state.add", "Add", false))));
        rows.add(editor("Actions", "Save", "Save geometry and all Veil states.", List.of(),
                List.of(AdminPanelRegistry.AdminAction.wide("anomaly.save", "Save anomaly", false),
                        AdminPanelRegistry.AdminAction.navigate("Back", ANOMALIES))));
        rows.add(editor("Actions", "Delete", "Permanently remove this anomaly.", List.of(),
                List.of(new AdminPanelRegistry.AdminAction("anomaly.delete", "Delete anomaly", true))));
        return new AdminPanelRegistry.AdminPage(ANOMALY_EDIT + id, "Edit Veil anomaly", id.toString(), TABS,
                rows, AdminPanelRegistry.PageLayout.EDITOR);
    }

    private static AdminPanelRegistry.AdminEntry editor(String section, String title, String detail,
                                                        List<AdminPanelRegistry.AdminField> fields,
                                                        List<AdminPanelRegistry.AdminAction> actions) {
        return AdminPanelRegistry.AdminEntry.editor(section, title, detail, fields, actions);
    }
    private static AdminPanelRegistry.AdminField text(String id, String label, String value, int length) {
        return new AdminPanelRegistry.AdminField(id, label, "", value, length, List.of());
    }
    private static AdminPanelRegistry.AdminField select(String id, String label, String value, List<String> options) {
        return AdminPanelRegistry.AdminField.select(id, label, value, options);
    }

    // Present advancement-reward settings and current online Echelon Point balances.
    private static AdminPanelRegistry.AdminPage advancementsPage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        List<AdminPanelRegistry.AdminEntry> entries = new ArrayList<>();
        entries.add(AdminPanelRegistry.AdminEntry.info("Reward tiers",
                "Really easy: 1 EP · Easy: 3 EP · Medium: 5 EP · Hard: 15 EP"));
        entries.add(AdminPanelRegistry.AdminEntry.info("Announcements",
                "Only hard Echelon advancements broadcast to the server"));
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            long points = CurrencyService.getBalance(server, target.getUUID(), EchelonCore.ECHELON_POINTS);
            entries.add(AdminPanelRegistry.AdminEntry.info(target.getName().getString(), points + " Echelon Points"));
        }
        return page(ADVANCEMENTS, "Advancements", "Echelon profession and mystery progression", entries);
    }

    // Validate and execute an Echelon-owned action after CWP authenticates the administrator.
    private static AdminPanelRegistry.ActionResult action(ServerPlayer player, String pageId, String actionId,
                                                          Map<String, String> values) {
        MinecraftServer server = player.level().getServer();
        if (AdminCategories.owns(pageId)) return AdminCategories.action(player, pageId, actionId, values);
        if (ekuzo.echeloncore.encounter.BossEncounterAdmin.owns(pageId)) return ekuzo.echeloncore.encounter.BossEncounterAdmin.action(player, pageId, actionId, values);
        if (ekuzo.crazyworldprogression.admin.AdminBonusAreaEditor.handles(actionId))
            return ekuzo.crazyworldprogression.admin.AdminBonusAreaEditor.action(player, actionId, values);
        if (actionId.equals("king.appoint")) {
            String name = values.getOrDefault("king_name", "").trim();
            ServerPlayer candidate = server.getPlayerList().getPlayerByName(name);
            if (candidate == null) return AdminPanelRegistry.ActionResult.failure("No online player named " + name);
            WorldLeaderService.setLeader(server, candidate.getUUID());
            return AdminPanelRegistry.ActionResult.success("Appointed " + candidate.getGameProfile().name() + " as king");
        }
        if (actionId.equals("anomaly.create")) {
            Identifier id = Identifier.parse(values.get("anomaly_id").trim());
            Restriction anomaly = new Restriction(id, Identifier.parse(values.get("dimension").trim()),
                    Restriction.Shape.parse(values.get("shape")), number(values, "center_x"),
                    number(values, "center_y"), number(values, "center_z"), number(values, "size_x"),
                    number(values, "size_y"), number(values, "size_z"), Restriction.BoundaryMode.KEEP_OUT,
                    List.of(new Restriction.RestrictionState(0, Restriction.Collision.SOLID,
                            Restriction.Visibility.OPAQUE, Restriction.BlockInteraction.AUTOMATIC)));
            RestrictionService.create(server, anomaly);
            return AdminPanelRegistry.ActionResult.success("Created anomaly " + id, ANOMALY_EDIT + id);
        }
        if (pageId.startsWith(ANOMALY_EDIT)) {
            Identifier id = Identifier.parse(pageId.substring(ANOMALY_EDIT.length()));
            Restriction current = RestrictionService.get(server, id);
            if (current == null) return AdminPanelRegistry.ActionResult.failure("Unknown anomaly " + id);
            if (actionId.equals("anomaly.delete")) {
                RestrictionService.remove(server, id);
                return AdminPanelRegistry.ActionResult.success("Deleted anomaly " + id, ANOMALIES);
            }
            if (actionId.equals("anomaly.save") || actionId.equals("anomaly.here")
                    || actionId.equals("anomaly.state.add") || actionId.startsWith("anomaly.state.remove|")) {
                int removeIndex = -1;
                if (actionId.startsWith("anomaly.state.remove|")) {
                    int threshold = Integer.parseInt(actionId.substring(actionId.indexOf('|') + 1));
                    for (int i = 0; i < current.states().size(); i++) {
                        if (current.states().get(i).minimumBoundaryLevel() == threshold) removeIndex = i;
                    }
                    if (removeIndex <= 0) throw new IllegalArgumentException("Cannot remove this Veil state");
                }
                Restriction updated = editedAnomaly(current, values, removeIndex);
                if (actionId.equals("anomaly.here")) {
                    updated = updated.movedTo(player.level().dimension().identifier(), new Vec3(
                            player.getX(), player.getY(), player.getZ()));
                } else if (actionId.equals("anomaly.state.add")) {
                    int level = Integer.parseInt(values.get("new_state_level"));
                    if (updated.states().stream().anyMatch(state -> state.minimumBoundaryLevel() == level)) {
                        throw new IllegalArgumentException("A Veil state already exists at level " + level);
                    }
                    updated = updated.withState(new Restriction.RestrictionState(
                            level,
                            Restriction.Collision.parse(values.get("new_state_collision")),
                            Restriction.Visibility.parse(values.get("new_state_visibility")),
                            Restriction.BlockInteraction.parse(values.get("new_state_blocks"))));
                }
                RestrictionService.update(server, updated);
                return AdminPanelRegistry.ActionResult.success("Saved anomaly " + id);
            }
        }
        if (actionId.equals("king.clear")) {
            WorldLeaderService.clearLeader(server);
            return AdminPanelRegistry.ActionResult.success("Cleared the Echelon crown");
        }
        if (actionId.equals("veil.refresh")) {
            RestrictionService.refreshLevel(server);
            return AdminPanelRegistry.ActionResult.success("Refreshed Veil anomaly runtime state");
        }
        if (actionId.equals("veil.set_radii")) {
            Identifier dimension = Identifier.parse(values.getOrDefault("veil_dimension", "").trim());
            int level = Integer.parseInt(values.getOrDefault("veil_level", "").trim());
            double inner = Double.parseDouble(values.getOrDefault("veil_inner", "").trim());
            double outer = Double.parseDouble(values.getOrDefault("veil_outer", "").trim());
            AdvancedWorldBoundary.setRadii(server, dimension, level, inner, outer);
            return AdminPanelRegistry.ActionResult.success("Saved Veil radii for " + dimension + " at level " + level);
        }
        String[] parts = actionId.split("\\|", -1);
        if (parts.length == 3 && parts[0].equals("veil.remove_radii")) {
            Identifier dimension = Identifier.parse(parts[1]);
            int level = Integer.parseInt(parts[2]);
            return AdvancedWorldBoundary.removeRadii(server, dimension, level)
                    ? AdminPanelRegistry.ActionResult.success("Removed Veil radii for " + dimension + " at level " + level)
                    : AdminPanelRegistry.ActionResult.failure("No such Veil radius entry");
        }
        if (parts.length == 2 && parts[0].equals("king.set")) {
            UUID king = UUID.fromString(parts[1]);
            if (server.getPlayerList().getPlayer(king) == null) {
                return AdminPanelRegistry.ActionResult.failure("That player is no longer online");
            }
            WorldLeaderService.setLeader(server, king);
            return AdminPanelRegistry.ActionResult.success("Appointed " + kingName(server, king) + " as king");
        }
        if (parts.length == 2 && parts[0].equals("anomaly.remove")) {
            Identifier id = Identifier.parse(parts[1]);
            return RestrictionService.remove(server, id)
                    ? AdminPanelRegistry.ActionResult.success("Removed anomaly " + id)
                    : AdminPanelRegistry.ActionResult.failure("Unknown anomaly " + id);
        }
        return AdminPanelRegistry.ActionResult.failure("Unknown Echelon admin action: " + actionId);
    }

    private static Restriction editedAnomaly(Restriction current, Map<String, String> values, int removeIndex) {
        List<Restriction.RestrictionState> states = new ArrayList<>();
        for (int i = 0; i < current.states().size(); i++) {
            if (i == removeIndex) continue;
            Restriction.RestrictionState old = current.states().get(i);
            String prefix = "state_" + i + "_";
            states.add(new Restriction.RestrictionState(
                    Integer.parseInt(values.getOrDefault(prefix + "level", Integer.toString(old.minimumBoundaryLevel()))),
                    Restriction.Collision.parse(values.getOrDefault(prefix + "collision", old.collision().serializedName())),
                    Restriction.Visibility.parse(values.getOrDefault(prefix + "visibility", old.visibility().serializedName())),
                    Restriction.BlockInteraction.parse(values.getOrDefault(prefix + "blocks", old.blockInteraction().serializedName()))));
        }
        return new Restriction(current.id(), Identifier.parse(values.getOrDefault("dimension",
                current.dimension().toString()).trim()), Restriction.Shape.parse(values.getOrDefault("shape",
                current.shape().serializedName())), number(values, "center_x", current.centerX()),
                number(values, "center_y", current.centerY()), number(values, "center_z", current.centerZ()),
                number(values, "size_x", current.sizeX()), number(values, "size_y", current.sizeY()),
                number(values, "size_z", current.sizeZ()), Restriction.BoundaryMode.parse(
                values.getOrDefault("boundary", current.boundaryMode().serializedName())), states);
    }

    private static double number(Map<String, String> values, String key) {
        return Double.parseDouble(values.get(key).trim());
    }
    private static double number(Map<String, String> values, String key, double fallback) {
        return values.containsKey(key) ? number(values, key) : fallback;
    }

    // Resolve an online king name and retain a useful UUID fallback for offline rulers.
    private static String kingName(MinecraftServer server, UUID king) {
        ServerPlayer online = server.getPlayerList().getPlayer(king);
        return online == null ? king.toString() : online.getName().getString();
    }

    // Construct a consistently tabbed Echelon page rendered entirely by CWP.
    private static AdminPanelRegistry.AdminPage page(String id, String title, String subtitle,
                                                     List<AdminPanelRegistry.AdminEntry> entries) {
        return new AdminPanelRegistry.AdminPage(id, title, subtitle, TABS, entries);
    }
}
