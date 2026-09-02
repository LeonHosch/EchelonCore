package ekuzo.echeloncore.advancement;

import ekuzo.crazyworldprogression.currency.CurrencyService;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.king.KingService;
import ekuzo.echeloncore.veil.VeilManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Awards Echelon's custom milestones from authoritative player statistics and gameplay state. */
public final class EchelonAdvancementManager {
    private static final String CRITERION = "earned";
    private static final Identifier MINING_ROOT = id("mining/root");
    private static final Identifier WOODWORKING_ROOT = id("woodworking/root");
    private static final Identifier MYSTERIES_ROOT = id("mysteries/root");
    private static final Identifier HUNTING_ROOT = id("monster_hunting/root");
    private static final Identifier FARMING_ROOT = id("farming/root");
    private static final Identifier FISHING_ROOT = id("fishing/root");
    private static final List<Block> LOG_BLOCKS = List.of(
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
            Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG,
            Blocks.PALE_OAK_LOG, Blocks.CRIMSON_STEM, Blocks.WARPED_STEM
    );

    // Prevent instantiation of the server-side milestone checker.
    private EchelonAdvancementManager() {
    }

    // Evaluate connected players once per second instead of repeating statistic checks every game tick.
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            server.getPlayerList().getPlayers().forEach(EchelonAdvancementManager::evaluate);
        });
    }

    // Evaluate every Echelon advancement category for one connected player.
    private static void evaluate(ServerPlayer player) {
        evaluateMining(player);
        evaluateWoodworking(player);
        evaluateMysteries(player);
        evaluateMonsterHunting(player);
        evaluateFarming(player);
        evaluateFishing(player);
        EchelonAdvancementRewardService.synchronizeCompletedRewards(player);
    }

    // Award stone-volume and ore-discovery milestones from Minecraft's persistent mined-block statistics.
    private static void evaluateMining(ServerPlayer player) {
        int stone = mined(player, Blocks.STONE);
        if (stone < 1) return;
        grant(player, MINING_ROOT);
        if (stone >= 1_000) grant(player, id("mining/stone_1000"));
        if (stone >= 10_000) grant(player, id("mining/stone_10000"));
        if (mined(player, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE) >= 1) {
            grant(player, id("mining/first_coal"));
        }
        if (mined(player, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE) >= 1) {
            grant(player, id("mining/first_iron"));
        }
        if (mined(player, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE) >= 1) {
            grant(player, id("mining/first_gold"));
        }
        if (mined(player, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE) >= 1) {
            grant(player, id("mining/first_diamond"));
        }
        if (mined(player, Blocks.ANCIENT_DEBRIS) >= 1) grant(player, id("mining/ancient_debris"));
    }

    // Award log-volume and crafted woodworking milestones from persistent vanilla statistics.
    private static void evaluateWoodworking(ServerPlayer player) {
        long logs = LOG_BLOCKS.stream().mapToLong(block -> mined(player, block)).sum();
        if (logs < 1L) return;
        grant(player, WOODWORKING_ROOT);
        if (logs >= 100L) grant(player, id("woodworking/logs_100"));
        if (logs >= 1_000L) grant(player, id("woodworking/logs_1000"));
        if (logs >= 10_000L) grant(player, id("woodworking/logs_10000"));
        if (crafted(player, Items.CRAFTING_TABLE) >= 1) grant(player, id("woodworking/crafting_table"));
        if (crafted(player, Items.CHEST) >= 1) grant(player, id("woodworking/chest"));
        if (crafted(player, Items.BOOKSHELF) >= 1) grant(player, id("woodworking/bookshelf"));
        if (crafted(player, Items.WOODEN_AXE) >= 1) grant(player, id("woodworking/wooden_axe"));
    }

    // Award discoveries that belong to the veil and other established Echelon world systems.
    private static void evaluateMysteries(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        double veilDistance = VeilManager.distanceFromSpawn(server, player);
        if (veilDistance > VeilManager.WORLD_RADIUS_ONE) grant(player, MYSTERIES_ROOT);
        if (!isComplete(player, MYSTERIES_ROOT)) return;
        if (veilDistance > VeilManager.WORLD_RADIUS_TWO) grant(player, id("mysteries/outer_veil"));
        if (CurrencyService.getBalance(server, player.getUUID(), EchelonCore.POWERFUL_SOULS) >= 1L) {
            grant(player, id("mysteries/powerful_soul"));
        }
        if (VeilManager.getVeilLevel(server) >= 1) grant(player, id("mysteries/veil_level"));
        if (KingService.isKing(server, player.getUUID())) grant(player, id("mysteries/crowned"));
        if (player.level().dimension() == Level.NETHER) grant(player, id("mysteries/nether"));
        if (player.level().dimension() == Level.END) grant(player, id("mysteries/end"));
    }

    // Award the Crown's hunting ranks from total kills and notable quarry recorded by vanilla statistics.
    private static void evaluateMonsterHunting(ServerPlayer player) {
        int kills = custom(player, Stats.MOB_KILLS);
        if (kills < 1) return;
        grant(player, HUNTING_ROOT);
        if (kills >= 25) grant(player, id("monster_hunting/kills_25"));
        if (kills >= 100) grant(player, id("monster_hunting/kills_100"));
        if (kills >= 500) grant(player, id("monster_hunting/kills_500"));
        if (kills >= 1_000) grant(player, id("monster_hunting/kills_1000"));
        if (killed(player, EntityTypes.CREEPER) >= 1) grant(player, id("monster_hunting/creeper"));
        if (killed(player, EntityTypes.ENDERMAN) >= 1) grant(player, id("monster_hunting/enderman"));
        if (killed(player, EntityTypes.WITHER) >= 1) grant(player, id("monster_hunting/wither"));
    }

    // Award agricultural ranks for harvesting staple crops, breeding livestock, and preparing food.
    private static void evaluateFarming(ServerPlayer player) {
        int harvests = mined(player, Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES,
                Blocks.BEETROOTS, Blocks.NETHER_WART);
        if (harvests < 1) return;
        grant(player, FARMING_ROOT);
        if (harvests >= 100) grant(player, id("farming/harvests_100"));
        if (harvests >= 1_000) grant(player, id("farming/harvests_1000"));
        if (harvests >= 10_000) grant(player, id("farming/harvests_10000"));
        int animalsBred = custom(player, Stats.ANIMALS_BRED);
        if (animalsBred >= 1) grant(player, id("farming/first_breeding"));
        if (animalsBred >= 100) grant(player, id("farming/breed_100"));
        if (crafted(player, Items.BREAD) >= 1) grant(player, id("farming/bread"));
        if (crafted(player, Items.GOLDEN_CARROT) >= 1) grant(player, id("farming/golden_carrot"));
    }

    // Award fishing-guild ranks from the player's persistent caught-fish statistic and crafted equipment.
    private static void evaluateFishing(ServerPlayer player) {
        int fish = custom(player, Stats.FISH_CAUGHT);
        if (fish < 1) return;
        grant(player, FISHING_ROOT);
        if (fish >= 25) grant(player, id("fishing/fish_25"));
        if (fish >= 100) grant(player, id("fishing/fish_100"));
        if (fish >= 500) grant(player, id("fishing/fish_500"));
        if (fish >= 1_000) grant(player, id("fishing/fish_1000"));
        if (crafted(player, Items.FISHING_ROD) >= 1) grant(player, id("fishing/fishing_rod"));
        if (crafted(player, Items.OAK_BOAT) >= 1) grant(player, id("fishing/boat"));
    }

    // Sum exact mined-block counters while allowing one milestone to accept related ore variants.
    private static int mined(ServerPlayer player, Block... blocks) {
        long total = 0L;
        for (Block block : blocks) total += player.getStats().getValue(Stats.BLOCK_MINED, block);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    // Read the vanilla crafted-item counter for one woodworking product.
    private static int crafted(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getStats().getValue(Stats.ITEM_CRAFTED, item);
    }

    // Read one vanilla custom statistic such as total mob kills, animals bred, or fish caught.
    private static int custom(ServerPlayer player, Identifier statistic) {
        return player.getStats().getValue(Stats.CUSTOM, statistic);
    }

    // Read how many entities of one exact type the player has killed.
    private static int killed(ServerPlayer player, EntityType<?> entityType) {
        return player.getStats().getValue(Stats.ENTITY_KILLED, entityType);
    }

    // Award the single framework-controlled criterion when the advancement exists and remains incomplete.
    private static void grant(ServerPlayer player, Identifier advancementId) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
        if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }

    // Check completion without creating progress for a definition that failed to load.
    private static boolean isComplete(ServerPlayer player, Identifier advancementId) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    // Create one stable advancement identifier inside EchelonCore's data namespace.
    private static Identifier id(String path) {
        return EchelonCore.id(path);
    }
}
