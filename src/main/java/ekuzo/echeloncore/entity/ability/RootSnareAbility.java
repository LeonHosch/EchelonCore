package ekuzo.echeloncore.entity.ability;

import com.geckolib.animatable.GeoEntity;
import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonTasks;
import ekuzo.echeloncore.entity.RootSnare;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Root Snare: the caster digs its claws into the ground and a ring of roots breaks out under its target. Leaving the
 * ring while the roots rise only costs a slow; staying in it until they close roots you in place ({@link RootSnare}).
 */
public final class RootSnareAbility implements MobAbility {
    public static final Identifier ID = EchelonCore.id("root_snare");
    private static final int WIND_UP = 12;

    @Override public long cooldownTicks() { return 260; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        if (!(caster instanceof Mob mob) || Busy.is(mob) || !(context.target() instanceof LivingEntity target)) return false;
        double distance = mob.distanceTo(target);
        return target.isAlive() && target.onGround() && distance >= 3 && distance <= 18 && mob.hasLineOfSight(target);
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        Busy.start(mob);
        if (mob instanceof GeoEntity geo) geo.triggerAnim("abilities", "root");
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                Busy.end(mob);
                return true;
            }
            Busy.holdStill(mob);
            mob.getLookControl().setLookAt(target, 30, 30);
            if (++ticks[0] < WIND_UP) return false;
            if (target.isAlive()) RootSnare.raise(level, mob, target.position());
            Busy.end(mob);
            return true;
        });
    }
}
