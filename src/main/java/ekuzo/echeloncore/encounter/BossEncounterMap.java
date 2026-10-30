package ekuzo.echeloncore.encounter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ekuzo.crazyworldprogression.worldboundary.restriction.Restriction;
import ekuzo.crazyworldprogression.worldboundary.restriction.RestrictionService;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.encounter.BossEncounterData.Encounter;
import ekuzo.echeloncore.encounter.BossEncounterData.State;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells clients where boss encounters are and whether their bosses are alive, for maps (CWP's optional JourneyMap
 * integration draws them, see the client's EncounterMapClient). Sent when a player joins and whenever an encounter
 * changes; a cleared encounter carries the seconds until its bosses may come back, and the client counts down itself.
 */
public final class BossEncounterMap {
    private static final Gson GSON = new Gson();
    private static boolean dirty;

    /**
     * One encounter as maps see it: its footprint ({@code round}: circle of radius {@code sizeX}, else a
     * {@code sizeX} by {@code sizeZ} rectangle), its bosses' types, and while cleared the seconds until they respawn
     * ({@code respawnIn} -1 while they are alive).
     */
    public record Summary(String id, String name, String dimension, boolean round, double x, double z, double sizeX,
                          double sizeZ, List<String> bosses, boolean alive, long respawnIn) { }

    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(EchelonCore.id("boss_encounters"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(262_144), Payload::json, Payload::new);
        @Override public Type<Payload> type() { return TYPE; }
    }

    private BossEncounterMap() { }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(server, handler.getPlayer(), json(server)));
        // Changes are sent once at the end of the tick they happened in.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!dirty) return;
            dirty = false;
            String json = json(server);
            server.getPlayerList().getPlayers().forEach(player -> send(server, player, json));
        });
    }

    /** An encounter changed (area, name, bosses, cleared or back): clients get the new list. */
    static void changed() { dirty = true; }

    private static void send(MinecraftServer server, ServerPlayer player, String json) {
        if (ServerPlayNetworking.canSend(player, Payload.TYPE)) ServerPlayNetworking.send(player, new Payload(json));
    }

    private static String json(MinecraftServer server) {
        List<Summary> summaries = new ArrayList<>();
        long now = server.overworld().getGameTime();
        for (Encounter encounter : BossEncounters.list(server)) {
            Restriction area = RestrictionService.get(server, BossEncounters.areaId(encounter.id));
            if (area == null) continue;
            boolean round = area.shape() == Restriction.Shape.SPHERE || area.shape() == Restriction.Shape.CYLINDER;
            boolean alive = encounter.state != State.CLEARED;
            summaries.add(new Summary(encounter.id.toString(), encounter.displayName(), area.dimension().toString(), round,
                    area.centerX(), area.centerZ(), area.sizeX(), round ? area.sizeX() : area.sizeZ(),
                    encounter.bosses.stream().map(boss -> boss.type().toString()).toList(), alive,
                    alive ? -1 : Math.max(0, (encounter.clearedUntil - now) / 20)));
        }
        return GSON.toJson(summaries);
    }

    public static List<Summary> decode(String json) {
        List<Summary> summaries = GSON.fromJson(json, new TypeToken<List<Summary>>() { }.getType());
        return summaries == null ? List.of() : summaries;
    }
}
