package ekuzo.echeloncore.encounter;

import ekuzo.crazyworldprogression.portals.WorkshopHelpers;
import ekuzo.crazyworldprogression.worldboundary.restriction.Restriction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss spawn points of boss encounters as CWP world helpers (F8/F9 in creative): a marker where each boss stands
 * while nobody fights it, with an arrow for its facing. The area itself is an anomaly, which CWP shows and moves.
 * IDs are "encounter_id|boss_index".
 */
final class BossSpawnHelpers implements WorkshopHelpers.Provider {
    static void register() { WorkshopHelpers.registerProvider("boss_spawn", new BossSpawnHelpers()); }

    @Override public List<WorkshopHelpers.Helper> helpers(ServerLevel level, Vec3 center, double radius) {
        List<WorkshopHelpers.Helper> result = new ArrayList<>();
        for (BossEncounterData.Encounter encounter : BossEncounters.list(level.getServer())) {
            Restriction area = BossEncounters.area(level.getServer(), encounter.id);
            if (area == null || !area.dimension().equals(level.dimension().identifier())) continue;
            for (int index = 0; index < encounter.bosses.size(); index++) {
                BossEncounterData.Boss boss = encounter.bosses.get(index);
                if (center.distanceToSqr(boss.x(), boss.y(), boss.z()) > radius * radius) continue;
                double yaw = Math.toRadians(boss.yaw());
                String name = (encounter.name.isBlank() ? encounter.id.toString() : encounter.name) + ": " + boss.type();
                result.add(WorkshopHelpers.Helper.point("boss_spawn", name, encounter.id + "|" + index,
                        boss.x(), boss.y(), boss.z()).toward(boss.x() - Math.sin(yaw) * 2, boss.y(),
                        boss.z() + Math.cos(yaw) * 2));
            }
        }
        return result;
    }

    @Override public boolean move(ServerLevel level, String id, int dx, int dy, int dz) {
        int bar = id.lastIndexOf('|');
        Identifier encounterId = bar < 0 ? null : Identifier.tryParse(id.substring(0, bar));
        BossEncounterData.Encounter encounter = encounterId == null ? null : BossEncounters.get(level.getServer(), encounterId);
        if (encounter == null) return false;
        int index;
        try { index = Integer.parseInt(id.substring(bar + 1)); }
        catch (NumberFormatException exception) { return false; }
        if (index < 0 || index >= encounter.bosses.size()) return false;
        BossEncounterData.Boss boss = encounter.bosses.get(index);
        BossEncounterData data = BossEncounterData.get(level.getServer());
        // Like adding or removing a boss: the encounter resets, so the boss comes back at its new spot.
        BossEncounters.reset(level.getServer(), data, encounter);
        encounter.bosses.set(index, new BossEncounterData.Boss(boss.type(), boss.x() + dx, boss.y() + dy, boss.z() + dz,
                boss.yaw()));
        data.changed();
        return true;
    }
}
