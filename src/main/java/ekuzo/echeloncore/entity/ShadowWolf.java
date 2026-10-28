package ekuzo.echeloncore.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.RawAnimation;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * A wolf of smoke and moss summoned by a Leshen (or any mob with the Summon Wolves ability). It hunts players, leaps at
 * them, and dissolves when its summoner dies or after {@link #LIFETIME} ticks.
 */
public class ShadowWolf extends Monster implements GeoEntity {
    public static final int LIFETIME = 1200;
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("attack.bite");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private UUID summoner;

    public ShadowWolf(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /** Summons a wolf at {@code x, y, z} for {@code owner}, already hunting {@code target}. */
    public static ShadowWolf summon(ServerLevel level, Mob owner, LivingEntity target, double x, double y, double z) {
        ShadowWolf wolf = new ShadowWolf(EchelonEntities.SHADOW_WOLF, level);
        wolf.summoner = owner.getUUID();
        wolf.setPos(x, y, z);
        wolf.setYRot(owner.getYRot());
        if (target != null) wolf.setTarget(target);
        level.addFreshEntity(wolf);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.6, z, 20, 0.4, 0.4, 0.4, 0.02);
        return wolf;
    }

    public boolean summonedBy(Entity entity) { return entity.getUUID().equals(summoner); }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        Entity owner = summoner == null ? null : level.getEntity(summoner);
        if (tickCount > LIFETIME || summoner != null && (owner == null || !owner.isAlive())) dissolve(level);
    }

    private void dissolve(ServerLevel level) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.6, getZ(), 20, 0.4, 0.4, 0.4, 0.02);
        discard();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) triggerAnim("main", "bite");
        return hit;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController(),
                new com.geckolib.animation.AnimationController<ShadowWolf>("main", test -> com.geckolib.animation.object.PlayState.STOP)
                        .triggerableAnim("bite", BITE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.geoCache; }
}
