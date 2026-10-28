package ekuzo.echeloncore.item;

import ekuzo.crazyworldprogression.progression.skilltrees.SkillStatRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sanctuary, the passive of the Oath of the Lifebound shield, unlocked by the {@code unlockSanctuary} stat (the
 * Guardian of Life 4-piece bonus). After the shield has been raised for {@link #CHARGE_TICKS}, it holds an aura of
 * {@link #RADIUS} blocks around the bearer; every attack the shield blocks then heals each player inside by half a
 * heart, at most once per {@link #HEAL_COOLDOWN_TICKS}. Lowering the shield drops the charge.
 */
public final class Sanctuary {
    public static final String STAT = "unlockSanctuary";
    public static final int CHARGE_TICKS = 40;
    public static final int HEAL_COOLDOWN_TICKS = 15;
    public static final double RADIUS = 3.0;
    private static final float HEAL = 1.0F;
    private static final Map<UUID, Long> LAST_HEAL = new ConcurrentHashMap<>();

    private Sanctuary() { }

    static void initialize() {
        SkillStatRegistry.registerUnlockStat(STAT, "stat.echelon-core.unlock_sanctuary");
        ServerTickEvents.END_SERVER_TICK.register(Sanctuary::tick);
    }

    private static boolean unlocked(ServerPlayer player) { return SkillStatRegistry.getTotal(player, STAT) > 0; }

    private static boolean raised(ServerPlayer player) {
        return player.isUsingItem() && player.getUseItem().is(EchelonItems.OATH_OF_THE_LIFEBOUND);
    }

    /** Raised long enough with the passive unlocked: the aura is up. */
    private static boolean charged(ServerPlayer player) {
        return raised(player) && player.getTicksUsingItem() >= CHARGE_TICKS && unlocked(player);
    }

    /** Charge-up particles, the ready chime, and the aura ring while it is up. */
    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!raised(player) || !unlocked(player)) continue;
            ServerLevel level = player.level();
            int using = player.getTicksUsingItem();
            if (using < CHARGE_TICKS) {
                if (using % 5 == 0) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0,
                        player.getZ(), 2, 0.4, 0.5, 0.4, 0.0);
                continue;
            }
            if (using == CHARGE_TICKS) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 1.0F, 1.2F);
                player.sendOverlayMessage(Component.translatable("message.echelon-core.sanctuary.ready"));
            }
            if (now % 4 != 0) continue;
            int points = 24;
            double offset = (now / 4 % points) * (Math.PI * 2 / points / 3);
            for (int index = 0; index < points; index++) {
                double angle = offset + index * Math.PI * 2 / points;
                level.sendParticles(ParticleTypes.COMPOSTER, player.getX() + Math.cos(angle) * RADIUS,
                        player.getY() + 0.1, player.getZ() + Math.sin(angle) * RADIUS, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Called when {@code bearer} blocked an attack with {@code shield}. */
    public static void onBlocked(ServerPlayer bearer, ItemStack shield) {
        if (!shield.is(EchelonItems.OATH_OF_THE_LIFEBOUND) || !charged(bearer)) return;
        long now = bearer.level().getServer().getTickCount();
        Long last = LAST_HEAL.get(bearer.getUUID());
        if (last != null && now - last < HEAL_COOLDOWN_TICKS) return;
        LAST_HEAL.put(bearer.getUUID(), now);
        ServerLevel level = bearer.level();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(bearer) > RADIUS * RADIUS) continue;
            player.heal(HEAL);
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.0, player.getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
        level.playSound(null, bearer.getX(), bearer.getY(), bearer.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 1.6F);
    }
}
