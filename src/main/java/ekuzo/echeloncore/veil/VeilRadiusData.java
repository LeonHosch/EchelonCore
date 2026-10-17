package ekuzo.echeloncore.veil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ekuzo.echeloncore.EchelonCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;

/** World-persistent radius pairs keyed by dimension and exact Veil Level. */
final class VeilRadiusData extends SavedData {
    static final Codec<VeilManager.Radii> RADII_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("inner").forGetter(VeilManager.Radii::inner),
            Codec.DOUBLE.fieldOf("outer").forGetter(VeilManager.Radii::outer)
    ).apply(instance, VeilManager.Radii::new));
    static final Codec<VeilRadiusData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, RADII_CODEC).optionalFieldOf("radii", Map.of())
                    .forGetter(data -> data.values)
    ).apply(instance, VeilRadiusData::new));
    static final SavedDataType<VeilRadiusData> TYPE = new SavedDataType<>(
            EchelonCore.id("veil_radii"), VeilRadiusData::new, CODEC, null);

    private final Map<String, VeilManager.Radii> values = new LinkedHashMap<>();

    VeilRadiusData() { }

    private VeilRadiusData(Map<String, VeilManager.Radii> values) {
        this.values.putAll(values);
    }

    static VeilRadiusData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    Map<String, VeilManager.Radii> values() {
        return Map.copyOf(values);
    }

    void put(String key, VeilManager.Radii radii) {
        values.put(key, radii);
        setDirty();
    }

    boolean remove(String key) {
        if (values.remove(key) == null) return false;
        setDirty();
        return true;
    }
}
