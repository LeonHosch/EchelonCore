package ekuzo.echeloncore.king;

import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;

public final class KingService {
    // Prevent instantiation of the public king-system facade.
    private KingService() { }

    // Read the optional elected king from persistent Echelon state.
    public static Optional<UUID> getKing(MinecraftServer server) { return KingData.get(server).electedKing(); }

    // Test whether a UUID is the currently appointed king for purchase-policy checks.
    public static boolean isKing(MinecraftServer server, UUID playerUuid) {
        return getKing(server).filter(playerUuid::equals).isPresent();
    }
    // Appoint a player immediately, replacing any previous king.
    public static void setKing(MinecraftServer server, UUID playerUuid) { KingData.get(server).setKing(playerUuid); }

    // Remove the current king appointment.
    public static void clearKing(MinecraftServer server) { KingData.get(server).clearKing(); }
}
