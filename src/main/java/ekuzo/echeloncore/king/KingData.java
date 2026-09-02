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

    // Create an unappointed king record for a new world.
    public KingData() { }

    // Restore the optional elected UUID decoded from EchelonCore's SavedData file.
    private KingData(Optional<UUID> electedKing) {
        this.electedKing = electedKing.orElse(null);
    }

    // Load the king record and consume the legacy CWP king field once when upgrading an existing world.
    static KingData get(MinecraftServer server) {
        KingData data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        if (data.electedKing == null) {
            GlobalProgressionData.get(server).takeLegacyElectedKing().ifPresent(data::setKing);
        }
        return data;
    }

    // Expose the current appointment without leaking a nullable UUID.
    Optional<UUID> electedKing() { return Optional.ofNullable(electedKing); }

    // Replace any previous king and mark the world record for saving.
    void setKing(UUID playerUuid) { electedKing = playerUuid; setDirty(); }

    // Clear the appointment and mark the world record for saving.
    void clearKing() { electedKing = null; setDirty(); }
}

