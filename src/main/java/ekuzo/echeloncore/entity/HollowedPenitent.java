package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.ability.PenitentAbilities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Hollowed Penitent, a world boss (model: assets/echelon-core/geckolib/models/entity/hollowed_penitent.geo.json,
 * source art/hollowed_penitent.bbmodel). Once a man; the void ate him from the face inwards and left a hole where his
 * face was. All he has left is sorrow: he keeps trying to cry and can't, and every failed attempt makes him angrier.
 *
 * <p>Built for three or four players but beatable solo by a very skilled one: the difficulty is in the mechanics
 * (telegraphs, interrupts, burst windows), there is no scaling by player count. His abilities are MobForge abilities
 * (see PenitentAbilities); the passives (the dripping void tar, the phases, Last Sorrow's pulses, Anguish) live here.
 * The synced {@link #action()} drives the animation of whatever he is doing.
 */
public class HollowedPenitent extends Monster implements GeoEntity {
    // ---------------------------------------------------------------- tuning
    // Roughly 3-3.5x the Leshen's effective health (420 health behind 12 armor and 4 toughness, against its 150 behind 6).
    public static final double MAX_HEALTH = 420.0;
    public static final double ATTACK_DAMAGE = 12.0;
    public static final double ARMOR = 12.0;
    public static final double ARMOR_TOUGHNESS = 4.0;
    public static final double KNOCKBACK_RESISTANCE = 0.95;
    public static final double SPEED = 0.26;
    /** Faceless Fury below this share of his health: the maw tears open, he moves 12% faster and attacks more often. */
    public static final float FURY_HEALTH = 0.5F;
    public static final double FURY_SPEED = 0.12;
    /** Last Sorrow below this share: a pulsing void shockwave every {@link #SORROW_INTERVAL} ticks. */
    public static final float SORROW_HEALTH = 0.2F;
    public static final int SORROW_INTERVAL = 80;
    /** Ticks of warning (the void gathering around him) before each pulse. */
    public static final int SORROW_CHARGE = 20;
    public static final double SORROW_RADIUS = 7.0;
    public static final float SORROW_DAMAGE = 5.0F;
    /** Weeping Void: a pool of void tar under him every {@link #WEEP_INTERVAL} ticks while he fights. */
    public static final int WEEP_INTERVAL = 120;
    public static final int POOL_TICKS = 100;
    public static final double POOL_RADIUS = 1.8;
    /** Anguish: each failed attempt to cry adds +10% damage and +5% speed, up to five stacks. */
    public static final int MAX_ANGUISH = 5;
    public static final double ANGUISH_DAMAGE = 0.10;
    public static final double ANGUISH_SPEED = 0.05;
    /** Damage he takes during Tearless Mourning (the group's burst window). */
    public static final float MOURNING_DAMAGE_TAKEN = 1.5F;
    /** Ticks he needs to die (the death animation: kneeling, reaching for the sky, slumping). */
    private static final int DEATH_TICKS = 60;

    // ---------------------------------------------------------------- what he is doing (drives the animation)
    public static final int NONE = 0;
    public static final int REND = 1;
    public static final int WAIL = 2;
    public static final int MOURNING = 3;
    /** The snap out of a failed Mourning: he rears up and roars without a voice (a new Anguish stack). */
    public static final int ANGUISH = 4;
    /** Grasp of the Lost, the telegraph: he reaches out. */
    public static final int REACH = 5;
    public static final int HOLD = 6;
    public static final int LUNGE = 7;
    public static final int STAGGER = 8;
    /** Entering Faceless Fury: the maw splits open. */
    public static final int MAW_OPEN = 9;

    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(HollowedPenitent.class, EntityDataSerializers.INT);
    /** 0 normal, 1 Faceless Fury, 2 Last Sorrow. */
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(HollowedPenitent.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANGUISH_STACKS = SynchedEntityData.defineId(HollowedPenitent.class, EntityDataSerializers.INT);
    private static final Identifier FURY_BOOST = EchelonCore.id("hollowed_penitent_fury");
    private static final Identifier ANGUISH_BOOST = EchelonCore.id("hollowed_penitent_anguish");
    /** The void tar dripping from his face. */
    private static final DustParticleOptions TAR = new DustParticleOptions(0x1A0A24, 1.4F);
    private static final DustParticleOptions ANGUISH_GLOW = new DustParticleOptions(0x7A1030, 1.1F);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("move.run");
    private static final RawAnimation FLECKS = RawAnimation.begin().thenLoop("misc.flecks");
    private static final RawAnimation MAW = RawAnimation.begin().thenLoop("misc.maw_open");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("misc.death");
    private static final RawAnimation[] ACTIONS = {
            null,
            RawAnimation.begin().thenPlay("attack.grief_rend"),
            RawAnimation.begin().thenPlayAndHold("attack.wail"),
            RawAnimation.begin().thenPlay("attack.mourning_kneel").thenLoop("attack.mourning"),
            RawAnimation.begin().thenPlay("attack.anguish"),
            RawAnimation.begin().thenPlayAndHold("attack.reach"),
            RawAnimation.begin().thenLoop("attack.hold"),
            RawAnimation.begin().thenPlay("attack.lunge"),
            RawAnimation.begin().thenPlay("misc.stagger"),
            RawAnimation.begin().thenPlay("misc.maw_split"),
    };

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private int weepTicks;
    private int sorrowTicks;

    public HollowedPenitent(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 150;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true) {
            /** Faceless Fury: he swings a third more often. */
            @Override
            protected int getAttackInterval() { return adjustedTickDelay(phase() >= 1 ? 14 : 20); }
        });
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, NONE);
        builder.define(PHASE, 0);
        builder.define(ANGUISH_STACKS, 0);
    }

    public int action() { return this.entityData.get(ACTION); }

    public void setAction(int action) { this.entityData.set(ACTION, action); }

    public int phase() { return this.entityData.get(PHASE); }

    public int anguish() { return this.entityData.get(ANGUISH_STACKS); }

    /** Adds (or with a negative {@code change} removes) Anguish stacks and updates his damage and speed. */
    public void addAnguish(int change) {
        int stacks = Mth.clamp(anguish() + change, 0, MAX_ANGUISH);
        this.entityData.set(ANGUISH_STACKS, stacks);
        applyAnguish(stacks);
    }

    private void applyAnguish(int stacks) {
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (damage != null) damage.removeModifier(ANGUISH_BOOST);
        if (speed != null) speed.removeModifier(ANGUISH_BOOST);
        if (stacks == 0) return;
        if (damage != null) damage.addPermanentModifier(new AttributeModifier(ANGUISH_BOOST, ANGUISH_DAMAGE * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        if (speed != null) speed.addPermanentModifier(new AttributeModifier(ANGUISH_BOOST, ANGUISH_SPEED * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** Where the void tar drips from: the hole that was his face. */
    public Vec3 maw() {
        return getEyePosition().add(Vec3.directionFromRotation(0, getYRot()).scale(0.55)).add(0, -0.15, 0);
    }

    /** Stuns never take hold; a stun still interrupts the Wail, Mourning and the Grasp (see PenitentAbilities). */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(ekuzo.crazyworldprogression.fletching.Fletching.STUNNED) && super.canBeAffected(effect);
    }

    /** Tearless Mourning leaves him open: he takes {@link #MOURNING_DAMAGE_TAKEN} times the damage. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (action() == MOURNING) amount *= MOURNING_DAMAGE_TAKEN;
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            if (!isAlive()) return;
            tickPhases(level);
            tickWeeping(level);
            tickSorrow(level);
            if (anguish() > 0 && tickCount % 10 == 0)
                level.sendParticles(ANGUISH_GLOW, getX(), getY() + getBbHeight() * 0.75, getZ(), anguish() * 3, 0.5, 0.4, 0.5, 0.01);
        }
        // While an ability holds him in place the client would never turn his body: keep body and head on his facing.
        else if (action() != NONE) {
            setYBodyRot(getYRot());
            setYHeadRot(getYRot());
        }
    }

    /** Faceless Fury below half his health, Last Sorrow below a fifth. */
    private void tickPhases(ServerLevel level) {
        float health = getHealth() / getMaxHealth();
        if (phase() == 0 && health <= FURY_HEALTH) {
            this.entityData.set(PHASE, 1);
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.addOrReplacePermanentModifier(new AttributeModifier(FURY_BOOST, FURY_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            // The maw tears wider: a wet ripping and a voiceless heave, no roar.
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.5F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SCULK_BLOCK_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
            Vec3 maw = maw();
            level.sendParticles(TAR, maw.x, maw.y, maw.z, 60, 0.4, 0.4, 0.4, 0.05);
            level.sendParticles(ParticleTypes.SQUID_INK, maw.x, maw.y, maw.z, 30, 0.3, 0.3, 0.3, 0.08);
            PenitentAbilities.transition(level, this, MAW_OPEN, 40);
        } else if (phase() == 1 && health <= SORROW_HEALTH) {
            this.entityData.set(PHASE, 2);
            this.sorrowTicks = 0;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0F, 0.5F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 80, 1.0, 1.2, 1.0, 0.1);
        }
    }

    /**
     * Weeping Void: tar drips from his face all the time; while he fights, it leaves a pool under him now and then
     * that withers and slows whoever stands in it for {@link #POOL_TICKS} ticks. The pools are no blocks, only an area.
     */
    private void tickWeeping(ServerLevel level) {
        if (tickCount % 4 == 0) {
            Vec3 maw = maw();
            level.sendParticles(TAR, maw.x, maw.y, maw.z, 1, 0.08, 0.02, 0.08, 0);
            level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, maw.x, maw.y - 0.1, maw.z, 1, 0.05, 0, 0.05, 0);
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        if (++weepTicks < (phase() >= 1 ? WEEP_INTERVAL * 2 / 3 : WEEP_INTERVAL)) return;
        weepTicks = 0;
        if (onGround()) pool(level, position());
    }

    private void pool(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.HOSTILE, 1.2F, 0.4F);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (++ticks[0] > POOL_TICKS) return true;
            if (ticks[0] % 4 == 0) {
                level.sendParticles(TAR, at.x, at.y + 0.05, at.z, 10, POOL_RADIUS * 0.5, 0.01, POOL_RADIUS * 0.5, 0);
                level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.1, at.z, 2, POOL_RADIUS * 0.4, 0.02, POOL_RADIUS * 0.4, 0.005);
            }
            if (ticks[0] % 10 != 0) return false;
            for (Player player : level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(POOL_RADIUS, 1.5, POOL_RADIUS),
                    player -> player.isAlive() && !player.isSpectator() && player.position().multiply(1, 0, 1)
                            .distanceTo(at.multiply(1, 0, 1)) <= POOL_RADIUS)) {
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0));
            }
            return false;
        });
    }

    /** Last Sorrow: the void gathers around him and bursts outwards every few seconds; step out of it in time. */
    private void tickSorrow(ServerLevel level) {
        if (phase() < 2) return;
        sorrowTicks++;
        if (sorrowTicks == SORROW_INTERVAL - SORROW_CHARGE)
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0F, 0.5F);
        if (sorrowTicks > SORROW_INTERVAL - SORROW_CHARGE && sorrowTicks < SORROW_INTERVAL && sorrowTicks % 3 == 0)
            PenitentAbilities.ring(level, position(), SORROW_RADIUS, ParticleTypes.REVERSE_PORTAL, 24);
        if (sorrowTicks < SORROW_INTERVAL) return;
        sorrowTicks = 0;
        PenitentAbilities.shockwave(level, this, position(), SORROW_RADIUS, SORROW_DAMAGE, 0.7, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.6F, 0.4F);
    }

    // ---------------------------------------------------------------- saving, death

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Anguish", anguish());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(ANGUISH_STACKS, Mth.clamp(input.getIntOr("Anguish", 0), 0, MAX_ANGUISH));
        applyAnguish(anguish());
    }

    /** He takes three seconds to die: kneels, reaches up towards the sky, slumps. */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (level() instanceof ServerLevel level && this.deathTime % 5 == 0)
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.0, getZ(), 2, 0.3, 0.4, 0.3, 0.02);
        if (this.deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        setAction(NONE);
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_LISTENING; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WARDEN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }
    @Override public float getVoicePitch() { return 0.55F + getRandom().nextFloat() * 0.1F; }

    // ---------------------------------------------------------------- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<HollowedPenitent>("Walk/Idle", 5, test -> {
                    HollowedPenitent penitent = test.animatable();
                    if (penitent.action() != NONE || penitent.isDeadOrDying()) return PlayState.STOP;
                    if (!test.isMoving()) return test.setAndContinue(IDLE);
                    return test.setAndContinue(penitent.isAggressive() && penitent.phase() >= 1 || penitent.anguish() >= 3 ? RUN : WALK);
                }),
                DefaultAnimations.genericAttackAnimation(DefaultAnimations.ATTACK_SWING),
                new AnimationController<HollowedPenitent>("actions", 3, test -> {
                    HollowedPenitent penitent = test.animatable();
                    if (penitent.isDeadOrDying()) return test.setAndContinue(DEATH);
                    int action = penitent.action();
                    if (action <= NONE || action >= ACTIONS.length) return PlayState.STOP;
                    // Grief Rend swings a quarter faster in Faceless Fury (see GriefRend).
                    test.setControllerSpeed(action == REND && penitent.phase() >= 1 ? 1.0F / 0.75F : 1.0F);
                    return test.setAndContinue(ACTIONS[action]);
                }),
                new AnimationController<HollowedPenitent>("maw", 10, test ->
                        test.animatable().phase() >= 1 ? test.setAndContinue(MAW) : PlayState.STOP),
                new AnimationController<HollowedPenitent>("flecks", test -> test.setAndContinue(FLECKS)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
