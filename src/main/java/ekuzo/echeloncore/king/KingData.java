package ekuzo.echeloncore.king;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ekuzo.crazyworldprogression.progression.global.GlobalProgressionData;
import ekuzo.echeloncore.EchelonCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;
import java.util.UUID;

public final class KingData extends SavedData {
    private UUID electedKing;

    public static final Codec<KingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).optionalFieldOf("elected_king")
                    .forGetter(data -> Optional.ofNullable(data.electedKing))
    ).apply(instance, KingData::new));
    public static final SavedDataType<KingData> TYPE = new SavedDataType<>(
            EchelonCore.id("king"), KingData::new, CODEC, null);

    public KingData() { }

    private KingData(Optional<UUID> electedKing) {
        this.electedKing = electedKing.orElse(null);
    }

    static KingData get(MinecraftServer server) {
        KingData data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        if (data.electedKing == null) {
            GlobalProgressionData.get(server).takeLegacyElectedKing().ifPresent(data::setKing);
        }
        return data;
    }

    Optional<UUID> electedKing() { return Optional.ofNullable(electedKing); }
    void setKing(UUID playerUuid) { electedKing = playerUuid; setDirty(); }
    void clearKing() { electedKing = null; setDirty(); }
}

