package ekuzo.echeloncore.entity;

import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The unicorn's second jump: in the air, jump again and it farts a rainbow and rises for as long as jump is held, up
 * to {@link #MAX_TICKS}. Once per time in the air. The rider's game moves the unicorn (it controls the mount, like
 * any ridden horse, see UnicornBoostClient); the server checks the rules and shows the rainbow and sound to everyone.
 */
public final class UnicornBoost {
    /** 1.5 seconds. */
    public static final int MAX_TICKS = 30;
    /** Upward speed at the start of the boost and while it is held (blocks per tick). */
    public static final double START_LIFT = 0.62, HELD_LIFT = 0.24;
    private static final int[] RAINBOW = {0xE84048, 0xF49234, 0xFAD640, 0x5CC860, 0x409CEC, 0x8260DC, 0xD264C8};
    /** Players boosting now, with the ticks so far. */
    private static final Map<UUID, Integer> BOOSTING = new HashMap<>();
    /** Players who used their boost and have not landed since. */
    private static final Set<UUID> USED = new HashSet<>();

    private UnicornBoost() { }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!payload.start()) {
                BOOSTING.remove(player.getUUID());
                return;
            }
            if (!(player.getVehicle() instanceof UnicornMount unicorn) || unicorn.onGround()
                    || USED.contains(player.getUUID()) || BOOSTING.containsKey(player.getUUID())) return;
            USED.add(player.getUUID());
            BOOSTING.put(player.getUUID(), 0);
            fart(unicorn, 0.75F);
        });
        ServerTickEvents.END_SERVER_TICK.register(UnicornBoost::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            BOOSTING.remove(handler.player.getUUID());
            USED.remove(handler.player.getUUID());
        });
    }

    private static void tick(MinecraftServer server) {
        USED.removeIf(uuid -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            return player == null || !(player.getVehicle() instanceof UnicornMount unicorn)
                    || unicorn.onGround() && !BOOSTING.containsKey(uuid);
        });
        BOOSTING.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(player.getVehicle() instanceof UnicornMount unicorn)) return true;
            int ticks = entry.getValue() + 1;
            entry.setValue(ticks);
            rainbow(unicorn, ticks);
            if (ticks % 7 == 0) fart(unicorn, 0.35F);
            return ticks >= MAX_TICKS;
        });
    }

    /** A puff of rainbow from under the tail, drifting backwards. */
    private static void rainbow(UnicornMount unicorn, int ticks) {
        if (!(unicorn.level() instanceof ServerLevel level)) return;
        Vec3 back = Vec3.directionFromRotation(0, unicorn.getYRot()).scale(-1.0);
        Vec3 at = unicorn.position().add(back.scale(0.9)).add(0, 1.05, 0);
        for (int band = 0; band < RAINBOW.length; band++) {
            double up = (band - 3) * 0.12;
            level.sendParticles(new DustParticleOptions(RAINBOW[(band + ticks / 3) % RAINBOW.length], 1.6F),
                    at.x + back.x * 0.2, at.y + up, at.z + back.z * 0.2, 2, 0.08, 0.04, 0.08, 0.0);
        }
        if (ticks % 4 == 0)
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.02);
    }

    private static void fart(UnicornMount unicorn, float volume) {
        float pitch = 0.5F + unicorn.getRandom().nextFloat() * 0.12F;
        unicorn.level().playSound(null, unicorn.getX(), unicorn.getY() + 1.0, unicorn.getZ(),
                SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.NEUTRAL, volume, pitch);
        unicorn.level().playSound(null, unicorn.getX(), unicorn.getY() + 1.0, unicorn.getZ(),
                SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, volume * 0.8F, 0.6F);
    }

    /** Client to server: the rider started (true) or let go of (false) the second jump. */
    public record Payload(boolean start) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(EchelonCore.id("unicorn_boost"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Payload::start, Payload::new);

        @Override public Type<Payload> type() { return TYPE; }
    }
}
