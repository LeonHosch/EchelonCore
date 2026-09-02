package ekuzo.echeloncore.veil;

import ekuzo.crazyworldprogression.progression.skilltrees.SkillStatRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ekuzo.echeloncore.veil.anomaly.VeilAnomalyService;

public final class VeilManager {
    public static final String ADD_VEIL_LEVEL = "addVeilLevel";
    public static final double WORLD_RADIUS_ONE = 1000.0;
    public static final double WORLD_RADIUS_TWO = 1200.0;

    // Prevent instantiation of the server-side veil event handler.
    private VeilManager() { }

    // Check player distances once per second and apply zone-specific periodic damage.
    public static void initialize() {
        SkillStatRegistry.registerNumberStat(ADD_VEIL_LEVEL, "stat.echelon-core.veil_level", 0.0, true,
                VeilManager::validateLevelIncrement,
                (player, total) -> VeilAnomalyService.refreshVeilLevel(player.level().getServer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) applyVeilDamage(server);
        });
    }

    // Measure every connected player from overworld spawn and damage those inside either veil band.
    private static void applyVeilDamage(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            double distance = distanceFromSpawn(server, player);
            if (distance > WORLD_RADIUS_TWO) {
                player.hurtServer(player.level(), player.damageSources().magic(), 5.0F);
            } else if (distance > WORLD_RADIUS_ONE && server.getTickCount() % 100 == 0) {
                player.hurtServer(player.level(), player.damageSources().magic(), 2.0F);
            }
        }
    }

    // Return the current global veil level represented by unlocked progression expressions.
    public static int getVeilLevel(MinecraftServer server) {
        return levelFromTotal(SkillStatRegistry.getGlobalTotal(server, ADD_VEIL_LEVEL));
    }

    // Measure one player's horizontal distance from the overworld respawn point used as the veil center.
    public static double distanceFromSpawn(MinecraftServer server, ServerPlayer player) {
        BlockPos spawn = server.getRespawnData().pos();
        double x = player.getX() - spawn.getX();
        double z = player.getZ() - spawn.getZ();
        return Math.sqrt(x * x + z * z);
    }

    // Restrict every veil-level skill expression to the single permitted +1 increment.
    private static void validateLevelIncrement(double value) {
        if (value != 1.0) {
            throw new IllegalArgumentException(ADD_VEIL_LEVEL + " only accepts 1; veil levels increment one at a time");
        }
    }

    // Convert an accumulated stat total into its exact bounded integer representation.
    private static int levelFromTotal(double total) {
        if (total < 0.0 || total > Integer.MAX_VALUE || total != Math.rint(total)) {
            throw new IllegalStateException("Invalid cumulative veil level: " + total);
        }
        return (int) total;
    }
}
