package ekuzo.echeloncore.entity.ability;

import com.geckolib.animatable.GeoEntity;
import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.ShadowWolf;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ground Slam: the caster raises its arms and smashes the ground in front of it, hurting and throwing back everyone
 * within {@link #RADIUS} blocks of the impact. The Leshen also uses it right after some of its Shadow Steps.
 */
public final class GroundSlam implements MobAbility {
    public static final Identifier ID = EchelonCore.id("ground_slam");
    private static final int WIND_UP = 14;
    private static final double RADIUS = 3.2;

    @Override public long cooldownTicks() { return 200; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return caster instanceof Mob mob && !Busy.is(mob) && context.target() instanceof LivingEntity target
                && target.isAlive() && mob.distanceTo(target) <= 4.0F;
    }

    @Override public void execute(MobAbilityContext context) {
        perform(context.world(), (Mob) context.world().getEntity(context.caster().uuid()), (LivingEntity) context.target());
    }

    /** Winds up facing {@code target}, then slams. */
    public static void perform(ServerLevel level, Mob mob, LivingEntity target) {
        Busy.start(mob);
        if (mob instanceof GeoEntity geo) geo.triggerAnim("abilities", "slam");
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.2F, 0.6F);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                Busy.end(mob);
                return true;
            }
            Busy.holdStill(mob);
            if (ticks[0] < WIND_UP / 2 && target.isAlive()) {
                float yaw = (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90.0F;
                mob.setYRot(yaw);
                mob.setYBodyRot(yaw);
                mob.setYHeadRot(yaw);
            }
            if (++ticks[0] < WIND_UP) return false;
            Busy.end(mob);
            slam(level, mob);
            return true;
        });
    }

    private static void slam(ServerLevel level, Mob mob) {
        Vec3 impact = mob.position().add(Vec3.directionFromRotation(0, mob.getYRot()).scale(mob.getBbWidth() / 2 + 1.2));
        double damage = mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(RADIUS, 2, RADIUS),
                entity -> entity != mob && entity.isAlive() && !(entity instanceof ShadowWolf)
                        && entity.position().distanceTo(impact) <= RADIUS)) {
            victim.hurtServer(level, level.damageSources().mobAttack(mob), (float) damage);
            Vec3 push = victim.position().subtract(impact).multiply(1, 0, 1).normalize().scale(0.9);
            victim.push(push.x, 0.55, push.z);
            victim.hurtMarked = true;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
                impact.x, impact.y + 0.1, impact.z, 60, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.2);
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.3, impact.z, 2, 0.5, 0.1, 0.5, 0);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 0.6F);
    }
}
