package ekuzo.echeloncore.veil.anomaly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ekuzo.echeloncore.EchelonCore;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** World-persistent collection of administrator-placed Veil anomalies. */
final class VeilAnomalyData extends SavedData {
    private static final Codec<Map<String, VeilAnomaly>> ANOMALY_CODEC =
            Codec.unboundedMap(Codec.STRING, VeilAnomaly.CODEC);
    static final Codec<VeilAnomalyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ANOMALY_CODEC.optionalFieldOf("anomalies", Map.of()).forGetter(data -> data.anomalies)
    ).apply(instance, VeilAnomalyData::new));
    static final SavedDataType<VeilAnomalyData> TYPE = new SavedDataType<>(
            EchelonCore.id("veil_anomalies"), VeilAnomalyData::new, CODEC, null);

    private final Map<String, VeilAnomaly> anomalies = new LinkedHashMap<>();

    // Create empty anomaly storage for a new world.
    VeilAnomalyData() {
    }

    // Copy decoded data into deterministic mutable storage.
    private VeilAnomalyData(Map<String, VeilAnomaly> anomalies) {
        this.anomalies.putAll(anomalies);
    }

    // Load the server-wide anomaly data from overworld persistence.
    static VeilAnomalyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // Return an immutable insertion-ordered anomaly snapshot.
    Map<String, VeilAnomaly> values() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(anomalies));
    }

    // Store or replace one anomaly and schedule world data for saving.
    void put(VeilAnomaly anomaly) {
        anomalies.put(anomaly.id().toString(), anomaly);
        setDirty();
    }

    // Remove one anomaly by ID and mark changed storage dirty.
    VeilAnomaly remove(Identifier id) {
        VeilAnomaly removed = anomalies.remove(id.toString());
        if (removed != null) setDirty();
        return removed;
    }
}
