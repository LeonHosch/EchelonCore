package ekuzo.echeloncore.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ekuzo.echeloncore.EchelonCore;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * World-persistent boss encounters: each one's name, bosses, loot pools and aggro radius (its area is the anomaly it
 * owns, see {@link BossEncounters#areaId}), and what it was doing (idle, fighting, cleared until when) together with
 * the bosses it spawned, so a restart finds them again.
 */
final class BossEncounterData extends SavedData {
    /** One boss of an encounter: what spawns, and where it stands while nobody fights it. */
    record Boss(Identifier type, double x, double y, double z, float yaw) {
        static final Codec<Boss> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(Boss::type),
                Codec.DOUBLE.fieldOf("x").forGetter(Boss::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Boss::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Boss::z),
                Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Boss::yaw)
        ).apply(instance, Boss::new));
    }

    enum State {
        /** Bosses wait in the area; anyone may come and go. */
        IDLE,
        /** A fight is on: whoever leaves the area cannot come back until it resets. */
        ACTIVE,
        /** All bosses are dead; they come back once the respawn time is over and nobody is inside. */
        CLEARED;

        static State parse(String value) { return valueOf(value.toUpperCase(Locale.ROOT)); }

        String serializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    static final class Encounter {
        static final int DEFAULT_RESPAWN_SECONDS = 1800;
        /** Blocks from a boss within which a player starts the fight. */
        static final double DEFAULT_AGGRO_RADIUS = 12.0;
        static final Codec<Encounter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(e -> e.id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(e -> e.name),
                Boss.CODEC.listOf().optionalFieldOf("bosses", List.of()).forGetter(e -> e.bosses),
                Identifier.CODEC.listOf().optionalFieldOf("loot_pools", List.of()).forGetter(e -> e.lootPools),
                Codec.INT.optionalFieldOf("respawn_seconds", DEFAULT_RESPAWN_SECONDS).forGetter(e -> e.respawnSeconds),
                Codec.DOUBLE.optionalFieldOf("aggro_radius", DEFAULT_AGGRO_RADIUS).forGetter(e -> e.aggroRadius),
                Codec.STRING.optionalFieldOf("state", "idle").forGetter(e -> e.state.serializedName()),
                Codec.STRING.listOf().optionalFieldOf("spawned", List.of()).forGetter(e -> e.spawned.stream()
                        .map(id -> id == null ? "" : id.toString()).toList()),
                Codec.LONG.optionalFieldOf("cleared_until", 0L).forGetter(e -> e.clearedUntil)
        ).apply(instance, Encounter::new));

        final Identifier id;
        /** Shown when a player enters the area. */
        String name;
        final List<Boss> bosses;
        /** Rolled for every player inside when the last boss dies. */
        final List<Identifier> lootPools;
        int respawnSeconds;
        double aggroRadius;
        State state;
        /** The entity spawned for each boss (null: none yet, or it died). Same order as {@link #bosses}. */
        final List<UUID> spawned;
        /** Game time a cleared encounter's bosses may come back. */
        long clearedUntil;

        Encounter(Identifier id, String name) {
            this(id, name, List.of(), List.of(), DEFAULT_RESPAWN_SECONDS, DEFAULT_AGGRO_RADIUS, "idle", List.of(), 0L);
        }

        private Encounter(Identifier id, String name, List<Boss> bosses, List<Identifier> lootPools, int respawnSeconds,
                          double aggroRadius, String state, List<String> spawned, long clearedUntil) {
            this.id = id;
            this.name = name;
            this.bosses = new ArrayList<>(bosses);
            this.lootPools = new ArrayList<>(lootPools);
            this.respawnSeconds = respawnSeconds;
            this.aggroRadius = aggroRadius;
            this.state = State.parse(state);
            this.spawned = new ArrayList<>();
            for (String uuid : spawned) this.spawned.add(uuid.isEmpty() ? null : UUID.fromString(uuid));
            this.clearedUntil = clearedUntil;
            fitSpawned();
        }

        /** The name players see: the display name, or the ID when none is set. */
        String displayName() { return name.isBlank() ? id.getPath() : name; }

        /** Keeps {@link #spawned} one entry per boss. */
        void fitSpawned() {
            while (spawned.size() < bosses.size()) spawned.add(null);
            while (spawned.size() > bosses.size()) spawned.removeLast();
        }
    }

    private static final Codec<BossEncounterData> CODEC = Encounter.CODEC.listOf().optionalFieldOf("encounters", List.of())
            .xmap(BossEncounterData::new, data -> List.copyOf(data.encounters.values())).codec();
    private static final SavedDataType<BossEncounterData> TYPE = new SavedDataType<>(EchelonCore.id("boss_encounters"),
            BossEncounterData::new, CODEC, null);

    private final Map<Identifier, Encounter> encounters = new LinkedHashMap<>();

    private BossEncounterData() { }

    private BossEncounterData(List<Encounter> loaded) {
        loaded.forEach(encounter -> encounters.put(encounter.id, encounter));
    }

    static BossEncounterData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    Map<Identifier, Encounter> all() { return Collections.unmodifiableMap(encounters); }

    Encounter get(Identifier id) { return encounters.get(id); }

    void put(Encounter encounter) {
        encounters.put(encounter.id, encounter);
        changed();
    }

    Encounter remove(Identifier id) {
        Encounter removed = encounters.remove(id);
        if (removed != null) changed();
        return removed;
    }

    /** Call after changing an encounter in place (saves it, and maps get the change). */
    void changed() {
        setDirty();
        BossEncounterMap.changed();
    }
}
