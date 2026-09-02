package ekuzo.echeloncore.veil.anomaly;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Uniform-grid broad phase containing only anomalies whose current state rejects players and interactions. */
final class VeilAnomalySpatialIndex {
    private static final int CELL_SIZE = 64;
    private final Map<Identifier, Map<Cell, List<VeilAnomaly>>> dimensions = new HashMap<>();
    private int anomalyCount;

    // Insert one rejecting anomaly into every horizontal cell overlapped by its broad-phase bounds.
    void add(VeilAnomaly anomaly) {
        AABB bounds = anomaly.bounds();
        int minX = cell(bounds.minX);
        int maxX = cell(bounds.maxX);
        int minZ = cell(bounds.minZ);
        int maxZ = cell(bounds.maxZ);
        Map<Cell, List<VeilAnomaly>> cells = dimensions.computeIfAbsent(anomaly.dimension(), ignored -> new HashMap<>());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cells.computeIfAbsent(new Cell(x, z), ignored -> new ArrayList<>()).add(anomaly);
            }
        }
        anomalyCount++;
    }

    // Return unique rejecting candidates whose indexed cells overlap one query bound.
    List<VeilAnomaly> query(Identifier dimension, AABB bounds) {
        Map<Cell, List<VeilAnomaly>> cells = dimensions.get(dimension);
        if (cells == null) return List.of();
        LinkedHashSet<VeilAnomaly> result = new LinkedHashSet<>();
        for (int x = cell(bounds.minX); x <= cell(bounds.maxX); x++) {
            for (int z = cell(bounds.minZ); z <= cell(bounds.maxZ); z++) {
                result.addAll(cells.getOrDefault(new Cell(x, z), List.of()));
            }
        }
        return List.copyOf(result);
    }

    // Report how many distinct rejection-enabled anomalies were inserted, excluding cell duplication.
    int anomalyCount() {
        return anomalyCount;
    }

    // Map one world coordinate to a stable signed grid cell.
    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    private record Cell(int x, int z) {
    }
}
