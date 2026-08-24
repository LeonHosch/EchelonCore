package ekuzo.echeloncore.king;

import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;

public final class KingService {
    private KingService() { }

    public static Optional<UUID> getKing(MinecraftServer server) { return KingData.get(server).electedKing(); }
    public static boolean isKing(MinecraftServer server, UUID playerUuid) {
        return getKing(server).filter(playerUuid::equals).isPresent();
    }
    public static void setKing(MinecraftServer server, UUID playerUuid) { KingData.get(server).setKing(playerUuid); }
    public static void clearKing(MinecraftServer server) { KingData.get(server).clearKing(); }
}
