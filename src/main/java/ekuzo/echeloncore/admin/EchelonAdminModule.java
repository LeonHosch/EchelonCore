package ekuzo.echeloncore.admin;

import ekuzo.crazyworldprogression.admin.AdminPanelRegistry;
import ekuzo.crazyworldprogression.currency.CurrencyRegistry;
import ekuzo.crazyworldprogression.currency.CurrencyService;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.king.KingService;
import ekuzo.echeloncore.veil.VeilManager;
import ekuzo.echeloncore.veil.anomaly.VeilAnomaly;
import ekuzo.echeloncore.veil.anomaly.VeilAnomalyService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
    private static final String ADVANCEMENTS = "advancements";
    private static final List<AdminPanelRegistry.AdminTab> TABS = List.of(
            new AdminPanelRegistry.AdminTab(OVERVIEW, "Overview"),
            new AdminPanelRegistry.AdminTab(KINGDOM, "Kingdom"),
            new AdminPanelRegistry.AdminTab(VEIL, "Veil"),
            new AdminPanelRegistry.AdminTab(ANOMALIES, "Anomalies"),
            new AdminPanelRegistry.AdminTab(ADVANCEMENTS, "Advancements"));

    // Prevent construction of the Echelon module registrar.
    private EchelonAdminModule() {
    }

    // Add one EchelonCore button and its server-owned pages to CWP's dashboard.
    public static void register() {
        AdminPanelRegistry.register(new AdminPanelRegistry.AdminModule(
                EchelonCore.id("admin"), "EchelonCore",
                "Kingdom leadership, Veil state, anomalies and Echelon progression.",
                "minecraft:golden_helmet", OVERVIEW, EchelonAdminModule::page, EchelonAdminModule::action));
    }

    // Route a requested Echelon tab to freshly calculated server state.
    private static AdminPanelRegistry.AdminPage page(ServerPlayer player, String pageId) {
        return switch (pageId) {
            case KINGDOM -> kingdomPage(player);
            case VEIL -> veilPage(player);
            case ANOMALIES -> anomaliesPage(player);
            case ADVANCEMENTS -> advancementsPage(player);
            default -> overviewPage(player);
        };
    }

    // Summarize the major Echelon systems without mixing their management controls together.
    private static AdminPanelRegistry.AdminPage overviewPage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Optional<UUID> king = KingService.getKing(server);
        List<AdminPanelRegistry.AdminEntry> entries = List.of(
                new AdminPanelRegistry.AdminEntry("The Crown", king.map(id -> kingName(server, id))
                        .orElse("No king is appointed"), king.isPresent() ? GOOD : NORMAL, List.of()),
                new AdminPanelRegistry.AdminEntry("Veil Level", Integer.toString(VeilManager.getVeilLevel(server)),
                        GOOD, List.of()),
                AdminPanelRegistry.AdminEntry.info("Veil anomalies",
                        VeilAnomalyService.anomalies(server).size() + " placed · "
                                + VeilAnomalyService.solidAnomalyCount(server) + " currently solid"),
                AdminPanelRegistry.AdminEntry.info("Echelon currencies",
                        CurrencyRegistry.values().stream().filter(currency ->
                                currency.id().getNamespace().equals(EchelonCore.MOD_ID)).count() + " registered"),
                AdminPanelRegistry.AdminEntry.info("Advancement policy",
                        "Echelon-only advancement trees and completion point rewards are active"));
        return page(OVERVIEW, "EchelonCore", "Current kingdom and world-state overview", entries);
    }

    // Show the current king and let an administrator appoint an online player or clear the crown.
    private static AdminPanelRegistry.AdminPage kingdomPage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Optional<UUID> king = KingService.getKing(server);
        List<AdminPanelRegistry.AdminEntry> entries = new ArrayList<>();
        entries.add(new AdminPanelRegistry.AdminEntry("Current ruler",
                king.map(id -> kingName(server, id) + " · " + id).orElse("The throne is vacant"),
                king.isPresent() ? GOOD : NORMAL, king.isPresent()
                ? List.of(new AdminPanelRegistry.AdminAction("king.clear", "Clear", true)) : List.of()));
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            boolean current = king.filter(candidate.getUUID()::equals).isPresent();
            entries.add(new AdminPanelRegistry.AdminEntry(candidate.getName().getString(),
                    current ? "Currently appointed king" : candidate.getUUID().toString(), current ? GOOD : NORMAL,
                    current ? List.of() : List.of(new AdminPanelRegistry.AdminAction(
                            "king.set|" + candidate.getUUID(), "Appoint", true))));
        }
        return page(KINGDOM, "Kingdom", "Leadership controls apply immediately", entries);
    }

    // Explain the progression-derived Veil level and expose a safe runtime re-index operation.
    private static AdminPanelRegistry.AdminPage veilPage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        int level = VeilManager.getVeilLevel(server);
        List<AdminPanelRegistry.AdminEntry> entries = new ArrayList<>();
        entries.add(new AdminPanelRegistry.AdminEntry("Current Veil Level", Integer.toString(level), GOOD,
                List.of(new AdminPanelRegistry.AdminAction("veil.refresh", "Refresh", false))));
        entries.add(AdminPanelRegistry.AdminEntry.info("Progression ownership",
                "The level is derived from global skill-tree effects and is intentionally not overwritten here."));
        entries.add(AdminPanelRegistry.AdminEntry.info("Active collision set",
                VeilAnomalyService.solidAnomalyCount(server) + " anomaly/anomalies require player rejection"));
        return page(VEIL, "The Veil", "Global level and anomaly runtime state", entries);
    }

    // List every placed anomaly, its current state, and a confirmed removal control.
    private static AdminPanelRegistry.AdminPage anomaliesPage(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        int veilLevel = VeilManager.getVeilLevel(server);
        List<AdminPanelRegistry.AdminEntry> entries = new ArrayList<>();
        for (VeilAnomaly anomaly : VeilAnomalyService.anomalies(server)) {
            VeilAnomaly.AnomalyState state = anomaly.stateAt(veilLevel);
            entries.add(new AdminPanelRegistry.AdminEntry(anomaly.id().toString(),
                    anomaly.shape().serializedName() + " · " + anomaly.boundaryMode().serializedName() + " · "
                            + state.collision().serializedName() + "/" + state.visibility().serializedName()
                            + " · " + anomaly.dimension(), state.collision() == VeilAnomaly.Collision.SOLID
                    ? DANGER : NORMAL, List.of(new AdminPanelRegistry.AdminAction(
                    "anomaly.remove|" + anomaly.id(), "Remove", true))));
        }
        if (entries.isEmpty()) entries.add(AdminPanelRegistry.AdminEntry.info("No anomalies placed",
                "Use /veil anomaly create to author one in the current dimension."));
        return page(ANOMALIES, "Veil anomalies", entries.size() + " persistent definition(s)", entries);
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
        if (actionId.equals("king.clear")) {
            KingService.clearKing(server);
            return AdminPanelRegistry.ActionResult.success("Cleared the Echelon crown");
        }
        if (actionId.equals("veil.refresh")) {
            VeilAnomalyService.refreshVeilLevel(server);
            return AdminPanelRegistry.ActionResult.success("Refreshed Veil anomaly runtime state");
        }
        String[] parts = actionId.split("\\|", -1);
        if (parts.length == 2 && parts[0].equals("king.set")) {
            UUID king = UUID.fromString(parts[1]);
            if (server.getPlayerList().getPlayer(king) == null) {
                return AdminPanelRegistry.ActionResult.failure("That player is no longer online");
            }
            KingService.setKing(server, king);
            return AdminPanelRegistry.ActionResult.success("Appointed " + kingName(server, king) + " as king");
        }
        if (parts.length == 2 && parts[0].equals("anomaly.remove")) {
            Identifier id = Identifier.parse(parts[1]);
            return VeilAnomalyService.remove(server, id)
                    ? AdminPanelRegistry.ActionResult.success("Removed anomaly " + id)
                    : AdminPanelRegistry.ActionResult.failure("Unknown anomaly " + id);
        }
        return AdminPanelRegistry.ActionResult.failure("Unknown Echelon admin action: " + actionId);
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
