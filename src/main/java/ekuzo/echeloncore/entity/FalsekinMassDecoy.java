package ekuzo.echeloncore.entity;

import ekuzo.echeloncore.entity.ability.ShadowStep;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mass Decoy: the Falsekin vanishes and a herd of 3 to 8 mobs of one kind appears close together. All of them are fake
 * except one, the Falsekin itself. The first hit decides:
 * <ul>
 *   <li>While stalking: hitting the real one reveals it, without the full-power buff. Hitting a fake one the first
 *       time makes the whole herd vanish and the Falsekin keeps stalking; the second time it attacks at full power.</li>
 *   <li>In combat: hitting the real one punishes it (stunned for 2 seconds). Hitting a fake one makes that fake run
 *       at the player and explode; the rest of the herd dissolves and the Falsekin fights on.</li>
 * </ul>
 * An undecided herd breaks up after {@link #TIMEOUT} ticks while stalking. In combat it lasts only
 * {@link #COMBAT_TIMEOUT} ticks: then each fake either dissolves or (half of them) runs at the player and explodes,
 * and the Falsekin fights on. The real one has a tell: it keeps watching its player and drips purple goo now and then.
 */
public final class FalsekinMassDecoy {
    static final int TIMEOUT = 1_200;
    static final int COMBAT_TIMEOUT = 120;
    static final int STUN = 40;
    private static final Map<UUID, Group> GROUPS = new HashMap<>();

    private FalsekinMassDecoy() { }

    private static final class Group {
        final UUID id = UUID.randomUUID();
        final Falsekin real;
        final List<Falsekin> fakes = new ArrayList<>();
        final boolean combat;
        final int round;
        final long expires;

        Group(Falsekin real, boolean combat, int round, long expires) {
            this.real = real;
            this.combat = combat;
            this.round = round;
            this.expires = expires;
        }
    }

    /**
     * Starts a Mass Decoy around {@code real}, worn as {@code kind}. {@code round} counts the decoys used while
     * stalking (1 or 2; ignored in combat).
     */
    public static void start(ServerLevel level, Falsekin real, String kind, boolean combat, int round) {
        if (active(real.herdGroup())) return;
        Group group = new Group(real, combat, round, level.getGameTime() + (combat ? COMBAT_TIMEOUT : TIMEOUT));
        level.sendParticles(Falsekin.goo(), real.getX(), real.getY() + 1, real.getZ(), 40, 0.5, 0.6, 0.5, 0.1);
        level.playSound(null, real.getX(), real.getY(), real.getZ(), Falsekin.soundById("entity.slime.squish"),
                SoundSource.HOSTILE, 1.2F, 0.5F);
        Vec3 center = real.position();
        int size = 3 + real.getRandom().nextInt(6);
        int realIndex = real.getRandom().nextInt(size);
        for (int k = 0; k < size; k++) {
            Vec3 spot = ShadowStep.spotNear(level, real, center, 0.8, 3.2);
            if (spot == null) spot = center;
            if (k == realIndex) {
                real.teleportTo(spot.x, spot.y, spot.z);
                continue;
            }
            Falsekin fake = EchelonEntities.FALSEKIN.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (fake == null) continue;
            fake.snapTo(spot.x, spot.y, spot.z, real.getRandom().nextFloat() * 360, 0);
            fake.makeHerdDecoy(kind, group.id);
            level.addFreshEntity(fake);
            group.fakes.add(fake);
        }
        real.hideInHerd(kind, group.id);
        GROUPS.put(group.id, group);
    }

    public static boolean active(UUID groupId) { return groupId != null && GROUPS.containsKey(groupId); }

    /** A member of a herd was hit by {@code attacker}. Returns true when the hit itself should still land. */
    public static boolean hit(ServerLevel level, Falsekin member, LivingEntity attacker) {
        Group group = GROUPS.remove(member.herdGroup());
        if (group == null) return true;
        boolean real = member == group.real;
        if (group.combat) {
            if (real) {
                dissolve(level, group.fakes);
                group.real.leaveHerd(level, false);
                group.real.stun(STUN);
                level.playSound(null, member.getX(), member.getY(), member.getZ(), Falsekin.soundById("entity.ravager.stunned"),
                        SoundSource.HOSTILE, 1.5F, 0.8F);
                return true;
            }
            List<Falsekin> others = new ArrayList<>(group.fakes);
            others.remove(member);
            dissolve(level, others);
            if (attacker instanceof Player player) member.chargeAndExplode(player);
            else dissolve(level, List.of(member));
            group.real.leaveHerd(level, false);
            return false;
        }
        dissolve(level, group.fakes);
        if (real) {
            group.real.leaveHerd(level, true);
            group.real.reveal(level, attacker, group.real.powerFraction());
            return false;
        }
        if (group.round <= 1) {
            // Wrong one: the herd vanishes and the Falsekin goes back to stalking (it will appear again).
            group.real.vanishForNow(level);
            return false;
        }
        group.real.leaveHerd(level, true);
        group.real.reveal(level, attacker, 1.0F);
        return false;
    }

    /** Called every tick by each herd member: breaks up a herd whose time ran out or whose Falsekin is gone. */
    static void tick(ServerLevel level, Falsekin member) {
        Group group = GROUPS.get(member.herdGroup());
        if (group == null) {
            if (member.decoy()) dissolve(level, List.of(member));
            else member.leaveHerd(level, false);
            return;
        }
        if (level.getGameTime() < group.expires && group.real.isAlive() && !group.real.isRemoved()) return;
        GROUPS.remove(group.id);
        Player player = group.real.target(level);
        if (group.combat && player != null && player.isAlive()) {
            // Time's up: half of the fakes dissolve, the rest charge the player and explode.
            for (Falsekin fake : group.fakes) {
                if (fake.isRemoved()) continue;
                if (fake.getRandom().nextBoolean()) dissolve(level, List.of(fake));
                else fake.chargeAndExplode(player);
            }
        } else {
            dissolve(level, group.fakes);
        }
        if (group.real.isAlive() && !group.real.isRemoved()) group.real.leaveHerd(level, !group.combat);
    }

    private static void dissolve(ServerLevel level, List<Falsekin> fakes) {
        for (Falsekin fake : fakes) {
            if (fake.isRemoved()) continue;
            level.sendParticles(Falsekin.goo(), fake.getX(), fake.getY() + 0.6, fake.getZ(), 15, 0.3, 0.4, 0.3, 0.05);
            fake.discard();
        }
    }
}
