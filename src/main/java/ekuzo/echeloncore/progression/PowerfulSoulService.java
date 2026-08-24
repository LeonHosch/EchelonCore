package ekuzo.echeloncore.progression;

import ekuzo.crazyworldprogression.currency.ClaimLedgerService;
import ekuzo.crazyworldprogression.currency.CurrencyService;
import ekuzo.echeloncore.EchelonCore;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class PowerfulSoulService {
    private PowerfulSoulService() { }

    public static ClaimLedgerService.ClaimResult claim(MinecraftServer server, UUID playerUuid,
                                                       Identifier claimId, int maximumClaims) {
        Math.addExact(CurrencyService.getBalance(server, playerUuid, EchelonCore.POWERFUL_SOULS), 1L);
        ClaimLedgerService.ClaimResult result = ClaimLedgerService.claim(
                server, playerUuid, EchelonCore.POWERFUL_SOULS, claimId, maximumClaims);
        if (result == ClaimLedgerService.ClaimResult.CLAIMED) {
            CurrencyService.credit(server, playerUuid, EchelonCore.POWERFUL_SOULS, 1L);
        }
        return result;
    }
}
