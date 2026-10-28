package ekuzo.echeloncore.entity.ability;

import com.geckolib.animatable.GeoEntity;
import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.crazyworldprogression.mobforge.entity.MobForgeEntityData;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.CrowSwarm;
import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.Leshen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;


/**
 * Crow Swarm: the caster stands still, points at its target and sends a wave of crows in a straight line. It tracks
 * the target for the first half of the two-second cast, then its aim is locked. Crows that hit someone cling to them and peck
 * for {@link CrowSwarm#DAMAGE} every second for three seconds. When the target is close, the caster first Shadow
 * Steps behind it. A Leshen's eyes and antlers glow pink while it aims and purple once the aim is locked. The caster can't attack while casting.
 * A stun (CWP's Fletching.stun, for example a disruption arrow) interrupts the cast, even on stun-immune casters.
 */
public final class CrowSwarmAbility implements MobAbility {
    public static final Identifier ID = EchelonCore.id("crow_swarm");
    private static final int CAST_TICKS = 40;
    private static final double CLOSE = 5.0;
    private static final float TURN_SPEED = 18.0F;
    private static final double CROW_SPEED = 0.7;
    /** Casters whose current cast a stun interrupted. */
    private static final java.util.Set<java.util.UUID> INTERRUPTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Casters in the middle of the cast (only those can be interrupted). */
    private static final java.util.Set<java.util.UUID> CASTING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Registers the interrupt with CWP's stuns. */
    public static void initialize() {
        ekuzo.crazyworldprogression.fletching.Fletching.onStun((target, source) -> {
            if (CASTING.contains(target.getUUID())) INTERRUPTED.add(target.getUUID());
        });
    }

    @Override public long cooldownTicks() { return 160; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        if (!(caster instanceof Mob mob) || Busy.is(mob) || !(context.target() instanceof LivingEntity target))
            return false;
        double distance = mob.distanceTo(target);
        return target.isAlive() && distance <= 26 && (distance < CLOSE || mob.hasLineOfSight(target));
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        if (mob.distanceTo(target) < CLOSE) {
            MobForgeEntityData.setCooldown(mob, ShadowStep.ID, level.getGameTime() + ShadowStep.COOLDOWN);
            ShadowStep.start(level, mob, target, ShadowStep.behind(level, mob, target, 6), () -> cast(level, mob, target));
        } else cast(level, mob, target);
    }

    private static void cast(ServerLevel level, Mob mob, LivingEntity target) {
        if (!mob.isAlive() || !target.isAlive()) return;
        Busy.start(mob);
        CASTING.add(mob.getUUID());
        INTERRUPTED.remove(mob.getUUID());
        if (mob instanceof Leshen leshen) leshen.setCastPhase(Leshen.AIMING);
        if (mob instanceof GeoEntity geo) geo.triggerAnim("abilities", "cast");
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.2F, 0.6F);
        Vec3[] aim = {target.getEyePosition()};
        Vec3[] seen = new Vec3[6];                                    // the target's last positions, to lead the shot
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            ticks[0]++;
            if (!mob.isAlive()) {
                finish(mob);
                return true;
            }
            if (INTERRUPTED.remove(mob.getUUID())) {
                // Stunned mid-cast: the crows scatter and nothing is launched (the cooldown still runs).
                level.playSound(null, mob.getX(), mob.getY() + 2, mob.getZ(), SoundEvents.PARROT_HURT, SoundSource.HOSTILE, 1.2F, 0.6F);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight(),
                        mob.getZ(), 20, 0.6, 0.4, 0.6, 0.02);
                finish(mob);
                return true;
            }
            if (target.isAlive()) seen[ticks[0] % seen.length] = target.position();
            if (ticks[0] <= CAST_TICKS / 2 && target.isAlive()) aim[0] = target.getEyePosition();   // aim locks halfway
            if (ticks[0] == CAST_TICKS / 2 + 1) {
                aim[0] = lead(mob, target, seen, ticks[0]);
                if (mob instanceof Leshen leshen) leshen.setCastPhase(Leshen.LOCKED);
            }
            if (ticks[0] % 8 == 0) level.playSound(null, mob.getX(), mob.getY() + 2, mob.getZ(), SoundEvents.PARROT_FLY,
                    SoundSource.HOSTILE, 0.8F, 0.7F);                // crows gathering
            face(mob, aim[0]);
            mob.getNavigation().stop();
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            if (ticks[0] < CAST_TICKS) return false;
            Vec3 forward = Vec3.directionFromRotation(0, mob.getYRot());
            Vec3 from = mob.getEyePosition().add(forward.scale(mob.getBbWidth() + 0.8)).add(0, -0.4, 0);
            CrowSwarm.launch(level, mob, from, aim[0]);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.5F, 0.8F);
            finish(mob);
            return true;
        });
    }

    /**
     * Where the target will be when the crows arrive if it keeps moving the way it did over the last few ticks: the
     * rest of the cast plus the crows' flight time. Stopping or turning still dodges the crows.
     */
    private static Vec3 lead(Mob mob, LivingEntity target, Vec3[] seen, int tick) {
        Vec3 now = seen[tick % seen.length];
        Vec3 before = seen[(tick + 1) % seen.length];
        if (now == null || before == null) return target.getEyePosition();
        Vec3 velocity = now.subtract(before).scale(1.0 / (seen.length - 1)).multiply(1, 0, 1);
        double flight = mob.getEyePosition().distanceTo(target.getEyePosition()) / CROW_SPEED;
        double ticks = Math.min(40, CAST_TICKS - tick + flight);
        return target.getEyePosition().add(velocity.scale(ticks));
    }

    /** Turns body, head and gaze towards {@code point}, at most {@link #TURN_SPEED} degrees per tick. */
    private static void face(Mob mob, Vec3 point) {
        double dx = point.x - mob.getX();
        double dz = point.z - mob.getZ();
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(mob.getYRot(), wanted, TURN_SPEED);
        float pitch = (float) -(Mth.atan2(point.y - mob.getEyeY(), Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setXRot(pitch);
        mob.getLookControl().setLookAt(point.x, point.y, point.z, 360, 90);
    }

    private static void finish(Mob mob) {
        Busy.end(mob);
        CASTING.remove(mob.getUUID());
        if (mob instanceof Leshen leshen) leshen.setCastPhase(0);
    }
}
