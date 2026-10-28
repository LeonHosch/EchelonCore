package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * A wave of crows flying in a straight line (Crow Swarm ability). On hitting a living entity it clings to it, follows
 * it wherever it goes and deals {@link #DAMAGE} once a second, {@link #HITS} times, then scatters. A wave that misses
 * (flies past where it was aimed, or hits a wall) circles there for {@link #LINGER_TICKS} and latches onto anyone who
 * walks into it.
 */
public class CrowSwarm extends Entity implements GeoEntity {
    public static final float DAMAGE = 3.0F;
    public static final int HITS = 3;
    public static final int LINGER_TICKS = 60;
    private static final double SPEED = 0.7;
    private static final double RANGE = 30;
    private static final double LINGER_RADIUS = 1.8;
    private static final int FLYING = 0, ATTACHED = 1, LINGERING = 2;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(CrowSwarm.class, EntityDataSerializers.INT);
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");
    private static final RawAnimation SWARM = RawAnimation.begin().thenLoop("misc.swarm");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private Vec3 direction = Vec3.ZERO;
    private double travelled;
    private double aimDistance = RANGE;
    private int victim = -1;
    private int stateTicks;
    private int hits;

    public CrowSwarm(EntityType<? extends CrowSwarm> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** Sends a swarm from {@code from} at {@code aim} on behalf of {@code owner}; it lingers if it passes the aim point. */
    public static void launch(ServerLevel level, Mob owner, Vec3 from, Vec3 aim) {
        CrowSwarm swarm = new CrowSwarm(EchelonEntities.CROW_SWARM, level);
        swarm.owner = owner.getUUID();
        swarm.direction = aim.subtract(from).normalize();
        swarm.aimDistance = Math.min(RANGE, aim.distanceTo(from));
        swarm.setPos(from);
        swarm.setYRot((float) (Mth.atan2(swarm.direction.z, swarm.direction.x) * Mth.RAD_TO_DEG) - 90.0F);
        level.addFreshEntity(swarm);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, FLYING);
    }

    private int state() { return this.entityData.get(STATE); }

    private void setState(int state) {
        this.entityData.set(STATE, state);
        this.stateTicks = 0;
    }

    public boolean attached() { return state() == ATTACHED; }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        stateTicks++;
        switch (state()) {
            case FLYING -> fly(level);
            case ATTACHED -> cling(level);
            default -> linger(level);
        }
    }

    private void fly(ServerLevel level) {
        Vec3 next = position().add(direction.scale(SPEED));
        if (level.clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
                != HitResult.Type.MISS) {
            setState(LINGERING);
            return;
        }
        LivingEntity hit = first(level, getBoundingBox().expandTowards(direction.scale(SPEED)).inflate(0.6));
        if (hit != null) {
            attach(level, hit);
            return;
        }
        setPos(next);
        if (tickCount % 2 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 2, 0.4, 0.3, 0.4, 0.0);
        if (tickCount % 5 == 1) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PARROT_FLY, SoundSource.HOSTILE, 1.2F,
                0.8F + random.nextFloat() * 0.3F);                    // wing beats of the flock
        travelled += SPEED;
        if (travelled >= aimDistance + 1.5) setState(LINGERING);
    }

    private void linger(ServerLevel level) {
        LivingEntity hit = first(level, new AABB(position(), position()).inflate(LINGER_RADIUS, 1.5, LINGER_RADIUS));
        if (hit != null) {
            attach(level, hit);
            return;
        }
        if (stateTicks % 10 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PARROT_FLY, SoundSource.HOSTILE,
                0.8F, 0.9F + random.nextFloat() * 0.2F);
        if (stateTicks % 4 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 2, 0.8, 0.4, 0.8, 0.0);
        if (stateTicks >= LINGER_TICKS) scatter(level);
    }

    private void attach(ServerLevel level, LivingEntity hit) {
        setState(ATTACHED);
        this.victim = hit.getId();
        level.playSound(null, hit.getX(), hit.getY(), hit.getZ(), SoundEvents.PARROT_HURT, SoundSource.HOSTILE, 1.2F, 0.6F);
    }

    /** The first living entity in the box that isn't the caster or one of its wolves. */
    private LivingEntity first(ServerLevel level, AABB box) {
        List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive() && !entity.isSpectator()
                && !entity.getUUID().equals(owner) && !(entity instanceof ShadowWolf));
        return hits.isEmpty() ? null : hits.getFirst();
    }

    private void cling(ServerLevel level) {
        if (!(level.getEntity(victim) instanceof LivingEntity target) || !target.isAlive()) {
            scatter(level);
            return;
        }
        setPos(target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ());
        if (stateTicks % 10 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.PARROT_FLY, SoundSource.HOSTILE,
                0.9F, 0.9F + random.nextFloat() * 0.2F);
        if (stateTicks % 20 != 0) return;
        Entity source = owner == null ? null : level.getEntity(owner);
        DamageSource damage = source != null ? level.damageSources().indirectMagic(this, source) : level.damageSources().magic();
        target.hurtServer(level, damage, DAMAGE);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 3, 0.3, 0.2, 0.3, 0.1);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PARROT_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.5F);
        if (++hits >= HITS) scatter(level);
    }

    private void scatter(ServerLevel level) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 15, 0.6, 0.5, 0.6, 0.05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PARROT_FLY, SoundSource.HOSTILE, 1.0F, 1.2F);
        discard();
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) { }
    @Override protected void addAdditionalSaveData(ValueOutput output) { }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("crows", 4, test -> test.setAndContinue(state() == FLYING ? FLY : SWARM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.geoCache; }
}
