package ekuzo.echeloncore.entity.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Damage a caster takes while an interruptible ability winds up (the Penitent's Wail, Mourning and Grasp). The
 * thresholds are fixed amounts of damage taken (after armor), the same however many players there are, so one strong,
 * well-geared player can reach them alone. A stun (CWP's Fletching.stun) breaks the ability outright.
 */
final class Interrupt {
    private static final Map<UUID, Float> DEALT = new ConcurrentHashMap<>();

    private Interrupt() { }

    static void initialize() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (taken > 0) DEALT.computeIfPresent(entity.getUUID(), (id, dealt) -> dealt + taken);
        });
        ekuzo.crazyworldprogression.fletching.Fletching.onStun((target, source) ->
                DEALT.computeIfPresent(target.getUUID(), (id, dealt) -> Float.MAX_VALUE));
    }

    /** Starts counting the damage {@code caster} takes. */
    static void watch(Entity caster) { DEALT.put(caster.getUUID(), 0.0F); }

    /** True once {@code caster} took at least {@code threshold} damage since {@link #watch}. */
    static boolean broken(Entity caster, float threshold) { return DEALT.getOrDefault(caster.getUUID(), 0.0F) >= threshold; }

    static void stop(Entity caster) { DEALT.remove(caster.getUUID()); }
}
