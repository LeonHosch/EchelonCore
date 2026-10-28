package ekuzo.echeloncore;

import ekuzo.crazyworldprogression.currency.CurrencyDefinition;
import ekuzo.crazyworldprogression.currency.CurrencyRegistry;
import ekuzo.crazyworldprogression.events.RandomEventRegistry;
import ekuzo.crazyworldprogression.progression.skilltrees.BaselineStatRegistry;
import ekuzo.crazyworldprogression.progression.skilltrees.SkillTreeRegistry;
import ekuzo.echeloncore.advancement.EchelonAdvancementManager;
import ekuzo.echeloncore.admin.EchelonAdminModule;
import ekuzo.echeloncore.command.KingCommands;
import ekuzo.echeloncore.command.VeilCommands;
import ekuzo.crazyworldprogression.worldboundary.WorldLeaderService;
import ekuzo.echeloncore.network.RequiredClientMods;
import ekuzo.crazyworldprogression.worldboundary.AdvancedWorldBoundary;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static ekuzo.crazyworldprogression.currency.CurrencyDefinition.CurrencyScope.GLOBAL;
import static ekuzo.crazyworldprogression.currency.CurrencyDefinition.CurrencyScope.PLAYER;

public final class EchelonCore implements ModInitializer {
    public static final String MOD_ID = "echelon-core";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier KINGDOM_POINTS = id("kingdom_points");
    public static final Identifier ECHELON_POINTS = id("echelon_points");
    public static final Identifier FAKHRUL_CURRENCY = id("fakhrul_currency");
    public static final Identifier POWERFUL_SOULS = id("powerful_souls");

    // Register all concrete Echelon content and policies with CWP, then start Echelon-owned gameplay systems.
    @Override
    public void onInitialize() {
        RequiredClientMods.register();
        registerCurrencies();
        ekuzo.echeloncore.item.EchelonItems.register();
        ekuzo.echeloncore.block.EchelonBlocks.register();
        ekuzo.echeloncore.entity.EchelonEntities.register();
        ekuzo.echeloncore.balance.VanillaWeapons.register();
        // CWP's reforging (Enchanting by default) is called Soulforging in Echelon; its proficiency is Soulweaving.
        ekuzo.crazyworldprogression.reforge.ReforgeBranding.set(new ekuzo.crazyworldprogression.reforge.ReforgeBranding(
                "Soulforging", "Reforge soul", "Soul reforged: ", "Soulweaving"));
        EchelonAdminModule.register();
        RandomEventRegistry.registerSource(MOD_ID, MOD_ID, "random-events.yml");
        BaselineStatRegistry.registerSource(id("default-baseline"), MOD_ID, "baseline-stats.yml");
        SkillTreeRegistry.registerSource(MOD_ID, MOD_ID, "skilltrees");
        AdvancedWorldBoundary.registerLevelStat("addVeilLevel", "stat.echelon-core.veil_level");
        ekuzo.crazyworldprogression.worldboundary.bonus.BonusAreas.setDisplayName("Kingdom area");
        SkillTreeRegistry.registerPurchasePolicy(id("kingdom-progression"), (player, tree) ->
                WorldLeaderService.isLeader(player.level().getServer(), player.getUUID())
                        ? SkillTreeRegistry.PurchaseDecision.ALLOWED
                        : SkillTreeRegistry.PurchaseDecision.denied("Only the elected king can unlock this tree."));
        EchelonAdvancementManager.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> WorldLeaderService.getLeader(server));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            KingCommands.register(dispatcher);
            VeilCommands.register(dispatcher);
            ekuzo.crazyworldprogression.worldboundary.bonus.BonusAreas.registerCommands(dispatcher, "kingdomarea");
        });
        LOGGER.info("Initialized Echelon gameplay systems");
    }

    // Define the four Echelon currencies whose storage, commands, and GUI rendering are supplied by CWP.
    private static void registerCurrencies() {
        register(KINGDOM_POINTS, "Kingdom Points", "KP", GLOBAL, "kp-32x32.png", 7, 4, 18, 24,
                ChatFormatting.GOLD, "kp", "kingdompoints");
        register(ECHELON_POINTS, "Echelon Points", "EP", PLAYER, "ep-32x32.png", 7, 4, 17, 24,
                ChatFormatting.AQUA, "ep", "echelonpoints");
        register(FAKHRUL_CURRENCY, "Fakhrul Currency", "FC", PLAYER, "fc-32x32.png", 5, 4, 22, 24,
                ChatFormatting.LIGHT_PURPLE, "fc", "fakhrul");
        register(POWERFUL_SOULS, "Powerful Souls", "PS", PLAYER, "ps-32x32.png", 8, 4, 16, 24,
                ChatFormatting.DARK_PURPLE, "ps", "powerfulsouls");
    }

    // Adapt compact Echelon metadata into one complete framework currency definition.
    private static void register(Identifier id, String name, String abbreviation,
                                 CurrencyDefinition.CurrencyScope scope, String icon, int u, int v,
                                 int width, int height, ChatFormatting color, String... commands) {
        CurrencyRegistry.register(new CurrencyDefinition(id, name, abbreviation, scope,
                id("currencies/" + icon), u, v, width, height, color, List.of(commands)));
    }

    // Create a stable resource or registry identifier inside EchelonCore's namespace.
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
