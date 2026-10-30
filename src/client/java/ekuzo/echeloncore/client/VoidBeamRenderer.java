package ekuzo.echeloncore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ekuzo.echeloncore.entity.VoidWraith;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Void Wraith's laser, drawn on the client with the Stormcaller chain lightning's render type: a straight, steady
 * beam from the star core in its chest. While it charges, a thin violet line that slowly grows wider and brighter;
 * once locked, brighter still and white-violet at its center; when it fires, a thick beam of purple light with a
 * bright core that fades quickly.
 */
final class VoidBeamRenderer {
    private static final long FIRE_MS = 450;
    private static final long CHARGE_MS = VoidWraith.CHARGE_TICKS * 50L;
    private static final Map<Integer, Beam> BEAMS = new ConcurrentHashMap<>();

    private VoidBeamRenderer() { }

    /**
     * One wraith's beam: its phase, the point it aims through (and the one before, to glide between them), when that
     * point arrived, when it started charging and when the phase last changed.
     */
    private record Beam(int phase, Vec3 aim, Vec3 previousAim, long aimAt, long chargeStart, long phaseStart) {
        /** The aim point at this moment: moved smoothly from the previous one over one tick. */
        Vec3 aimNow(long now) {
            return previousAim.lerp(aim, Math.min(1.0, (now - aimAt) / 50.0));
        }
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(VoidWraith.BeamPayload.TYPE, (payload, context) -> {
            long now = System.currentTimeMillis();
            Vec3 end = new Vec3(payload.x(), payload.y(), payload.z());
            if (payload.phase() == VoidWraith.BeamPayload.CANCELED) {
                BEAMS.remove(payload.entityId());
                return;
            }
            Beam previous = BEAMS.get(payload.entityId());
            boolean continued = previous != null && previous.phase() != VoidWraith.BeamPayload.FIRED;
            long chargeStart = continued ? previous.chargeStart() : now;
            long phaseStart = previous != null && previous.phase() == payload.phase() ? previous.phaseStart() : now;
            Vec3 from = continued ? previous.aimNow(now) : end;
            BEAMS.put(payload.entityId(), new Beam(payload.phase(), end, from, now, chargeStart, phaseStart));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BEAMS.clear());
        LevelRenderEvents.COLLECT_SUBMITS.register(VoidBeamRenderer::submit);
    }

    private static void submit(LevelRenderContext context) {
        if (BEAMS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long now = System.currentTimeMillis();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack stack = context.poseStack();
        for (var entry : BEAMS.entrySet()) {
            Beam beam = entry.getValue();
            var entity = minecraft.level.getEntity(entry.getKey());
            boolean gone = !(entity instanceof VoidWraith wraith) || wraith.isRemoved();
            if (beam.phase() == VoidWraith.BeamPayload.FIRED ? now - beam.phaseStart() > FIRE_MS
                    : gone || now - beam.chargeStart() > CHARGE_MS + 1500) {
                BEAMS.remove(entry.getKey());
                continue;
            }
            if (gone && beam.phase() != VoidWraith.BeamPayload.FIRED) continue;
            float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 aim = beam.aimNow(now);
            Vec3 start = gone ? aim : coreAt((VoidWraith) entity, partial);
            Vec3 direction = aim.subtract(start);
            if (direction.lengthSqr() < 1.0E-4) continue;
            Vec3 end = start.add(direction.normalize().scale(VoidWraith.BEAM_LENGTH));
            Vec3 from = start.subtract(camera), to = end.subtract(camera);
            stack.pushPose();
            context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.lightning(), (pose, vertices) -> {
                switch (beam.phase()) {
                    case VoidWraith.BeamPayload.CHARGING -> {
                        float progress = Math.min(1, (now - beam.chargeStart()) / (float) CHARGE_MS);
                        strip(pose, vertices, from, to, 0.05F + 0.06F * progress, argb(0.15F + 0.2F * progress, 0x7A1FD6));
                        strip(pose, vertices, from, to, 0.015F + 0.015F * progress, argb(0.55F + 0.3F * progress, 0xC77DFF));
                        orb(pose, vertices, from, camera, 0.12F + 0.25F * progress, argb(0.6F + 0.3F * progress, 0xD9A6FF));
                    }
                    case VoidWraith.BeamPayload.LOCKED -> {
                        strip(pose, vertices, from, to, 0.12F, argb(0.4F, 0x8A2BE2));
                        strip(pose, vertices, from, to, 0.035F, argb(1.0F, 0xF5E6FF));
                        orb(pose, vertices, from, camera, 0.4F, argb(0.9F, 0xF0D9FF));
                    }
                    default -> {
                        float fade = 1 - (now - beam.phaseStart()) / (float) FIRE_MS;
                        float swell = 1 + 0.4F * (1 - fade);
                        strip(pose, vertices, from, to, 0.55F * swell, argb(0.35F * fade, 0x5A12A8));
                        strip(pose, vertices, from, to, 0.28F * swell, argb(0.7F * fade, 0x9B3DFF));
                        strip(pose, vertices, from, to, 0.09F, argb(fade, 0xFFF0FF));
                        orb(pose, vertices, from, camera, 0.7F * swell, argb(0.8F * fade, 0xE6C2FF));
                    }
                }
            });
            stack.popPose();
        }
    }

