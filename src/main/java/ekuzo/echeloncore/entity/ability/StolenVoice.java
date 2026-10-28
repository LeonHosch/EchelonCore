package ekuzo.echeloncore.entity.ability;

import com.geckolib.animatable.GeoEntity;
import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.Falsekin;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Stolen Voice: a sound it has stolen (a creeper's hiss, a wolf's growl, a player's hurt sound, a zombie's groan)
 * plays off to the target's side to make them turn. The caster vanishes meanwhile and comes out of the dark
 * right behind them, lunging for 1.4x its attack damage.
 */
public final class StolenVoice implements MobAbility {
    public static final Identifier ID = EchelonCore.id("stolen_voice");
    private static final String[] LURES = {"entity.creeper.primed", "entity.wolf.growl", "entity.player.hurt",
            "entity.zombie.ambient"};
    private static final int DELAY = 18;

    @Override public long cooldownTicks() { return 320; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        if (!FalsekinAbilities.ready(caster) || !(context.target() instanceof LivingEntity target) || !target.isAlive()) return false;
        float distance = ((Mob) caster).distanceTo(target);
        return distance > 4.0F && distance <= 24.0F;
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        Busy.start(mob);
        // The lure: off to one side and a little ahead, so the target turns away from where the attack comes from.
        Vec3 look = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 side = new Vec3(-look.z, 0, look.x).scale(mob.getRandom().nextBoolean() ? 4 : -4);
        Vec3 lure = target.position().add(look.scale(3)).add(side);
        level.playSound(null, lure.x, lure.y, lure.z, Falsekin.soundById(LURES[mob.getRandom().nextInt(LURES.length)]),
                SoundSource.HOSTILE, 1.2F, 1.0F);
        ShadowStep.vanish(level, mob);
        EchelonTasks.later(DELAY, () -> {
            Busy.end(mob);
            if (!mob.isAlive()) return;
            if (!target.isAlive()) {
                ShadowStep.appear(level, mob);
                return;
            }
            Vec3 spot = ShadowStep.behind(level, mob, target, 2.0);
            mob.teleportTo(spot.x, spot.y, spot.z);
            float yaw = Falsekin.yawTowards(mob, target);
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            mob.setInvisible(false);
            level.sendParticles(Falsekin.goo(), mob.getX(), mob.getY() + 1, mob.getZ(), 25, 0.4, 0.6, 0.4, 0.05);
            if (mob instanceof GeoEntity geo) geo.triggerAnim("abilities", "lunge");
            EchelonTasks.later(6, () -> {
                if (mob.isAlive() && target.isAlive() && mob.distanceTo(target) < 3.5F) {
                    target.hurtServer(level, level.damageSources().mobAttack(mob),
                            (float) (mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4));
                }
            });
        });
    }
}
