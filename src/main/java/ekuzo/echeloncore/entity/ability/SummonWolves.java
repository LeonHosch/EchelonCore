package ekuzo.echeloncore.entity.ability;

import ekuzo.crazyworldprogression.api.mobforge.MobAbility;
import ekuzo.crazyworldprogression.api.mobforge.MobAbilityContext;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.ShadowWolf;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * Summon Wolves: smoke gathers around the caster and Shadow Wolves step out of it. The Leshen calls them when it enters
 * its second phase; other mobs can carry this ability too.
 */
public final class SummonWolves implements MobAbility {
    public static final Identifier ID = EchelonCore.id("summon_wolves");
    private static final int MAX_WOLVES = 3;

    @Override public long cooldownTicks() { return 900; }

    @Override public boolean canCast(MobAbilityContext context) {
        Entity caster = context.world().getEntity(context.caster().uuid());
        return caster instanceof Mob mob && !Busy.is(mob) && context.target() instanceof LivingEntity target && target.isAlive()
                && wolves(context.world(), mob) < MAX_WOLVES;
    }

    @Override public void execute(MobAbilityContext context) {
        Mob mob = (Mob) context.world().getEntity(context.caster().uuid());
        summon(context.world(), mob, (LivingEntity) context.target(), 2);
    }

    /** Summons up to {@code count} wolves around the caster (never more than three alive at once). */
    public static void summon(ServerLevel level, Mob mob, LivingEntity target, int count) {
        int room = Math.max(0, MAX_WOLVES - wolves(level, mob));
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.6F);
        for (int i = 0; i < Math.min(count, room); i++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double x = mob.getX() + Math.cos(angle) * 2.5;
            double z = mob.getZ() + Math.sin(angle) * 2.5;
            BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(x, mob.getY(), z));
            double y = Math.abs(ground.getY() - mob.getY()) <= 3 ? ground.getY() : mob.getY();
            ShadowWolf.summon(level, mob, target, x, y, z);
        }
    }

    public static int wolves(ServerLevel level, Mob mob) {
        return level.getEntitiesOfClass(ShadowWolf.class, new AABB(mob.blockPosition()).inflate(48),
                wolf -> wolf.isAlive() && wolf.summonedBy(mob)).size();
    }
}
