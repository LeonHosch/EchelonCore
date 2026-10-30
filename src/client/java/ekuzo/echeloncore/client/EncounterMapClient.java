package ekuzo.echeloncore.client;

import ekuzo.crazyworldprogression.client.map.MapData;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.encounter.BossEncounterMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boss encounters on maps: the server's encounter list becomes CWP map landmarks (drawn by CWP's optional
 * JourneyMap integration once the player has been near them). Each shows its area and a picture of its first boss,
 * greyed out with a respawn countdown while its bosses are dead.
 *
 * <p>The picture is {@code <namespace>:textures/map/<mob>.png} (32x32, with a {@code _grey} variant) when the mob's
 * mod ships one, else the mob's spawn egg (16x16, darkened while inactive), else a generic encounter skull.
 */
public final class EncounterMapClient {
    private static final Identifier FALLBACK = EchelonCore.id("textures/map/encounter.png");
    private static final Identifier FALLBACK_GREY = EchelonCore.id("textures/map/encounter_grey.png");
    private static volatile List<BossEncounterMap.Summary> summaries = List.of();
    /** When the current list arrived (for counting down respawn times). */
    private static volatile long receivedAt;
    private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();

    private EncounterMapClient() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BossEncounterMap.Payload.TYPE, (payload, context) -> {
            summaries = BossEncounterMap.decode(payload.json());
            receivedAt = System.currentTimeMillis();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> summaries = List.of());
        MapData.setLandmarkProvider(EncounterMapClient::landmarks);
    }

    private static List<MapData.Landmark> landmarks() {
        List<MapData.Landmark> out = new ArrayList<>();
        long elapsed = (System.currentTimeMillis() - receivedAt) / 1000;
        for (BossEncounterMap.Summary summary : summaries) {
            String detail;
            if (summary.alive()) {
                detail = summary.bosses().size() > 1
                        ? Component.translatable("map.echelon-core.encounter.bosses", summary.bosses().size()).getString() : "";
            } else {
                long left = Math.max(0, summary.respawnIn() - elapsed);
                detail = left > 0 ? Component.translatable("map.echelon-core.encounter.respawns_in", time(left)).getString()
                        : Component.translatable("map.echelon-core.encounter.respawns_soon").getString();
            }
            Icon icon = icon(summary.bosses().isEmpty() ? null : Identifier.tryParse(summary.bosses().getFirst()), summary.alive());
            out.add(new MapData.Landmark("encounter:" + summary.id(), summary.dimension(), summary.round(), summary.x(),
                    summary.z(), summary.sizeX(), summary.sizeZ(), summary.name(), icon.texture(), icon.size(),
                    summary.alive(), detail));
        }
        return out;
    }

    private record Icon(Identifier texture, int size) { }

    private static Icon icon(Identifier mob, boolean alive) {
        if (mob != null) {
            Identifier picture = mob.withPath("textures/map/" + mob.getPath() + (alive ? "" : "_grey") + ".png");
            if (exists(picture)) return new Icon(picture, 32);
            Identifier egg = mob.withPath("textures/item/" + mob.getPath() + "_spawn_egg.png");
            if (exists(egg)) return new Icon(egg, 16);
        }
        return new Icon(alive ? FALLBACK : FALLBACK_GREY, 32);
    }

    private static boolean exists(Identifier texture) {
        return EXISTS.computeIfAbsent(texture, id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
    }

    private static String time(long seconds) {
        long hours = seconds / 3600, minutes = seconds / 60 % 60, rest = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, rest) : String.format("%d:%02d", minutes, rest);
    }
}
