package ekuzo.echeloncore.entity.ability;

import com.geckolib.animatable.GeoEntity;
import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.Falsekin;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tongue Lash: a snake-like tongue shoots out up to {@link #RANGE} blocks, hooks the target and drags them to the
 * caster's jaws (half its attack damage, slowed for 2 seconds).
 *
 * <p>Counterplay: the caster hisses and opens its jaws for {@link #WIND_UP} ticks first, and stops turning halfway
 * through ({@link #AIM_LOCK}). The tongue then flies straight along that locked line, so stepping sideways or breaking
 * line of sight dodges it. A raised shield facing the caster blocks it: no damage, no pull, and the caster recoils,
 * stunned, for 1.5 seconds.
 */
public final class TongueLash implements MobAbility {
    public static final Identifier ID = EchelonCore.id("tongue_lash");
    private static final double RANGE = 12.0;
    private static final int WIND_UP = 12;
    private static final int AIM_LOCK = 6;
    private static final int STUN = 30;
    private static final DustParticleOptions TONGUE = new DustParticleOptions(0x9A2A55, 1.0F);

    @Override public long cooldownTicks() { return 180; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        if (!FalsekinAbilities.ready(caster) || !(context.target() instanceof LivingEntity target) || !target.isAlive()) return false;
        Mob mob = (Mob) caster;
        float distance = mob.distanceTo(target);
        return distance >= 4.0F && distance <= RANGE && mob.hasLineOfSight(target);
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        Busy.start(mob);
        if (mob instanceof GeoEntity geo) geo.triggerAnim("abilities", "lash");
        // The tell: a low hiss as the jaws open.
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), Falsekin.soundById("entity.cat.hiss"),
                SoundSource.HOSTILE, 1.4F, 0.5F);
        int[] ticks = {0};
        Vec3[] aim = {null};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                Busy.end(mob);
                return true;
            }
            Busy.holdStill(mob);
            ticks[0]++;
            if (ticks[0] <= AIM_LOCK && target.isAlive()) {
                float yaw = Falsekin.yawTowards(mob, target);
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                mob.setYHeadRot(yaw);
                if (ticks[0] == AIM_LOCK)
                    aim[0] = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(mouth(mob)).normalize();
            }
            Vec3 mouth = mouth(mob);
            level.sendParticles(Falsekin.goo(), mouth.x, mouth.y, mouth.z, 2, 0.1, 0.05, 0.1, 0);
            if (ticks[0] < WIND_UP) return false;
            Busy.end(mob);
            if (aim[0] != null) lash(level, mob, target, mouth, aim[0]);
            return true;
        });
    }

    private static Vec3 mouth(Mob mob) {
        return mob.getEyePosition().add(Vec3.directionFromRotation(0, mob.getYRot()).scale(mob.getBbWidth() / 2 + 0.3));
    }

    /** The tongue flies along the locked aim; it only hooks the target if they are still on that line. */
    private static void lash(ServerLevel level, Mob mob, LivingEntity target, Vec3 mouth, Vec3 aim) {
        Vec3 end = mouth.add(aim.scale(RANGE));
        HitResult wall = level.clip(new ClipContext(mouth, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        var hit = target.isAlive() ? target.getBoundingBox().inflate(0.3).clip(mouth, end) : java.util.Optional.<Vec3>empty();
        Vec3 tip = hit.orElse(end);
        Vec3 step = tip.subtract(mouth);
        int points = Math.max(1, (int) (step.length() * 3));
        for (int k = 0; k <= points; k++) {
            Vec3 p = mouth.add(step.scale(k / (double) points));
            level.sendParticles(TONGUE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), Falsekin.soundById("entity.frog.tongue"),
                SoundSource.HOSTILE, 1.5F, 0.5F);
        if (hit.isEmpty()) return;                                    // dodged
        if (target.isBlocking() && facing(target, mob)) {
            // Blocked: the tongue glances off the shield and the creature recoils.
            level.playSound(null, target.getX(), target.getY(), target.getZ(), Falsekin.soundById("item.shield.block"),
                    SoundSource.PLAYERS, 1.0F, 0.8F);
            stun(mob);
            return;
        }
        target.hurtServer(level, level.damageSources().mobAttack(mob), (float) (mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5));
        Vec3 pull = mob.position().subtract(target.position()).normalize().scale(Math.min(2.2, mob.distanceTo(target) * 0.2));
        target.push(pull.x, 0.35, pull.z);
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
    }

    private static boolean facing(LivingEntity target, Mob mob) {
        Vec3 toMob = mob.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        return target.getViewVector(1.0F).multiply(1, 0, 1).normalize().dot(toMob) > 0.5;
    }

    private static void stun(Mob mob) {
        Busy.start(mob);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive() || ++ticks[0] > STUN) {
                Busy.end(mob);
                return true;
            }
            Busy.holdStill(mob);
            return false;
        });
    }
}
