package ekuzo.echeloncore.client;

import ekuzo.echeloncore.entity.UnicornBoost;
import ekuzo.echeloncore.entity.UnicornMount;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * The rider's side of the unicorn's second jump (UnicornBoost): pressing jump in the air starts it, and while jump is
 * held (up to 1.5 seconds) the unicorn keeps rising. The rider's game moves a ridden mount, so the lift is applied
 * here; the server shows the rainbow and plays the sound.
 */
final class UnicornBoostClient {
    private static boolean wasDown;
    /** Ticks of the running boost, or -1. */
    private static int boostTicks = -1;
    /** Used since the unicorn last stood on the ground. */
    private static boolean used;

    private UnicornBoostClient() { }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(UnicornBoostClient::tick);
    }

    private static void tick(Minecraft client) {
        boolean down = client.options.keyJump.isDown();
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (client.player == null || !(client.player.getVehicle() instanceof UnicornMount unicorn)) {
            stop(false);
            used = false;
            return;
        }
        if (unicorn.onGround() && boostTicks < 0) used = false;
        if (boostTicks < 0) {
            if (!pressed || unicorn.onGround() || used || client.gui.screen() != null) return;
            used = true;
            boostTicks = 0;
            send(true);
        }
        if (!down || boostTicks >= UnicornBoost.MAX_TICKS) {
            stop(true);
            return;
        }
        Vec3 motion = unicorn.getDeltaMovement();
        double lift = boostTicks == 0 ? UnicornBoost.START_LIFT : UnicornBoost.HELD_LIFT;
        unicorn.setDeltaMovement(motion.x * 1.04, Math.max(motion.y, lift), motion.z * 1.04);
        unicorn.resetFallDistance();
        boostTicks++;
    }

    private static void stop(boolean tell) {
        if (boostTicks < 0) return;
        boostTicks = -1;
        if (tell) send(false);
    }

    private static void send(boolean start) {
        if (ClientPlayNetworking.canSend(UnicornBoost.Payload.TYPE)) ClientPlayNetworking.send(new UnicornBoost.Payload(start));
    }
}
