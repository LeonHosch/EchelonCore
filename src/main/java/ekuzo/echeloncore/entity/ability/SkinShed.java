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

import java.util.List;

/**
 * Skin Shed: the caster shudders and sheds the skins it has worn as three decoys (copies of the mobs a Falsekin
 * imitated; farm animals for any other caster). They run at the target and burst into blinding, slowing goo.
 */
public final class SkinShed implements MobAbility {
    public static final Identifier ID = EchelonCore.id("skin_shed");
    private static final int WIND_UP = 20;
    private static final List<String> FALLBACK = List.of("cow", "sheep", "pig");

    @Override public long cooldownTicks() { return 500; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return FalsekinAbilities.ready(caster) && context.target() instanceof LivingEntity target && target.isAlive()
                && ((Mob) caster).distanceTo(target) <= 16.0F;
    }

    @Override public void execute(MobAbilityContext context) {
        ServerLevel level = context.world();
        Mob mob = (Mob) level.getEntity(context.caster().uuid());
        LivingEntity target = (LivingEntity) context.target();
        Busy.start(mob);
        if (mob instanceof GeoEntity geo) geo.triggerAnim("abilities", "shed");
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), Falsekin.soundById("entity.slime.squish"),
                SoundSource.HOSTILE, 1.5F, 0.5F);
        int[] ticks = {0};
        EchelonTasks.everyTick(() -> {
            if (!mob.isAlive()) {
                Busy.end(mob);
                return true;
            }
            Busy.holdStill(mob);
            if (++ticks[0] < WIND_UP) return false;
            Busy.end(mob);
            List<String> skins = mob instanceof Falsekin falsekin && !falsekin.worn().isEmpty() ? falsekin.worn() : FALLBACK;
            for (int k = 0; k < 3; k++) Falsekin.spawnDecoy(level, mob, skins.get(mob.getRandom().nextInt(skins.size())), target);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), Falsekin.soundById("entity.slime.squish"),
                    SoundSource.HOSTILE, 2.0F, 0.4F);
            return true;
        });
    }
}
