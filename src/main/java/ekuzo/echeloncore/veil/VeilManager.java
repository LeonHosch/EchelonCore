package ekuzo.echeloncore.veil;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class VeilManager {
    public static final double WORLD_RADIUS_ONE = 1000.0;
    public static final double WORLD_RADIUS_TWO = 1200.0;

    private VeilManager() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) applyVeilDamage(server);
        });
    }

    private static void applyVeilDamage(MinecraftServer server) {
        BlockPos spawn = server.getRespawnData().pos();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            double x = player.getX() - spawn.getX();
            double z = player.getZ() - spawn.getZ();
            double distance = Math.sqrt(x * x + z * z);
            if (distance > WORLD_RADIUS_TWO) {
                player.hurtServer(player.level(), player.damageSources().magic(), 5.0F);
            } else if (distance > WORLD_RADIUS_ONE && server.getTickCount() % 100 == 0) {
                player.hurtServer(player.level(), player.damageSources().magic(), 2.0F);
            }
        }
    }
}
