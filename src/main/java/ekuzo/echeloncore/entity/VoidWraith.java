package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The Void Wraith, a misty black and purple creature that comes with a fallen star (model: art/void_wraith.bbmodel).
 * It hovers at a distance from its target and fires a laser from the star core in its chest: the beam charges for
 * {@link #CHARGE_TICKS} (a thin line of purple light shows where it will hit, locked in place for the last
 * {@link #LOCK_TICKS}), then ignites along its whole length, through blocks, hurting every player in its path. The
 * beam is drawn on the client (VoidBeamRenderer) from {@link BeamPayload}s. It backs away from players chasing it,
 * and a stun while it charges makes it crash to the ground (see {@link #registerStunCrash}).
 */
public class VoidWraith extends Monster implements GeoEntity {
    public static final int CHARGE_TICKS = 34, LOCK_TICKS = 10;
    public static final double BEAM_LENGTH = 32, BEAM_WIDTH = 0.6;
    public static final float BEAM_DAMAGE = 8.0F;
    /** How fast the beam's aim follows its target, in blocks per tick. */
    private static final double AIM_SPEED = 0.55;
    /** Stunned while charging: it powers down, drops to the ground and stays stunned this long. */
    public static final int CRASH_TICKS = 60;
    private int crashTicks;
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(VoidWraith.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop("attack.charge");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public VoidWraith(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 15;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 45.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGING, false);
    }

    public boolean charging() { return entityData.get(CHARGING); }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LaserGoal());
        this.goalSelector.addGoal(2, new HoverAtDistanceGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) { return false; }

    /**
     * Stunned while charging its beam: the beam powers down, it drops to the ground and is stunned for exactly
     * {@link #CRASH_TICKS}, however long the stun was. Other stuns work as usual.
     */
    public static void registerStunCrash() {
        ekuzo.crazyworldprogression.fletching.Fletching.onStun((target, source) -> {
            if (target instanceof VoidWraith wraith && wraith.charging() && wraith.crashTicks <= 0) wraith.crash();
        });
    }

    private void crash() {
        crashTicks = CRASH_TICKS;
        entityData.set(CHARGING, false);
        setNoGravity(false);
        getNavigation().stop();
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2F, 0.7F);
        if (level() instanceof ServerLevel) beam(BeamPayload.CANCELED, position());
    }

    /** While crashed it stays grounded and stunned (its mist wisps are part of the model and its animations). */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && crashTicks > 0) {
            // Grounded and stunned for exactly the crash time; then it rises again.
            var stun = ekuzo.crazyworldprogression.fletching.Fletching.STUNNED;
            if (crashTicks == CRASH_TICKS || !hasEffect(stun))
                addEffect(new net.minecraft.world.effect.MobEffectInstance(stun, crashTicks));
            if (--crashTicks == 0) {
                removeEffect(stun);
                setNoGravity(true);
            }
        }
    }

    /** Where the beam starts: the star core in its chest. */
    public Vec3 core() {
        Vec3 forward = Vec3.directionFromRotation(0, yBodyRot).scale(0.25);
        return position().add(forward).add(0, getBbHeight() * 0.6, 0);
    }

    /** Tells everyone who can see this wraith what its beam is doing. */
    private void beam(int phase, Vec3 end) {
        if (!(level() instanceof ServerLevel)) return;
        BeamPayload payload = new BeamPayload(getId(), phase, (float) end.x, (float) end.y, (float) end.z);
        for (var player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(this))
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, BeamPayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }

    public static void registerPayload() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(BeamPayload.TYPE, BeamPayload.CODEC);
    }

    /**
     * The beam of wraith {@code entityId}: charging (sent every tick while the aim moves), locked (it will fire
     * there), fired, or canceled (the wraith was interrupted). x, y, z is the point the beam points through; it runs
     * {@link #BEAM_LENGTH} blocks from the wraith's core in that direction.
     */
    public record BeamPayload(int entityId, int phase, float x, float y, float z)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final int CHARGING = 0, LOCKED = 1, FIRED = 2, CANCELED = 3;
        public static final Type<BeamPayload> TYPE = new Type<>(ekuzo.echeloncore.EchelonCore.id("void_beam"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, BeamPayload> CODEC =
                net.minecraft.network.codec.StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, BeamPayload::entityId,
                        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, BeamPayload::phase,
                        net.minecraft.network.codec.ByteBufCodecs.FLOAT, BeamPayload::x,
                        net.minecraft.network.codec.ByteBufCodecs.FLOAT, BeamPayload::y,
                        net.minecraft.network.codec.ByteBufCodecs.FLOAT, BeamPayload::z, BeamPayload::new);

        @Override
        public Type<BeamPayload> type() { return TYPE; }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<VoidWraith>("main", 4, test -> {
                    if (test.animatable().charging()) return test.setAndContinue(CHARGE);
                    return test.setAndContinue(test.isMoving() ? DefaultAnimations.WALK : DefaultAnimations.IDLE);
                }),
                new AnimationController<VoidWraith>("abilities", test -> PlayState.STOP)
                        .triggerableAnim("fire", RawAnimation.begin().thenPlay("attack.fire")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    /** Keeps 8 to 14 blocks away from its target, a few blocks above it. */
    private final class HoverAtDistanceGoal extends Goal {
        HoverAtDistanceGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() { return getTarget() != null && getTarget().isAlive() && crashTicks <= 0; }

        /** Backs away fast from a chasing target (closer than 6 blocks); otherwise drifts to 8 to 14 blocks away. */
        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
            double distance = away.length();
            Vec3 direction = distance < 0.01 ? Vec3.directionFromRotation(0, getYRot() + 180) : away.scale(1 / distance);
            if (distance < 6 && tickCount % 4 == 0) {
                Vec3 spot = position().add(direction.scale(6)).add(0, 1.5, 0);
                getNavigation().moveTo(spot.x, spot.y, spot.z, 1.6);
                return;
            }
            if (tickCount % 10 != 0) return;
            if (distance > 7.5 && distance < 14.5 && Math.abs(getY() - (target.getY() + 3)) < 2) return;
            Vec3 spot = target.position().add(direction.scale(11)).add(0, 3, 0);
            getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }
    }

    /** Turns body, head and gaze toward a point at once (the beam leaves its chest in that direction). */
    private void face(Vec3 point) {
        Vec3 offset = point.subtract(position());
        float yaw = (float) (Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(point.y - getEyeY(), offset.horizontalDistance()));
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        setXRot(pitch);
        getLookControl().setLookAt(point.x, point.y, point.z, 90, 90);
    }

    /** Charge, lock, fire; then a pause of 5 to 7 seconds. */
    private final class LaserGoal extends Goal {
        private int cooldown = 60;
        private int ticks;
        /** The point the beam points through; it follows the target at a limited speed, so the beam sweeps smoothly. */
        private Vec3 aimPoint;

        /** It has to hold still to cast: the goal takes movement as well as looking. */
        LaserGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity target = getTarget();
            return crashTicks <= 0 && target != null && target.isAlive() && distanceTo(target) <= BEAM_LENGTH - 4;
        }

        @Override
        public boolean canContinueToUse() { return ticks <= CHARGE_TICKS && isAlive() && crashTicks <= 0; }

        @Override
        public void start() {
            ticks = 0;
            aimPoint = getTarget() == null ? null : getTarget().getEyePosition();
            getNavigation().stop();
            entityData.set(CHARGING, true);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.4F, 1.3F);
        }

        @Override
        public void stop() {
            entityData.set(CHARGING, false);
            cooldown = 100 + getRandom().nextInt(40);
            if (ticks < CHARGE_TICKS) beam(BeamPayload.CANCELED, Vec3.ZERO);
        }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            ticks++;
            LivingEntity target = getTarget();
            if (!(level() instanceof ServerLevel level)) return;
            // Holds completely still while casting, turned toward where the beam goes.
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            Vec3 core = core();
            if (aimPoint == null) return;
            if (ticks <= CHARGE_TICKS - LOCK_TICKS && target != null) {
                Vec3 wanted = target.getEyePosition().subtract(aimPoint);
                double step = Math.min(wanted.length(), AIM_SPEED);
                if (step > 1.0E-3) aimPoint = aimPoint.add(wanted.normalize().scale(step));
            }
            face(aimPoint);
            if (ticks < CHARGE_TICKS) {
                // While it still aims, the beam follows the target every tick; then it stays where it is.
                beam(ticks <= CHARGE_TICKS - LOCK_TICKS ? BeamPayload.CHARGING : BeamPayload.LOCKED, aimPoint);
                return;
            }
            fire(level, core);
        }

        private void fire(ServerLevel level, Vec3 core) {
            triggerAnim("abilities", "fire");
            Vec3 direction = aimPoint.subtract(core);
            if (direction.lengthSqr() < 1.0E-4) return;
            Vec3 end = core.add(direction.normalize().scale(BEAM_LENGTH));
            beam(BeamPayload.FIRED, aimPoint);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.6F, 1.4F);
            // Through blocks: everyone whose body the line crosses is hit.
            AABB reach = new AABB(core, end).inflate(BEAM_WIDTH + 1);
            for (Player player : level.getEntitiesOfClass(Player.class, reach, player -> player.isAlive() && !player.isSpectator())) {
                if (player.getBoundingBox().inflate(BEAM_WIDTH).clip(core, end).isPresent())
                    player.hurtServer(level, damageSources().sonicBoom(VoidWraith.this), BEAM_DAMAGE);
            }
        }
    }
}
