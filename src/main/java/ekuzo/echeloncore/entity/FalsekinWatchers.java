package ekuzo.echeloncore.entity;

import ekuzo.echeloncore.entity.ability.ShadowStep;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Watchers, a rare stage-3 scare while the Falsekin stalks: 6 to 10 villagers stand spaced out in a ring around its
 * player, 10 to 16 blocks away, all staring at them. Each is a fake; hit one and it simply vanishes. One of them has
 * glowing red eyes: walk up to it and it vanishes, leaving the player in Darkness. They fade after
 * {@link #LIFETIME} ticks. They make no sound and never move. Purely to unsettle the player: the Falsekin does not
 * attack while they are out.
 */
public final class FalsekinWatchers {
    static final int LIFETIME = 900;
    /** Chance per tick in stage 3 (about once every 6 minutes), and the wait before the next one. */
    static final int CHANCE = 7_200;
    static final int COOLDOWN = 3_600;

    /** Players with watchers out, until which game time. */
    private static final java.util.Map<java.util.UUID, Long> ACTIVE = new java.util.HashMap<>();

    private FalsekinWatchers() { }

    /** True while watchers stand around {@code player}. */
    public static boolean active(ServerLevel level, java.util.UUID player) {
        Long until = ACTIVE.get(player);
        if (until == null) return false;
        if (level.getGameTime() < until) return true;
        ACTIVE.remove(player);
        return false;
    }

    public static int start(ServerLevel level, Falsekin owner, Player target) {
        int count = 6 + owner.getRandom().nextInt(5);
        int redEyes = owner.getRandom().nextInt(count);
        double offset = owner.getRandom().nextDouble() * Math.PI * 2;
        int placed = 0;
        for (int k = 0; k < count; k++) {
            double angle = offset + k * Math.PI * 2 / count + (owner.getRandom().nextDouble() - 0.5) * 0.3;
            double radius = 10 + owner.getRandom().nextDouble() * 6;
            Vec3 ring = target.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            Vec3 spot = ShadowStep.spotNear(level, owner, ring, 0, 1.5);
            if (spot == null) continue;
            Falsekin watcher = EchelonEntities.FALSEKIN.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (watcher == null) continue;
            watcher.snapTo(spot.x, spot.y, spot.z, Falsekin.yawTowards(spot, target.position()), 0);
            watcher.makeWatcher(target, k == redEyes, LIFETIME);
            level.addFreshEntity(watcher);
            placed++;
        }
        if (placed > 0) ACTIVE.put(target.getUUID(), level.getGameTime() + LIFETIME);
        return placed;
    }
}
