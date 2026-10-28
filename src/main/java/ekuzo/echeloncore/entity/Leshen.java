package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;
import com.geckolib.constant.DefaultAnimations;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilities;
import ekuzo.echeloncore.entity.ability.ShadowStep;
import ekuzo.echeloncore.entity.ability.SummonWolves;
import ekuzo.echeloncore.EchelonCore;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.EntityType;
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

/**
 * The Leshen, a forest spirit with a deer skull and antlers (model: assets/echelon-core/geckolib/models/entity/leshen.geo.json,
 * source art/leshen.bbmodel). A slow, hard-hitting melee brute; MobForge templates can resize it through the scale attribute.
 */
public class Leshen extends Monster implements GeoEntity {
    /** Ticks without a target before it fades out again. */
    private static final int CALM_DELAY = 60;
    /** Crow Swarm cast phase (0 none, 1 aiming, 2 aim locked): its eyes and antler tips glow pink, then purple. */
    private static final EntityDataAccessor<Integer> CAST_PHASE = SynchedEntityData.defineId(Leshen.class, EntityDataSerializers.INT);
    /** Second phase (below {@link #RAGE_HEALTH} of its health): red glow, Shadow Wolves, faster and harder hits. */
    private static final EntityDataAccessor<Boolean> RAGE = SynchedEntityData.defineId(Leshen.class, EntityDataSerializers.BOOLEAN);
    private static final float RAGE_HEALTH = 0.4F;
    private static final net.minecraft.resources.Identifier RAGE_BOOST = EchelonCore.id("leshen_rage");
    public static final int AIMING = 1;
    public static final int LOCKED = 2;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private int calmTicks;
    private int showTicks;
    private int nextShow;

    public Leshen(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CAST_PHASE, 0);
        builder.define(RAGE, false);
    }

    /** Stuns never take hold (a stun can still interrupt a cast, see CrowSwarmAbility). */
    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        return !effect.is(ekuzo.crazyworldprogression.fletching.Fletching.STUNNED) && super.canBeAffected(effect);
    }

    public boolean casting() { return castPhase() != 0; }

    public int castPhase() { return this.entityData.get(CAST_PHASE); }

    public void setCastPhase(int phase) { this.entityData.set(CAST_PHASE, phase); }

    public boolean raging() { return this.entityData.get(RAGE); }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            tickHiding(level);
            tickRage(level);
        }
        // While casting it stands still, where the client would never turn its body: keep body and head on its
        // facing (synced from the server) so it visibly turns towards where it aims.
        else if (casting()) {
            setYBodyRot(getYRot());
            setYHeadRot(getYRot());
        }
    }

    private int nextSummon;

    /**
     * Below 40% health the Leshen enters its second phase: it roars, its glow turns red, it moves 15% faster and hits
     * 3 harder, and three Shadow Wolves come out of the smoke. While it fights, it calls two more now and then (not
     * always) once its wolves are gone.
     */
    private void tickRage(ServerLevel level) {
        if (!raging()) {
            if (getHealth() > getMaxHealth() * RAGE_HEALTH || !isAlive()) return;
            this.entityData.set(RAGE, true);
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            var damage = getAttribute(Attributes.ATTACK_DAMAGE);
            if (speed != null) speed.addOrReplacePermanentModifier(new AttributeModifier(RAGE_BOOST, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            if (damage != null) damage.addOrReplacePermanentModifier(new AttributeModifier(RAGE_BOOST, 3.0, AttributeModifier.Operation.ADD_VALUE));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 60, 1.2, 1.2, 1.2, 0.05);
            SummonWolves.summon(level, this, getTarget(), 3);
            nextSummon = 500 + getRandom().nextInt(400);
            return;
        }
        if (getTarget() == null || !getTarget().isAlive() || --nextSummon > 0) return;
        nextSummon = 400 + getRandom().nextInt(400);
        if (SummonWolves.wolves(level, this) == 0 && getRandom().nextFloat() < 0.6F) SummonWolves.summon(level, this, getTarget(), 2);
    }

    /**
     * Out of combat a Leshen with Shadow Step stays vanished and only shows itself briefly now and then; a target
     * (or being hurt) brings it back. Without Shadow Step (switched off by its MobForge template) it stays visible.
     */
    private void tickHiding(ServerLevel level) {
        if (ShadowStep.active(this)) return;
        if (getTarget() != null && getTarget().isAlive() || !MobAbilities.has(this, ShadowStep.ID)) {
            calmTicks = 0;
            showTicks = 0;
            if (isInvisible()) ShadowStep.appear(level, this);
            return;
        }
        if (++calmTicks < CALM_DELAY) return;
        if (showTicks > 0) {
            if (--showTicks == 0) hide(level);
            return;
        }
        if (!isInvisible()) hide(level);
        else if (--nextShow <= 0) {
            ShadowStep.appear(level, this);
            showTicks = 40 + getRandom().nextInt(60);
        }
    }

    private void hide(ServerLevel level) {
        ShadowStep.vanish(level, this);
        nextShow = 300 + getRandom().nextInt(600);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController(),
                DefaultAnimations.genericAttackAnimation(DefaultAnimations.ATTACK_SWING),
                new AnimationController<Leshen>("abilities", test -> PlayState.STOP)
                        .triggerableAnim("cast", RawAnimation.begin().thenPlay("attack.cast"))
                        .triggerableAnim("root", RawAnimation.begin().thenPlay("attack.root"))
                        .triggerableAnim("slam", RawAnimation.begin().thenPlay("attack.slam")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
