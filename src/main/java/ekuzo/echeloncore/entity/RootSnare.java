package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import ekuzo.echeloncore.EchelonCore;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A ring of roots breaking out of the ground (Root Snare ability). While the roots rise, everyone inside is slowed;
 * whoever is still inside when they close is rooted: they can't walk or jump for {@link #HOLD_TICKS}.
 */
public class RootSnare extends Entity implements GeoEntity {
    public static final double RADIUS = 2.4;
    public static final int RISE_TICKS = 30;
    public static final int HOLD_TICKS = 60;
    private static final int SINK_TICKS = 14;
    private static final float DAMAGE = 3.0F;
    private static final Identifier ROOTED = EchelonCore.id("rooted");
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RootSnare.class, EntityDataSerializers.INT);
    private static final int RISING = 0, HOLDING = 1, SINKING = 2;
    private static final RawAnimation RISE = RawAnimation.begin().thenPlayAndHold("misc.spawn");
    private static final RawAnimation GRIP = RawAnimation.begin().thenLoop("misc.idle");
    private static final RawAnimation SINK = RawAnimation.begin().thenPlayAndHold("misc.die");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final List<Integer> rooted = new ArrayList<>();
    private UUID owner;
    private int stateTicks;

    public RootSnare(EntityType<? extends RootSnare> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** Raises a snare under {@code position} on behalf of {@code owner}. */
    public static void raise(ServerLevel level, Mob owner, Vec3 position) {
        RootSnare snare = new RootSnare(EchelonEntities.ROOT_SNARE, level);
        snare.owner = owner.getUUID();
        snare.setPos(position);
        level.addFreshEntity(snare);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, RISING);
    }

    private int state() { return this.entityData.get(STATE); }

    private void setState(int state) {
        this.entityData.set(STATE, state);
        this.stateTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        stateTicks++;
        switch (state()) {
            case RISING -> {
                for (LivingEntity victim : inside(level))
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 2, false, false, true));
                if (stateTicks % 3 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
                        getX(), getY() + 0.2, getZ(), 12, RADIUS * 0.6, 0.1, RADIUS * 0.6, 0.1);
                if (stateTicks >= RISE_TICKS) close(level);
            }
            case HOLDING -> {
                for (int id : rooted) if (level.getEntity(id) instanceof LivingEntity victim) victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
                if (stateTicks >= HOLD_TICKS) release(level);
            }
            default -> {
                if (stateTicks >= SINK_TICKS) discard();
            }
        }
    }

    /** The roots close: everyone still inside is rooted and hurt; with nobody inside the roots sink again. */
    private void close(ServerLevel level) {
        Entity source = owner == null ? null : level.getEntity(owner);
        DamageSource damage = source != null ? level.damageSources().indirectMagic(this, source) : level.damageSources().magic();
        for (LivingEntity victim : inside(level)) {
            rooted.add(victim.getId());
            root(victim, true);
            victim.hurtServer(level, damage, DAMAGE);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_PLACE, SoundSource.HOSTILE, 2.0F, 0.5F);
        if (rooted.isEmpty()) setState(SINKING);
        else setState(HOLDING);
    }

    private void release(ServerLevel level) {
        for (int id : rooted) if (level.getEntity(id) instanceof LivingEntity victim) root(victim, false);
        rooted.clear();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.2F, 0.8F);
        setState(SINKING);
    }

    private static void root(LivingEntity victim, boolean on) {
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = victim.getAttribute(attribute);
            if (instance == null) continue;
            instance.removeModifier(ROOTED);
            if (on) instance.addTransientModifier(new AttributeModifier(ROOTED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Living things inside the ring, except the caster and its summons. */
    private List<LivingEntity> inside(ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(RADIUS, 2.0, RADIUS),
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.getUUID().equals(owner) && !(entity instanceof ShadowWolf)
                        && entity.position().subtract(position()).horizontalDistance() <= RADIUS);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) for (int id : rooted) if (level.getEntity(id) instanceof LivingEntity victim) root(victim, false);
        super.remove(reason);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) { }
    @Override protected void addAdditionalSaveData(ValueOutput output) { }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("roots", 2, test -> test.setAndContinue(switch (state()) {
            case RISING -> RISE;
            case HOLDING -> GRIP;
            default -> SINK;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.geoCache; }
}
