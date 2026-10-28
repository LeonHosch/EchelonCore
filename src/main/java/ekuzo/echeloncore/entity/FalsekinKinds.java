package ekuzo.echeloncore.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which mobs the Falsekin may wear where. A kind fits a place when the biome naturally spawns it there (so no llamas
 * in the plains); villagers fit wherever villagers live nearby. Existing herds of wearable mobs are its favourite
 * hiding places.
 */
public final class FalsekinKinds {
    /** Mob types it can wear: entity ID path in the minecraft namespace. */
    public static final List<String> WEARABLE = List.of("cow", "pig", "sheep", "chicken", "goat", "horse", "donkey",
            "llama", "villager", "fox", "rabbit", "cat", "camel", "mooshroom");

    private FalsekinKinds() { }

    public static String path(Mob mob) { return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath(); }

    public static boolean wearable(Mob mob) { return !(mob instanceof Falsekin) && WEARABLE.contains(path(mob)); }

    /** Kinds that make sense at {@code pos}: the biome's natural creature spawns, plus villagers near a village. */
    public static List<String> fitting(ServerLevel level, BlockPos pos) {
        List<String> kinds = new ArrayList<>();
        for (var entry : level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.CREATURE).unwrap()) {
            String path = BuiltInRegistries.ENTITY_TYPE.getKey(entry.value().type()).getPath();
            if (WEARABLE.contains(path) && !kinds.contains(path)) kinds.add(path);
        }
        if (!level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(32), mob -> path(mob).equals("villager")).isEmpty())
            kinds.add("villager");
        return kinds;
    }

    /** A wearable herd: at least {@code min} mobs of one kind close together. */
    public record Herd(String kind, List<Mob> members) { }

    /** Wearable herds within {@code range} of {@code center}, biggest first. */
    public static List<Herd> herds(ServerLevel level, Vec3 center, double range, int min) {
        Map<String, List<Mob>> byKind = new HashMap<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(range), FalsekinKinds::wearable))
            byKind.computeIfAbsent(path(mob), ignored -> new ArrayList<>()).add(mob);
        List<Herd> herds = new ArrayList<>();
        byKind.forEach((kind, members) -> { if (members.size() >= min) herds.add(new Herd(kind, members)); });
        herds.sort((a, b) -> b.members().size() - a.members().size());
        return herds;
    }

    /** True when {@code player} could see a point at {@code spot} (in front of them, nothing in the way). */
    public static boolean inView(ServerLevel level, Player player, Vec3 spot) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = spot.subtract(eye);
        if (player.getViewVector(1.0F).dot(to.normalize()) < 0.45) return false;
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, spot, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceTo(spot) < 1.0;
    }
}
