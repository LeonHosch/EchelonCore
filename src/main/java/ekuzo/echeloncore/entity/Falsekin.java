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
import ekuzo.echeloncore.entity.ability.ShadowStep;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The Falsekin, a demon that wears the skins of other creatures (model: geckolib/models/entity/falsekin.geo.json,
 * source art/falsekin.bbmodel).
 *
 * <p><b>Haunting.</b> Each Falsekin hunts one player chosen beforehand ({@link FalsekinHaunts}). It appears near that
 * player as a mob that fits the place, preferably inside an existing herd, and copies what that kind of mob does.
 * When nobody is looking it silently teleports to another herd and becomes one of them. If its player leaves it behind
 * (more than {@link #LEASH} blocks) it despawns and its progress pauses until it appears again.
 *
 * <p><b>Power.</b> While its player is within {@link #STALK_RANGE} blocks it gains power ({@link #MAX_POWER} ticks
 * for full) and its act gets weirder in four stages: (0) a perfect copy; (1) it stares at its player and its voice
 * is slightly off; (2) it follows its player, freezes when watched, walks backwards and makes other animals' sounds;
 * (3) it creeps closer whenever unseen, twitches, stutters and imitates players. At full power it snaps and attacks.
 *
 * <p><b>Found out.</b> Hit in disguise it vanishes with a scream and comes back in its true form, as strong as the
 * power it built (up to 2.5x health, 2x damage, +30% speed, +6 armor). In stage 3 it answers the first two hits
 * with a Mass Decoy instead ({@link FalsekinMassDecoy}). In its true form it moves 40% faster while its player isn't
 * looking and uses its abilities (see EchelonAbilities). Now and then it rears up and walks on two legs for a few
 * seconds.
 *
 * <p><b>Disengaging.</b> When its player is in a divine field (CWP) or with another player, it gives up and leaves
 * (stalking or fighting); the haunt waits up to 3 minutes for the player to be alone again.
 *
 * <p>Decoys are Falsekins too: shed skins (Skin Shed) that run at players and burst, the fake herd members of a
 * Mass Decoy, and the staring villagers of the rare stage-3 Watchers scare ({@link FalsekinWatchers}).
 */
public class Falsekin extends Monster implements GeoEntity {
    public static final int MAX_POWER = 6_000;
    public static final double STALK_RANGE = 48.0;
    public static final double LEASH = 64.0;
    private static final EntityDataAccessor<String> DISGUISE = SynchedEntityData.defineId(Falsekin.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> TWITCH = SynchedEntityData.defineId(Falsekin.class, EntityDataSerializers.BOOLEAN);
    /** True form, now and then: it rears up and walks on two legs for a few seconds. */
    private static final EntityDataAccessor<Boolean> UPRIGHT = SynchedEntityData.defineId(Falsekin.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation UPRIGHT_IDLE = RawAnimation.begin().thenLoop("misc.upright_idle");
    private static final RawAnimation UPRIGHT_WALK = RawAnimation.begin().thenLoop("move.upright_walk");
    private static final Identifier POWER_BOOST = EchelonCore.id("falsekin_power");
    private static final Identifier UNSEEN_BOOST = EchelonCore.id("falsekin_unseen");
    /** Damage of an exploding fake at point blank (60% of it at the edge). */
    private static final float EXPLOSION_DAMAGE = 8.0F;
    private static final int UPRIGHT_CHANCE = 800;
    private static final DustParticleOptions GOO = new DustParticleOptions(0x3A1C52, 1.6F);

    /**
     * What a decoy is: a shed skin that charges and bursts, a herd member of a Mass Decoy, an exploding fake, or one
     * of the staring villagers of {@link FalsekinWatchers}.
     */
    private enum Decoy { NONE, CHARGER, HERD, EXPLODER, WATCHER }

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private UUID hauntTarget;
    private Decoy decoy = Decoy.NONE;
    private UUID herdGroup;
    private boolean revealed;
    private boolean hiding;
    private boolean leaving;
    private int leaveTicks;
    private int stunTicks;
    private final List<String> worn = new ArrayList<>();
    private EntityType<?> disguiseType;
    private String disguiseCache = "";
    private int nextShift = 600;
    private int stareTicks;
    private int vanishTicks;
    private LivingEntity vanishTarget;
    private int snapTicks = -1;
    private int twitchTicks;
    private int creepCooldown = 300;
    private int life = 240;
    private boolean redEyes;
    private Player watched;
    private int watchersCooldown = FalsekinWatchers.COOLDOWN / 2;
    private int uprightTicks;
    /** Its player died: it runs off without a haunt and is gone. */
    private boolean orphaned;
    private Vec3 fleeFrom;

    public Falsekin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override public boolean canUse() { return fighting() && super.canUse(); }
            @Override public boolean canContinueToUse() { return fighting() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(3, new ImitateGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return decoy != Decoy.CHARGER && decoy != Decoy.EXPLODER && decoy != Decoy.WATCHER
                    && stareTicks <= 0
                    && snapTicks < 0 && !leaving && stunTicks == 0 && super.canUse(); }
        });
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return decoy != Decoy.WATCHER && super.canUse(); }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (target, level) -> decoy == Decoy.CHARGER || fighting() && target.getUUID().equals(hauntTarget)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISGUISE, "");
        builder.define(TWITCH, false);
        builder.define(UPRIGHT, false);
    }

    // ---------------------------------------------------------------- state

    /** The mob type it looks like, or null in its true form. */
    public EntityType<?> disguise() {
        String id = this.entityData.get(DISGUISE);
        if (!id.equals(disguiseCache)) {
            disguiseCache = id;
            disguiseType = id.isEmpty() ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).orElse(null);
        }
        return disguiseType;
    }

    public boolean twitching() { return this.entityData.get(TWITCH); }

    public boolean upright() { return this.entityData.get(UPRIGHT); }

    public boolean revealed() { return revealed; }

    public boolean decoy() { return decoy != Decoy.NONE; }

    /** In its true form and free to fight: not hiding in a herd, stunned or leaving. */
    public boolean fighting() { return revealed && !hiding && !leaving && stunTicks == 0 && decoy == Decoy.NONE; }

    /** True while it can't start an ability: hiding, stunned, leaving, or not revealed. */
    public boolean busy() { return !fighting(); }

    public UUID hauntTarget() { return hauntTarget; }

    UUID herdGroup() { return herdGroup; }

    private FalsekinHaunts.Haunt haunt() {
        return hauntTarget == null || !(level() instanceof ServerLevel level) ? null
                : FalsekinHaunts.get(level.getServer()).haunt(hauntTarget);
    }

    /** 0 to 1: how much power it built up unnoticed. */
    public float powerFraction() {
        FalsekinHaunts.Haunt haunt = haunt();
        return haunt == null ? 0.0F : Math.min(1.0F, haunt.power / (float) MAX_POWER);
    }

    /** How weird its act is: 0 (perfect copy) to 3 (about to snap). */
    public int stage() { return Math.min(3, (int) (powerFraction() * 4)); }

    /** The mob kinds it has worn, for its shed skins. */
    public List<String> worn() { return List.copyOf(worn); }

    private void wear(String path) {
        this.entityData.set(DISGUISE, "minecraft:" + path);
        if (!worn.contains(path)) worn.add(path);
    }

    /** Called by the haunt when it appears: whom it hunts and what it looks like. */
    void haunting(UUID target, String kind) {
        hauntTarget = target;
        wear(kind);
    }

    /** Turns this into a shed skin: a fragile copy of {@code path} that runs at players and bursts. */
    public void makeDecoy(String path) {
        decoy = Decoy.CHARGER;
        this.entityData.set(DISGUISE, "minecraft:" + path);
        fragile(0.34);
    }

    /** A fake member of a Mass Decoy herd. */
    void makeHerdDecoy(String path, UUID group) {
        decoy = Decoy.HERD;
        herdGroup = group;
        this.entityData.set(DISGUISE, "minecraft:" + path);
        fragile(0.25);
    }

    private void fragile(double speed) {
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(4.0);
        setHealth(4.0F);
        var movement = getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(speed);
    }

    /** One of the Watchers: a villager that stands still and stares at {@code target}; {@code red} gets glowing eyes. */
    void makeWatcher(Player target, boolean red, int lifetime) {
        decoy = Decoy.WATCHER;
        watched = target;
        redEyes = red;
        life = lifetime;
        this.entityData.set(DISGUISE, "minecraft:villager");
        fragile(0.0);
        // No pathfinding, no sounds: they just stand there.
        setNoAi(true);
        setSilent(true);
    }

    /** The real Falsekin joins its Mass Decoy herd, looking like the rest. */
    void hideInHerd(String kind, UUID group) {
        herdGroup = group;
        if (revealed) hiding = true;
        setTarget(null);
        getNavigation().stop();
        wear(kind);
    }

    /** The herd is over: still stalking it keeps the disguise, in combat it shows its true form again. */
    void leaveHerd(ServerLevel level, boolean stalking) {
        herdGroup = null;
        if (!stalking && revealed) {
            hiding = false;
            this.entityData.set(DISGUISE, "");
            level.sendParticles(GOO, getX(), getY() + 1, getZ(), 40, 0.5, 0.7, 0.5, 0.1);
            Player target = target(level);
            if (target != null) setTarget(target);
        }
    }

    /** Stands still and can't attack for {@code ticks}. */
    public void stun(int ticks) { stunTicks = Math.max(stunTicks, ticks); }

    /** A fake herd member hit in combat: it runs at the player and explodes. */
    void chargeAndExplode(Player player) {
        decoy = Decoy.EXPLODER;
        herdGroup = null;
        life = 120;
        setTarget(player);
        var movement = getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(0.4);
        playSound(soundById("entity.creeper.primed"), 1.5F, 0.6F);
    }

    /** Gives up for now: gone in a puff of goo, the haunt waits to appear again. */
    void vanishForNow(ServerLevel level) {
        level.sendParticles(GOO, getX(), getY() + 0.8, getZ(), 30, 0.4, 0.5, 0.4, 0.05);
        pause();
        discard();
    }

    private void pause() {
        FalsekinHaunts.Haunt haunt = haunt();
        if (haunt != null) haunt.cooldown = FalsekinHaunts.COOLDOWN;
    }

    Player target(ServerLevel level) {
        return hauntTarget == null ? null : level.getServer().getPlayerList().getPlayer(hauntTarget);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        EntityType<?> type = disguise();
        return type != null ? type.getDimensions() : super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DISGUISE.equals(key)) refreshDimensions();
    }

    @Override
    public boolean isPushable() { return decoy != Decoy.WATCHER && super.isPushable(); }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // The real one despawns on its own terms (left behind, disengaging); only decoys use vanilla despawning.
        return decoy();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // Killed for good: the haunt ends.
        if (!decoy() && hauntTarget != null && level() instanceof ServerLevel level)
            FalsekinHaunts.get(level.getServer()).end(hauntTarget);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        if (herdGroup != null) FalsekinMassDecoy.tick(level, this);
        if (isRemoved()) return;
        if (decoy()) {
            tickDecoy(level);
            return;
        }
        if (orphaned) {
            tickLeaving(level, null);
            return;
        }
        if (herdGroup != null) tickTell(level);
        FalsekinHaunts.Haunt haunt = adoptHaunt(level);
        if (haunt == null) return;
        if (stunTicks > 0) {
            stunTicks--;
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        Player target = target(level);
        if (leaving) {
            tickLeaving(level, target);
            return;
        }
        if (target == null || target.level() != level || distanceTo(target) > LEASH) {
            // Left behind: gone until it appears again.
            pause();
            discard();
            return;
        }
        if (tickCount % 20 == 0 && target instanceof ServerPlayer serverPlayer && FalsekinHaunts.protectedPlayer(serverPlayer)) {
            startLeaving(level, target);
            return;
        }
        if (revealed) tickRevealed(target);
        else tickStalking(level, target, haunt);
    }

    /** Its haunt (adopting the nearest player when it was spawned without one); null when it had to go. */
    private FalsekinHaunts.Haunt adoptHaunt(ServerLevel level) {
        FalsekinHaunts haunts = FalsekinHaunts.get(level.getServer());
        if (hauntTarget == null) {
            Player nearest = level.getNearestPlayer(getX(), getY(), getZ(), 128, p -> !p.isSpectator() && !((Player) p).isCreative());
            if (nearest == null) {
                discard();
                return null;
            }
            hauntTarget = nearest.getUUID();
            haunts.start(hauntTarget);
            if (disguise() == null) wear(FalsekinKinds.fitting(level, blockPosition()).stream().findFirst().orElse("cow"));
        }
        FalsekinHaunts.Haunt haunt = haunts.haunt(hauntTarget);
        if (haunt == null || !haunts.claim(haunt, this)) {
            discard();
            return null;
        }
        return haunt;
    }

    /**
     * The tell while it hides in a Mass Decoy herd: the real one keeps watching its player and now and then drips a
     * drop of purple goo; the fakes graze and look around.
     */
    private void tickTell(ServerLevel level) {
        Player target = target(level);
        if (target != null) getLookControl().setLookAt(target, 40, 40);
        if (tickCount % 30 == 0)
            level.sendParticles(GOO, getX(), getY() + getBbHeight() * 0.4, getZ(), 2, 0.15, 0.1, 0.15, 0);
    }

    /** Its player died (killed by it or not): it heals fully, runs off and is gone. The haunt is already over. */
    void afterPlayerDeath(ServerLevel level, Vec3 deathSpot) {
        orphaned = true;
        setHealth(getMaxHealth());
        hiding = false;
        herdGroup = null;
        uprightTicks = 0;
        this.entityData.set(UPRIGHT, false);
        if (revealed) this.entityData.set(DISGUISE, "");
        startLeaving(level, null);
        fleeFrom = deathSpot;
        leaveTicks = 200;
    }

    private void startLeaving(ServerLevel level, Player target) {
        leaving = true;
        fleeFrom = target == null ? position() : target.position();
        leaveTicks = 300;
        setTarget(null);
        this.entityData.set(TWITCH, false);
        if (revealed) playSound(soundById("entity.ravager.ambient"), 1.5F, 0.6F);
    }

    /** Runs away from its player and is gone once it's far enough or out of sight. */
    private void tickLeaving(ServerLevel level, Player target) {
        if (target != null && target.isAlive() && target.level() == level) fleeFrom = target.position();
        double distance = position().distanceTo(fleeFrom);
        if (--leaveTicks <= 0 || distance > 28 || leaveTicks < 160 && !seen(level)) {
            level.sendParticles(GOO, getX(), getY() + 0.8, getZ(), 20, 0.4, 0.5, 0.4, 0.05);
            pause();
            discard();
            return;
        }
        if (leaveTicks % 20 == 0) {
            Vec3 away = position().add(position().subtract(fleeFrom).multiply(1, 0, 1).normalize().scale(16));
            getNavigation().moveTo(away.x, away.y, away.z, revealed ? 1.4 : 1.1);
        }
    }

    private void tickStalking(ServerLevel level, Player target, FalsekinHaunts.Haunt haunt) {
        if (vanishTicks > 0) {
            if (--vanishTicks == 0) {
                setInvisible(false);
                reveal(level, vanishTarget, powerFraction());
            }
            return;
        }
        if (snapTicks >= 0) {
            tickSnap(level, target);
            return;
        }
        if (herdGroup != null) return;
        if (distanceTo(target) <= STALK_RANGE && !target.isCreative() && !target.isSpectator()) {
            haunt.power = Math.min(MAX_POWER, haunt.power + 1);
            if (tickCount % 100 == 0) FalsekinHaunts.get(level.getServer()).changed();
        }
        if (haunt.power >= MAX_POWER && !FalsekinWatchers.active(level, target.getUUID())) {
            snapTicks = 0;
            return;
        }
        if (twitchTicks > 0 && --twitchTicks == 0) this.entityData.set(TWITCH, false);
        if (--nextShift <= 0) {
            if (!seen(level) && shapeshift(level, target)) nextShift = (stage() >= 2 ? 500 : 1000) + getRandom().nextInt(900);
            else nextShift = 100;
        }
        tickWeirdness(level, target);
    }

    /** The odd things it does to its player, more of them at higher stages. */
    private void tickWeirdness(ServerLevel level, Player target) {
        int stage = stage();
        if (stage == 0) return;
        double distance = distanceTo(target);
        if (stareTicks > 0) {
            stareTicks--;
            getNavigation().stop();
            getLookControl().setLookAt(target, 60, 60);
            if (stage >= 3 && stareTicks % 20 < 8) setYHeadRot(yBodyRot + 180.0F);   // head all the way round
        } else if (distance < 24 && getRandom().nextInt(stage >= 2 ? 300 : 600) == 0) {
            stareTicks = 60 + getRandom().nextInt(60);
        }
        if (stage >= 2 && distance < 20 && getRandom().nextInt(900) == 0) {
            Vec3 away = position().add(position().subtract(target.position()).normalize().scale(5));
            getNavigation().moveTo(away.x, away.y, away.z, 0.6);
            stareTicks = 40;
        }
        if (stage < 3) return;
        if (twitchTicks == 0 && getRandom().nextInt(260) == 0) {
            twitchTicks = 15 + getRandom().nextInt(30);
            this.entityData.set(TWITCH, true);
        }
        if (getRandom().nextInt(500) == 0) mimicPlayer(level, target);
        if (--watchersCooldown <= 0 && getRandom().nextInt(FalsekinWatchers.CHANCE) == 0) {
            FalsekinWatchers.start(level, this, target);
            watchersCooldown = FalsekinWatchers.COOLDOWN;
        }
        if (--creepCooldown <= 0 && distance < 32 && distance > 9 && !seen(level)) {
            Vec3 spot = ShadowStep.behind(level, this, target, 8 + getRandom().nextInt(4));
            if (spot.distanceTo(target.position()) > 5) teleportTo(spot.x, spot.y, spot.z);
            creepCooldown = 200 + getRandom().nextInt(300);
        }
    }

    /** Footsteps, a hurt grunt or a door right behind the player. */
    private void mimicPlayer(ServerLevel level, Player player) {
        Vec3 behind = player.position().subtract(Vec3.directionFromRotation(0, player.getYRot()).scale(3));
        String sound = switch (getRandom().nextInt(3)) {
            case 0 -> "entity.player.hurt";
            case 1 -> "block.wooden_door.open";
            default -> "block.grass.step";
        };
        int repeats = sound.endsWith("step") ? 4 : 1;
        for (int k = 0; k < repeats; k++) {
            EchelonTasks.later(k * 7, () -> level.playSound(null, behind.x, behind.y, behind.z, sound(sound),
                    SoundSource.PLAYERS, 0.7F, 0.9F + getRandom().nextFloat() * 0.15F));
        }
    }

    /** Full power: it convulses, stutters its stolen voice, then screams and attacks. */
    private void tickSnap(ServerLevel level, Player target) {
        snapTicks++;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (snapTicks == 1) this.entityData.set(TWITCH, true);
        if (snapTicks % 4 == 0 && disguise() != null) {
            level.playSound(null, getX(), getY(), getZ(), sound("entity." + disguisePath() + ".ambient"),
                    SoundSource.NEUTRAL, 1.0F, 0.4F + getRandom().nextFloat() * 1.4F);
        }
        if (snapTicks >= 50) {
            snapTicks = -1;
            reveal(level, target, 1.0F);
        }
    }

    private void tickRevealed(Player target) {
        // Rarely (about every 40 seconds) it rears up and walks on two legs for 3 to 5 seconds.
        if (uprightTicks > 0) {
            if (--uprightTicks == 0 || !fighting()) {
                uprightTicks = 0;
                this.entityData.set(UPRIGHT, false);
            }
        } else if (fighting() && getRandom().nextInt(UPRIGHT_CHANCE) == 0) {
            uprightTicks = 60 + getRandom().nextInt(41);
            this.entityData.set(UPRIGHT, true);
        }
        if (fighting() && (getTarget() == null || !getTarget().isAlive()) && !target.isCreative() && !target.isSpectator())
            setTarget(target);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        // Unseen it moves 40% faster; looked at, it slows back down.
        boolean unseen = fighting() && !looksAt(target);
        if (unseen && !speed.hasModifier(UNSEEN_BOOST))
            speed.addTransientModifier(new AttributeModifier(UNSEEN_BOOST, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else if (!unseen && speed.hasModifier(UNSEEN_BOOST)) speed.removeModifier(UNSEEN_BOOST);
    }

    private void tickDecoy(ServerLevel level) {
        if (decoy == Decoy.HERD) return;
        if (decoy == Decoy.WATCHER) {
            tickWatcher(level);
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            getNavigation().moveTo(target, decoy == Decoy.EXPLODER ? 1.6 : 1.5);
            if (distanceTo(target) < 1.8F) {
                if (decoy == Decoy.EXPLODER) explode(level);
                else burst(level);
                return;
            }
        }
        if (--life <= 0) {
            if (decoy == Decoy.EXPLODER) explode(level);
            else burst(level);
        }
    }

    /** Explosion of a fake hit in combat: up to {@link #EXPLOSION_DAMAGE} within 3.5 blocks, no block damage. */
    /** Stands still facing its player; the red-eyed one vanishes into Darkness when the player comes close. */
    private void tickWatcher(ServerLevel level) {
        if (watched == null || !watched.isAlive() || watched.level() != level || --life <= 0) {
            fade(level);
            return;
        }
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        float yaw = yawTowards(position(), watched.position());
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        double dy = watched.getEyeY() - getEyeY();
        setXRot((float) -(Mth.atan2(dy, watched.position().subtract(position()).horizontalDistance()) * Mth.RAD_TO_DEG));
        if (!redEyes) return;
        if (tickCount % 2 == 0) {
            // Two glowing dots on the villager's face, just in front of where its eyes are.
            Vec3 forward = Vec3.directionFromRotation(getXRot(), yaw);
            Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize();
            Vec3 face = new Vec3(getX(), getY() + getBbHeight() * 0.81, getZ()).add(forward.scale(0.27));
            for (int eye : new int[]{-1, 1}) {
                Vec3 p = face.add(side.scale(0.09 * eye));
                level.sendParticles(EchelonParticles.RED_EYE, true, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        if (distanceTo(watched) < 3.5F) {
            level.playSound(null, getX(), getY(), getZ(), sound("ambient.cave"), SoundSource.HOSTILE, 1.5F, 0.6F);
            watched.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 240, 0));
            watched.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            fade(level);
        }
    }

    private void fade(ServerLevel level) {
        level.sendParticles(GOO, getX(), getY() + 0.9, getZ(), 15, 0.3, 0.5, 0.3, 0.03);
        discard();
    }

    private void explode(ServerLevel level) {
        if (isRemoved()) return;
        discard();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(GOO, getX(), getY() + 0.6, getZ(), 60, 0.8, 0.6, 0.8, 0.2);
        level.playSound(null, getX(), getY(), getZ(), sound("entity.generic.explode"), SoundSource.HOSTILE, 1.5F, 0.8F);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5),
                entity -> !(entity instanceof Falsekin) && entity.isAlive())) {
            double distance = victim.distanceTo(this);
            if (distance > 3.5) continue;
            victim.hurtServer(level, level.damageSources().explosion(this, this), (float) (EXPLOSION_DAMAGE * (1 - distance / 3.5 * 0.6)));
            Vec3 push = victim.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.8);
            victim.push(push.x, 0.4, push.z);
            victim.hurtMarked = true;
        }
    }

    /** A shed skin bursts into goo, slowing and blinding players right next to it. */
    public void burst(ServerLevel level) {
        if (isRemoved()) return;
        level.sendParticles(GOO, getX(), getY() + getBbHeight() / 2, getZ(), 50, 0.6, 0.5, 0.6, 0.1);
        level.playSound(null, getX(), getY(), getZ(), sound("entity.slime.squish"), SoundSource.HOSTILE, 1.0F, 0.6F);
        for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(2.5))) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
        }
        discard();
    }

    // ---------------------------------------------------------------- being found out

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        if (decoy == Decoy.CHARGER) {
            if (attacker != null) {
                burst(level);
                return true;
            }
            return super.hurtServer(level, source, amount);
        }
        if (decoy == Decoy.HERD) {
            if (attacker != null) FalsekinMassDecoy.hit(level, this, attacker);
            return false;
        }
        if (decoy == Decoy.EXPLODER) return super.hurtServer(level, source, amount);
        if (decoy == Decoy.WATCHER) {
            fade(level);
            return false;
        }
        if (herdGroup != null && FalsekinMassDecoy.active(herdGroup)) {
            if (attacker == null) return false;
            return FalsekinMassDecoy.hit(level, this, attacker) && super.hurtServer(level, source, amount);
        }
        if (revealed) return super.hurtServer(level, source, amount);
        if (vanishTicks > 0) return false;
        if (attacker != null && hauntTarget != null && FalsekinWatchers.active(level, hauntTarget)) {
            // No attack while its watchers are out: it slips away instead and comes back later.
            vanishForNow(level);
            return false;
        }
        if (attacker != null) {
            FalsekinHaunts.Haunt haunt = haunt();
            if (stage() >= 3 && haunt != null && haunt.decoys < 2 && snapTicks < 0) {
                haunt.decoys++;
                FalsekinHaunts.get(level.getServer()).changed();
                scream(level);
                FalsekinMassDecoy.start(level, this, disguisePath(), false, haunt.decoys);
                return false;
            }
            vanish(level, attacker);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    /** Hit in disguise: it screams, vanishes and comes back in its true form a moment later, somewhere near. */
    private void vanish(ServerLevel level, LivingEntity attacker) {
        scream(level);
        level.sendParticles(GOO, getX(), getY() + getBbHeight() / 2, getZ(), 60, 0.5, 0.5, 0.5, 0.15);
        setInvisible(true);
        getNavigation().stop();
        Vec3 spot = ShadowStep.spotNear(level, this, attacker.position(), 5, 8);
        if (spot != null) teleportTo(spot.x, spot.y, spot.z);
        vanishTarget = attacker;
        vanishTicks = 30;
    }

    /**
     * Drops the disguise: true form, {@code power} (0 to 1) applied, screaming, then it attacks {@code target} (its
     * haunted player when {@code target} is someone else and its player is near).
     */
    public void reveal(ServerLevel level, LivingEntity target, float power) {
        revealed = true;
        hiding = false;
        snapTicks = -1;
        this.entityData.set(TWITCH, false);
        this.entityData.set(DISGUISE, "");
        boost(Attributes.MAX_HEALTH, 1.5 * power);
        boost(Attributes.ATTACK_DAMAGE, 1.0 * power);
        boost(Attributes.MOVEMENT_SPEED, 0.3 * power);
        var armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.addOrReplacePermanentModifier(new AttributeModifier(POWER_BOOST, 6.0 * power, AttributeModifier.Operation.ADD_VALUE));
        setHealth(getMaxHealth());
        level.sendParticles(GOO, getX(), getY() + 1, getZ(), 80, 0.7, 0.9, 0.7, 0.15);
        scream(level);
        triggerAnim("abilities", "scream");
        stun(30);
        Player hunted = target(level);
        LivingEntity victim = hunted != null && hunted.isAlive() ? hunted : target;
        if (victim != null && victim.isAlive() && !(victim instanceof Player player && (player.isCreative() || player.isSpectator())))
            setTarget(victim);
    }

    private void boost(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double amount) {
        var instance = getAttribute(attribute);
        if (instance != null) instance.addOrReplacePermanentModifier(
                new AttributeModifier(POWER_BOOST, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** Layered shrieks: a ghast scream under a fox screech and a screaming goat. */
    public void scream(ServerLevel level) {
        level.playSound(null, getX(), getY(), getZ(), sound("entity.ghast.scream"), SoundSource.HOSTILE, 2.0F, 0.55F);
        level.playSound(null, getX(), getY(), getZ(), sound("entity.fox.screech"), SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), sound("entity.goat.screaming.ambient"), SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    // ---------------------------------------------------------------- shapeshifting

    /** Silently teleports into another herd near its player and becomes one of them. */
    private boolean shapeshift(ServerLevel level, Player target) {
        String current = disguisePath();
        for (FalsekinKinds.Herd herd : FalsekinKinds.herds(level, target.position(), 40, 1)) {
            if (herd.kind().equals(current)) continue;
            for (Mob member : herd.members()) {
                Vec3 spot = ShadowStep.spotNear(level, this, member.position(), 1.5, 3.5);
                if (spot == null || FalsekinKinds.inView(level, (ServerPlayer) target, spot.add(0, 1, 0))) continue;
                getNavigation().stop();
                teleportTo(spot.x, spot.y, spot.z);
                wear(herd.kind());
                return true;
            }
        }
        return false;
    }

    private Mob nearestOfKind(ServerLevel level, String path, double range) {
        Mob best = null;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(range), mob -> !(mob instanceof Falsekin)
                && FalsekinKinds.path(mob).equals(path))) {
            if (best == null || distanceToSqr(mob) < distanceToSqr(best)) best = mob;
        }
        return best;
    }

    public String disguisePath() {
        EntityType<?> type = disguise();
        return type == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }

    /** True when a player within 64 blocks has it in view (60 degree cone, clear line of sight). */
    public boolean seen(ServerLevel level) {
        for (Player player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(this) > 64 * 64) continue;
            if (looksAt(player)) return true;
        }
        return false;
    }

    private boolean looksAt(Player player) {
        Vec3 toMe = getEyePosition().subtract(player.getEyePosition()).normalize();
        return player.getViewVector(1.0F).dot(toMe) > 0.5 && player.hasLineOfSight(this);
    }

    // ---------------------------------------------------------------- sounds

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
    }

    /** A vanilla sound by its ID path (e.g. "entity.creeper.primed"), for the abilities. */
    public static SoundEvent soundById(String path) { return sound(path); }

    @Override
    protected SoundEvent getAmbientSound() {
        String path = disguisePath();
        if (path.isEmpty()) return sound("entity.ravager.ambient");
        // From stage 2 it sometimes uses another animal's voice.
        if (!decoy() && stage() >= 2 && getRandom().nextInt(3) == 0)
            path = FalsekinKinds.WEARABLE.get(getRandom().nextInt(FalsekinKinds.WEARABLE.size()));
        return sound("entity." + path + ".ambient");
    }

    @Override
    public void playAmbientSound() {
        super.playAmbientSound();
        if (!revealed && !decoy() && stage() >= 3 && level() instanceof ServerLevel && getRandom().nextBoolean()) {
            SoundEvent sound = getAmbientSound();
            for (int k = 1; k <= 2; k++) EchelonTasks.later(k * 3, () -> playSound(sound, 1.0F, getVoicePitch()));
        }
    }

    @Override
    public int getAmbientSoundInterval() { return revealed && !hiding ? 120 : stage() >= 2 ? 160 : 240; }

    @Override
    public float getVoicePitch() {
        if (revealed && !hiding) return 0.7F + getRandom().nextFloat() * 0.1F;
        if (decoy()) return super.getVoicePitch();
        return switch (stage()) {
            case 0 -> super.getVoicePitch();
            case 1, 2 -> 0.75F + getRandom().nextFloat() * 0.1F;
            default -> 0.5F + getRandom().nextFloat();
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        String path = disguisePath();
        return path.isEmpty() ? sound("entity.ravager.hurt") : sound("entity." + path + ".hurt");
    }

    @Override
    protected SoundEvent getDeathSound() { return revealed ? sound("entity.warden.death") : super.getDeathSound(); }

    // ---------------------------------------------------------------- imitation

    /**
     * Copies a mob of the kind it wears: stays with it, walks where it walks and looks where it looks. From stage 2
     * it sometimes shadows its player instead, standing still whenever they look.
     */
    /** Stuns never take hold (a stun can still interrupt a cast, see CrowSwarmAbility). */
    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        return !effect.is(ekuzo.crazyworldprogression.fletching.Fletching.STUNNED) && super.canBeAffected(effect);
    }

    private final class ImitateGoal extends Goal {
        private Entity model;
        private int retarget;

        ImitateGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        private boolean allowed() {
            return !revealed && decoy == Decoy.NONE && herdGroup == null && !leaving && vanishTicks == 0 && snapTicks < 0
                    && stareTicks <= 0;
        }

        @Override
        public boolean canUse() { return allowed() && pick() != null; }

        @Override
        public boolean canContinueToUse() { return allowed() && model != null && model.isAlive() && distanceToSqr(model) < 40 * 40; }

        private Entity pick() {
            if (!(level() instanceof ServerLevel level)) return null;
            Player player = target(level);
            if (stage() >= 2 && player != null && distanceTo(player) < 24 && !player.isCreative() && !player.isSpectator()
                    && getRandom().nextInt(3) > 0)
                model = player;
            else model = nearestOfKind(level, disguisePath(), 16);
            return model;
        }

        @Override
        public void stop() {
            model = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (model == null) return;
            if (--retarget <= 0) {
                retarget = 10 + getRandom().nextInt(10);
                double distance = distanceTo(model);
                if (model instanceof Player player) {
                    if (looksAt(player)) getNavigation().stop();                      // freezes when watched
                    else if (distance > 12) getNavigation().moveTo(player, 0.9);
                    else if (distance < 7) getNavigation().stop();
                } else if (distance > 4.5) {
                    getNavigation().moveTo(model, 1.0);
                } else if (model.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
                    Vec3 ahead = model.position().add(model.getDeltaMovement().scale(20)).add(getRandom().nextGaussian(), 0,
                            getRandom().nextGaussian());
                    getNavigation().moveTo(ahead.x, ahead.y, ahead.z, 1.0);
                } else {
                    getNavigation().stop();
                }
            }
            if (model instanceof Player) getLookControl().setLookAt(model, 30, 30);
            else {
                Vec3 look = model.getEyePosition().add(model.getViewVector(1.0F).scale(6));
                getLookControl().setLookAt(look.x, look.y, look.z);
            }
        }
    }

    // ---------------------------------------------------------------- animation and helpers

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<Falsekin>("Walk/Idle", 4, test -> {
                    boolean moving = test.isMoving();
                    if (test.animatable().upright()) return test.setAndContinue(moving ? UPRIGHT_WALK : UPRIGHT_IDLE);
                    return test.setAndContinue(moving ? DefaultAnimations.WALK : DefaultAnimations.IDLE);
                }),
                DefaultAnimations.genericAttackAnimation(DefaultAnimations.ATTACK_SWING),
                new AnimationController<Falsekin>("abilities", test -> PlayState.STOP)
                        .triggerableAnim("scream", RawAnimation.begin().thenPlay("attack.scream"))
                        .triggerableAnim("lash", RawAnimation.begin().thenPlay("attack.lash"))
                        .triggerableAnim("shed", RawAnimation.begin().thenPlay("attack.shed"))
                        .triggerableAnim("lunge", RawAnimation.begin().thenPlay("attack.lunge")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    /** Degrees from {@code from} to face {@code to}. */
    public static float yawTowards(Entity from, Entity to) { return yawTowards(from.position(), to.position()); }

    public static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Spawns a shed skin of {@code path} next to {@code owner}. */
    public static Falsekin spawnDecoy(ServerLevel level, Mob owner, String path, LivingEntity target) {
        Falsekin skin = EchelonEntities.FALSEKIN.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (skin == null) return null;
        Vec3 spot = ShadowStep.spotNear(level, owner, owner.position(), 1.0, 2.5);
        if (spot == null) spot = owner.position();
        skin.snapTo(spot.x, spot.y, spot.z, owner.getYRot(), 0);
        skin.makeDecoy(path);
        if (target != null) skin.setTarget(target);
        level.addFreshEntity(skin);
        level.sendParticles(GOO, spot.x, spot.y + 0.5, spot.z, 20, 0.3, 0.4, 0.3, 0.05);
        return skin;
    }

    /** Purple goo particles, for the abilities. */
    public static DustParticleOptions goo() { return GOO; }

    /** Starts a combat Mass Decoy as a herd of a kind that fits where it stands (MassDecoyAbility). */
    public void massDecoyInCombat(ServerLevel level) {
        List<String> fitting = FalsekinKinds.fitting(level, blockPosition());
        String kind = !fitting.isEmpty() ? fitting.get(getRandom().nextInt(fitting.size()))
                : worn.isEmpty() ? "cow" : worn.get(getRandom().nextInt(worn.size()));
        FalsekinMassDecoy.start(level, this, kind, true, 0);
    }
}
