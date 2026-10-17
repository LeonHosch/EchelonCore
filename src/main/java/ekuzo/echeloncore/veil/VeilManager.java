package ekuzo.echeloncore.veil;

import ekuzo.crazyworldprogression.progression.skilltrees.SkillStatRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import ekuzo.echeloncore.veil.anomaly.VeilAnomalyService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class VeilManager {
    public static final String ADD_VEIL_LEVEL = "addVeilLevel";
    private static final Map<MinecraftServer, Integer> LAST_LEVELS = new WeakHashMap<>();

    // Prevent instantiation of the server-side veil event handler.
    private VeilManager() { }

    // Check player distances once per second and apply zone-specific periodic damage.
    public static void initialize() {
        SkillStatRegistry.registerNumberStat(ADD_VEIL_LEVEL, "stat.echelon-core.veil_level", 0.0, true,
                VeilManager::validateLevelIncrement,
                (player, total) -> {
                    VeilAnomalyService.refreshVeilLevel(player.level().getServer());
                    VeilRadiusNetworking.syncAll(player.level().getServer());
                });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                VeilRadiusNetworking.send(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                int level = getVeilLevel(server);
                Integer previous = LAST_LEVELS.put(server, level);
                if (previous != null && previous != level) VeilRadiusNetworking.syncAll(server);
                applyVeilDamage(server);
            }
        });
    }

    // Damage players only in dimensions configured at the current exact Veil Level.
    private static void applyVeilDamage(MinecraftServer server) {
        int level = getVeilLevel(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Radii radii = getRadii(server, player.level().dimension().identifier(), level);
            if (radii == null) continue;
            double distance = distanceFromSpawn(server, player);
            if (distance > radii.outer()) {
                player.hurtServer(player.level(), player.damageSources().magic(), 5.0F);
            } else if (distance > radii.inner() && server.getTickCount() % 100 == 0) {
                player.hurtServer(player.level(), player.damageSources().magic(), 2.0F);
            }
        }
    }

    // Return the current global veil level represented by unlocked progression expressions.
    public static int getVeilLevel(MinecraftServer server) {
        return levelFromTotal(SkillStatRegistry.getGlobalTotal(server, ADD_VEIL_LEVEL));
    }

    // Use overworld spawn only in the overworld; other dimensions center their own radius at origin.
    public static double distanceFromSpawn(MinecraftServer server, ServerPlayer player) {
        BlockPos center = player.level() == server.overworld() ? server.getRespawnData().pos() : BlockPos.ZERO;
        double x = player.getX() - center.getX();
        double z = player.getZ() - center.getZ();
        return Math.sqrt(x * x + z * z);
    }

    private static String key(Identifier dimension, int level) {
        return dimension + "|" + level;
    }

    public static Radii getRadii(MinecraftServer server, Identifier dimension, int level) {
        return VeilRadiusData.get(server).values().get(key(dimension, level));
    }

    public static Map<String, Radii> configuredRadii(MinecraftServer server) {
        return VeilRadiusData.get(server).values();
    }

    public static Map<String, Radii> currentRadii(MinecraftServer server) {
        int level = getVeilLevel(server);
        Map<String, Radii> result = new LinkedHashMap<>();
        for (Map.Entry<String, Radii> entry : configuredRadii(server).entrySet()) {
            if (entry.getKey().endsWith("|" + level)) {
                result.put(entry.getKey().substring(0, entry.getKey().lastIndexOf('|')), entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    public static void setRadii(MinecraftServer server, Identifier dimension, int level,
                                double inner, double outer) {
        if (server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimension)) == null) {
            throw new IllegalArgumentException("Unknown dimension " + dimension);
        }
        if (level < 0) throw new IllegalArgumentException("Veil Level must be non-negative");
        VeilRadiusData.get(server).put(key(dimension, level), new Radii(inner, outer));
        VeilRadiusNetworking.syncAll(server);
    }

    public static boolean removeRadii(MinecraftServer server, Identifier dimension, int level) {
        boolean removed = VeilRadiusData.get(server).remove(key(dimension, level));
        if (removed) VeilRadiusNetworking.syncAll(server);
        return removed;
    }

    public record Radii(double inner, double outer) {
        public Radii {
            if (!Double.isFinite(inner) || !Double.isFinite(outer) || inner <= 0 || outer <= inner) {
                throw new IllegalArgumentException("Radii must be finite and 0 < inner < outer");
            }
        }
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
