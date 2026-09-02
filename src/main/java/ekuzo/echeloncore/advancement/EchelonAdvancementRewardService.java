package ekuzo.echeloncore.advancement;

import ekuzo.crazyworldprogression.currency.ClaimLedgerService;
import ekuzo.crazyworldprogression.currency.CurrencyService;
import ekuzo.echeloncore.EchelonCore;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static java.util.Map.entry;

/** Converts completed Echelon advancements into one-time Echelon Point rewards. */
public final class EchelonAdvancementRewardService {
    private static final Identifier REWARD_LEDGER = EchelonCore.id("advancement_rewards");
    private static final Map<Identifier, RewardTier> REWARDS = Map.ofEntries(
            entry(id("mining/root"), RewardTier.REALLY_EASY),
            entry(id("mining/first_coal"), RewardTier.REALLY_EASY),
            entry(id("mining/first_iron"), RewardTier.EASY),
            entry(id("mining/first_gold"), RewardTier.EASY),
            entry(id("mining/first_diamond"), RewardTier.MEDIUM),
            entry(id("mining/stone_1000"), RewardTier.MEDIUM),
            entry(id("mining/stone_10000"), RewardTier.HARD),
            entry(id("mining/ancient_debris"), RewardTier.HARD),
            entry(id("woodworking/root"), RewardTier.REALLY_EASY),
            entry(id("woodworking/crafting_table"), RewardTier.REALLY_EASY),
            entry(id("woodworking/wooden_axe"), RewardTier.REALLY_EASY),
            entry(id("woodworking/chest"), RewardTier.EASY),
            entry(id("woodworking/bookshelf"), RewardTier.EASY),
            entry(id("woodworking/logs_100"), RewardTier.EASY),
            entry(id("woodworking/logs_1000"), RewardTier.MEDIUM),
            entry(id("woodworking/logs_10000"), RewardTier.HARD),
            entry(id("mysteries/root"), RewardTier.EASY),
            entry(id("mysteries/outer_veil"), RewardTier.MEDIUM),
            entry(id("mysteries/powerful_soul"), RewardTier.MEDIUM),
            entry(id("mysteries/veil_level"), RewardTier.MEDIUM),
            entry(id("mysteries/nether"), RewardTier.EASY),
            entry(id("mysteries/end"), RewardTier.HARD),
            entry(id("mysteries/crowned"), RewardTier.HARD),
            entry(id("monster_hunting/root"), RewardTier.REALLY_EASY),
            entry(id("monster_hunting/kills_25"), RewardTier.EASY),
            entry(id("monster_hunting/kills_100"), RewardTier.MEDIUM),
            entry(id("monster_hunting/kills_500"), RewardTier.MEDIUM),
            entry(id("monster_hunting/kills_1000"), RewardTier.HARD),
            entry(id("monster_hunting/creeper"), RewardTier.EASY),
            entry(id("monster_hunting/enderman"), RewardTier.EASY),
            entry(id("monster_hunting/wither"), RewardTier.HARD),
            entry(id("farming/root"), RewardTier.REALLY_EASY),
            entry(id("farming/harvests_100"), RewardTier.EASY),
            entry(id("farming/harvests_1000"), RewardTier.MEDIUM),
            entry(id("farming/harvests_10000"), RewardTier.HARD),
            entry(id("farming/first_breeding"), RewardTier.REALLY_EASY),
            entry(id("farming/breed_100"), RewardTier.MEDIUM),
            entry(id("farming/bread"), RewardTier.REALLY_EASY),
            entry(id("farming/golden_carrot"), RewardTier.EASY),
            entry(id("fishing/root"), RewardTier.REALLY_EASY),
            entry(id("fishing/fish_25"), RewardTier.EASY),
            entry(id("fishing/fish_100"), RewardTier.MEDIUM),
            entry(id("fishing/fish_500"), RewardTier.MEDIUM),
            entry(id("fishing/fish_1000"), RewardTier.HARD),
            entry(id("fishing/fishing_rod"), RewardTier.REALLY_EASY),
            entry(id("fishing/boat"), RewardTier.EASY)
    );

    // Prevent instantiation of the advancement reward service.
    private EchelonAdvancementRewardService() {
    }

    // Find completed configured advancements and reconcile their persistent one-time rewards.
    public static void synchronizeCompletedRewards(ServerPlayer player) {
        int totalAwarded = 0;
        for (Identifier advancementId : REWARDS.keySet()) totalAwarded += award(player, advancementId);
        if (totalAwarded > 0) sendRewardMessage(player, totalAwarded);
    }

    // Award the configured EP tier once when the requested advancement exists and is complete.
    public static boolean awardForCompletion(ServerPlayer player, Identifier advancementId) {
        int awarded = award(player, advancementId);
        if (awarded > 0) sendRewardMessage(player, awarded);
        return awarded > 0;
    }

    // Perform the validated claim and return the number of points paid so callers can combine notifications.
    private static int award(ServerPlayer player, Identifier advancementId) {
        RewardTier tier = REWARDS.get(advancementId);
        if (tier == null || !isComplete(player, advancementId)) return 0;
        MinecraftServer server = player.level().getServer();
        long currentBalance = CurrencyService.getBalance(server, player.getUUID(), EchelonCore.ECHELON_POINTS);
        try {
            Math.addExact(currentBalance, tier.echelonPoints());
        } catch (ArithmeticException exception) {
            EchelonCore.LOGGER.warn("Could not reward {} for {} because their EP balance would overflow",
                    player.getGameProfile().name(), advancementId);
            return 0;
        }
        ClaimLedgerService.ClaimResult claim = ClaimLedgerService.claim(server, player.getUUID(),
                REWARD_LEDGER, advancementId, REWARDS.size());
        if (claim != ClaimLedgerService.ClaimResult.CLAIMED) return 0;
        CurrencyService.credit(server, player.getUUID(), EchelonCore.ECHELON_POINTS, tier.echelonPoints());
        return tier.echelonPoints();
    }

    // Tell the player about one reward or one combined reconciliation without producing message spam.
    private static void sendRewardMessage(ServerPlayer player, int amount) {
        player.sendSystemMessage(Component.translatable("message.echelon-core.advancement_reward", amount)
                .withStyle(ChatFormatting.AQUA));
    }

    // Check advancement progress without creating a reward claim for missing definitions.
    private static boolean isComplete(ServerPlayer player, Identifier advancementId) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    // Create a stable identifier in the EchelonCore namespace for the reward table.
    private static Identifier id(String path) {
        return EchelonCore.id(path);
    }

    /** Named balance tiers keep the provisional reward amounts centralized and easy to rebalance. */
    public enum RewardTier {
        REALLY_EASY(1), EASY(3), MEDIUM(5), HARD(15);

        private final int echelonPoints;

        // Store the number of Echelon Points represented by this difficulty tier.
        RewardTier(int echelonPoints) {
            this.echelonPoints = echelonPoints;
        }

        // Return the current provisional Echelon Point amount for this tier.
        public int echelonPoints() {
            return echelonPoints;
        }
    }
}