    private static Vec3 coreAt(VoidWraith wraith, float partial) {
        Vec3 now = wraith.core(), moved = wraith.position().subtract(wraith.getPosition(partial));
        return now.subtract(moved);
    }

    /** Two crossed flat strips from {@code from} to {@code to}, one of them turned to face the camera. */
    private static void strip(PoseStack.Pose pose, VertexConsumer vertices, Vec3 from, Vec3 to, float width, int color) {
        Vec3 direction = to.subtract(from);
        Vec3 side = direction.cross(from.add(to).scale(0.5));
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(width);
        quad(pose, vertices, from, to, side, color);
        Vec3 other = direction.cross(side);
        if (other.lengthSqr() > 1.0E-8) quad(pose, vertices, from, to, other.normalize().scale(width), color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices, Vec3 from, Vec3 to, Vec3 side, int color) {
        vertices.addVertex(pose, (float) (from.x + side.x), (float) (from.y + side.y), (float) (from.z + side.z)).setColor(color);
        vertices.addVertex(pose, (float) (from.x - side.x), (float) (from.y - side.y), (float) (from.z - side.z)).setColor(color);
        vertices.addVertex(pose, (float) (to.x - side.x), (float) (to.y - side.y), (float) (to.z - side.z)).setColor(color);
        vertices.addVertex(pose, (float) (to.x + side.x), (float) (to.y + side.y), (float) (to.z + side.z)).setColor(color);
    }

    /** A glowing point at the star core: a small camera-facing square (camera-relative position). */
    private static void orb(PoseStack.Pose pose, VertexConsumer vertices, Vec3 at, Vec3 camera, float size, int color) {
        Vec3 toCamera = at.scale(-1);
        if (toCamera.lengthSqr() < 1.0E-6) return;
        Vec3 right = toCamera.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
        right = right.normalize().scale(size);
        Vec3 up = right.cross(toCamera).normalize().scale(size);
        vertices.addVertex(pose, (float) (at.x - right.x - up.x), (float) (at.y - right.y - up.y), (float) (at.z - right.z - up.z)).setColor(color);
        vertices.addVertex(pose, (float) (at.x + right.x - up.x), (float) (at.y + right.y - up.y), (float) (at.z + right.z - up.z)).setColor(color);
        vertices.addVertex(pose, (float) (at.x + right.x + up.x), (float) (at.y + right.y + up.y), (float) (at.z + right.z + up.z)).setColor(color);
        vertices.addVertex(pose, (float) (at.x - right.x + up.x), (float) (at.y - right.y + up.y), (float) (at.z - right.z + up.z)).setColor(color);
    }

    private static int argb(float alpha, int rgb) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24 | rgb;
    }
}
