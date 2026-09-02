package ekuzo.echeloncore.veil.anomaly;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import ekuzo.echeloncore.mixin.client.RenderPipelinesAccessor;
import ekuzo.echeloncore.mixin.client.RenderTypeAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cached client mesh renderer for opaque or translucent anomaly boundaries. */
public final class VeilAnomalyRenderer {
    private static final double RENDER_DISTANCE = 256.0;
    private static final double RENDER_DISTANCE_SQUARED = RENDER_DISTANCE * RENDER_DISTANCE;
    private static final int CYLINDER_SEGMENTS = 48;
    private static final int SPHERE_LONGITUDE_SEGMENTS = 32;
    private static final int SPHERE_LATITUDE_SEGMENTS = 16;
    private static final Map<Identifier, CachedMesh> CACHE = new HashMap<>();
    private static final Identifier BLACKOUT_ID = Identifier.fromNamespaceAndPath("echelon-core", "anomaly_blackout");
    private static final RenderPipeline OPAQUE_PIPELINE = RenderPipelinesAccessor.echelonCore$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("echelon-core", "pipeline/opaque_veil_anomaly"))
                    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                    .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                    .withCull(false)
                    .build());
    private static final RenderType OPAQUE_RENDER_TYPE = RenderTypeAccessor.echelonCore$create(
            "echelon_core_opaque_veil_anomaly", RenderSetup.builder(OPAQUE_PIPELINE).createRenderSetup());

    // Prevent instantiation of the anomaly renderer.
    private VeilAnomalyRenderer() {
    }

    // Register cached custom geometry submission after the level render state is collected.
    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(VeilAnomalyRenderer::submit);
        HudElementRegistry.addLast(BLACKOUT_ID, VeilAnomalyRenderer::renderBlackout);
    }

    // Cover the entire view whenever the actual camera enters a solid anomaly, independently of player position.
    private static void renderBlackout(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.gameRenderer.mainCamera().isInitialized()) return;
        ClientVeilAnomalies.AnomalyVision vision = ClientVeilAnomalies.visionAt(
                minecraft.level.dimension().identifier(), minecraft.gameRenderer.mainCamera().position());
        if (vision == ClientVeilAnomalies.AnomalyVision.BLACKOUT) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        }
    }

    // Submit nearby anomaly meshes for the current client dimension.
    private static void submit(LevelRenderContext context) {
        if (Minecraft.getInstance().level == null) return;
        Identifier dimension = Minecraft.getInstance().level.dimension().identifier();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        for (ClientVeilAnomalies.VisibleAnomaly visible : ClientVeilAnomalies.visibleAnomalies()) {
            VeilAnomaly anomaly = visible.anomaly();
            if (visible.visibility() == VeilAnomaly.Visibility.HIDDEN
                    || !anomaly.dimension().equals(dimension)
                    || anomaly.bounds().distanceToSqr(camera) > RENDER_DISTANCE_SQUARED) continue;
            CachedMesh cached = CACHE.get(anomaly.id());
            if (cached == null || cached.geometryHash() != geometryHash(anomaly)) {
                cached = new CachedMesh(geometryHash(anomaly), buildMesh(anomaly));
                CACHE.put(anomaly.id(), cached);
            }
            submitMesh(context, camera, anomaly, cached.mesh(), visible.collision(), visible.visibility());
        }
    }

    // Translate one cached local-space mesh to camera space and submit its quads.
    private static void submitMesh(LevelRenderContext context, Vec3 camera, VeilAnomaly anomaly,
                                   Mesh mesh, VeilAnomaly.Collision collision,
                                   VeilAnomaly.Visibility visibility) {
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(anomaly.centerX() - camera.x, anomaly.centerY() - camera.y, anomaly.centerZ() - camera.z);
        int alpha = visibility == VeilAnomaly.Visibility.OPAQUE ? 255 : 135;
        int color = collision == VeilAnomaly.Collision.PASSABLE && visibility == VeilAnomaly.Visibility.OPAQUE
                ? rgba(210, 92, 176, alpha) : rgba(32, 4, 48, alpha);
        RenderType renderType = visibility == VeilAnomaly.Visibility.OPAQUE
                ? OPAQUE_RENDER_TYPE : RenderTypes.debugQuads();
        context.submitNodeCollector().submitCustomGeometry(poseStack, renderType,
                (pose, vertices) -> emit(pose, vertices, mesh, color));
        poseStack.popPose();
    }

    // Emit cached quads in both winding directions so the veil is visible from either side.
    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, Mesh mesh, int color) {
        for (int index = 0; index < mesh.vertices().size(); index += 4) {
            Vertex a = mesh.vertices().get(index);
            Vertex b = mesh.vertices().get(index + 1);
            Vertex c = mesh.vertices().get(index + 2);
            Vertex d = mesh.vertices().get(index + 3);
            vertex(consumer, pose, a, color);
            vertex(consumer, pose, b, color);
            vertex(consumer, pose, c, color);
            vertex(consumer, pose, d, color);
            vertex(consumer, pose, d, color);
            vertex(consumer, pose, c, color);
            vertex(consumer, pose, b, color);
            vertex(consumer, pose, a, color);
        }
    }

    // Write one cached position with the color communicating its resolved crossing state.
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vertex vertex, int color) {
        consumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z()).setColor(color);
    }

    // Build local-space quads once for one anomaly geometry revision.
    private static Mesh buildMesh(VeilAnomaly anomaly) {
        List<Vertex> vertices = new ArrayList<>();
        switch (anomaly.shape()) {
            case CUBE -> cube(vertices, anomaly.sizeX(), anomaly.sizeY(), anomaly.sizeZ());
            case PYRAMID -> pyramid(vertices, anomaly.sizeX(), anomaly.sizeY(), anomaly.sizeZ());
            case CYLINDER -> cylinder(vertices, anomaly.sizeX(), anomaly.sizeY());
            case SPHERE -> sphere(vertices, anomaly.sizeX());
        }
        return new Mesh(List.copyOf(vertices));
    }

    // Generate six axis-aligned cube faces.
    private static void cube(List<Vertex> out, double width, double height, double depth) {
        float x = (float) (width / 2.0);
        float y = (float) (height / 2.0);
        float z = (float) (depth / 2.0);
        quad(out, v(-x,-y,-z), v(x,-y,-z), v(x,y,-z), v(-x,y,-z));
        quad(out, v(x,-y,z), v(-x,-y,z), v(-x,y,z), v(x,y,z));
        quad(out, v(-x,-y,z), v(-x,-y,-z), v(-x,y,-z), v(-x,y,z));
        quad(out, v(x,-y,-z), v(x,-y,z), v(x,y,z), v(x,y,-z));
        quad(out, v(-x,y,-z), v(x,y,-z), v(x,y,z), v(-x,y,z));
        quad(out, v(-x,-y,z), v(x,-y,z), v(x,-y,-z), v(-x,-y,-z));
    }

    // Generate four triangular sides and one square base using degenerate quads for triangle submission.
    private static void pyramid(List<Vertex> out, double width, double height, double depth) {
        float x = (float) (width / 2.0);
        float y = (float) (height / 2.0);
        float z = (float) (depth / 2.0);
        Vertex apex = v(0,y,0);
        Vertex a = v(-x,-y,-z), b = v(x,-y,-z), c = v(x,-y,z), d = v(-x,-y,z);
        quad(out, a,b,apex,apex);
        quad(out, b,c,apex,apex);
        quad(out, c,d,apex,apex);
        quad(out, d,a,apex,apex);
        quad(out, d,c,b,a);
    }

    // Generate cylindrical side quads plus top and bottom cap triangles.
    private static void cylinder(List<Vertex> out, double radius, double height) {
        float y = (float) (height / 2.0);
        for (int segment = 0; segment < CYLINDER_SEGMENTS; segment++) {
            double first = Math.PI * 2.0 * segment / CYLINDER_SEGMENTS;
            double second = Math.PI * 2.0 * (segment + 1) / CYLINDER_SEGMENTS;
            Vertex bottomA = v(Math.cos(first) * radius, -y, Math.sin(first) * radius);
            Vertex bottomB = v(Math.cos(second) * radius, -y, Math.sin(second) * radius);
            Vertex topB = v(bottomB.x(), y, bottomB.z());
            Vertex topA = v(bottomA.x(), y, bottomA.z());
            quad(out, bottomA,bottomB,topB,topA);
            quad(out, v(0,-y,0),bottomB,bottomA,bottomA);
            quad(out, v(0,y,0),topA,topB,topB);
        }
    }

    // Generate latitude-longitude sphere quads with geometry cached between frames.
    private static void sphere(List<Vertex> out, double radius) {
        for (int latitude = 0; latitude < SPHERE_LATITUDE_SEGMENTS; latitude++) {
            double latA = -Math.PI / 2.0 + Math.PI * latitude / SPHERE_LATITUDE_SEGMENTS;
            double latB = -Math.PI / 2.0 + Math.PI * (latitude + 1) / SPHERE_LATITUDE_SEGMENTS;
            for (int longitude = 0; longitude < SPHERE_LONGITUDE_SEGMENTS; longitude++) {
                double lonA = Math.PI * 2.0 * longitude / SPHERE_LONGITUDE_SEGMENTS;
                double lonB = Math.PI * 2.0 * (longitude + 1) / SPHERE_LONGITUDE_SEGMENTS;
                quad(out, sphereVertex(radius, latA, lonA), sphereVertex(radius, latA, lonB),
                        sphereVertex(radius, latB, lonB), sphereVertex(radius, latB, lonA));
            }
        }
    }

    // Convert spherical coordinates into one local mesh vertex.
    private static Vertex sphereVertex(double radius, double latitude, double longitude) {
        double horizontal = Math.cos(latitude) * radius;
        return v(Math.cos(longitude) * horizontal, Math.sin(latitude) * radius,
                Math.sin(longitude) * horizontal);
    }

    // Append one four-vertex face to a mesh under construction.
    private static void quad(List<Vertex> out, Vertex a, Vertex b, Vertex c, Vertex d) {
        out.add(a); out.add(b); out.add(c); out.add(d);
    }

    // Create one float vertex from convenient double geometry calculations.
    private static Vertex v(double x, double y, double z) {
        return new Vertex((float) x, (float) y, (float) z);
    }

    // Hash only geometry fields so state-only changes keep the cached mesh.
    private static int geometryHash(VeilAnomaly anomaly) {
        return java.util.Objects.hash(anomaly.shape(), anomaly.sizeX(), anomaly.sizeY(), anomaly.sizeZ());
    }

    // Pack separate color channels into the ARGB format accepted by VertexConsumer.
    private static int rgba(int red, int green, int blue, int alpha) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private record Vertex(float x, float y, float z) {
    }

    private record Mesh(List<Vertex> vertices) {
    }

    private record CachedMesh(int geometryHash, Mesh mesh) {
    }
}
