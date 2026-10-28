package ekuzo.echeloncore.entity.ability;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mobs in the middle of an ability (winding up, casting, shadow-stepping). A busy mob starts no other ability and
 * can't hit anything with its own attacks.
 */
public final class Busy {
    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();

    private Busy() { }

    public static boolean is(Entity entity) { return BUSY.contains(entity.getUUID()) || ShadowStep.active(entity); }
    static void start(Entity entity) { BUSY.add(entity.getUUID()); }
    static void end(Entity entity) { BUSY.remove(entity.getUUID()); }

    /** Keeps the mob in place for this tick (call every tick while it winds up). */
    static void holdStill(Mob mob) {
        mob.getNavigation().stop();
        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
    }
}
