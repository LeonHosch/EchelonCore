package ekuzo.echeloncore.veil.anomaly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Persistent geometric definition and Veil-Level state sequence for one hand-placed anomaly. */
public record VeilAnomaly(Identifier id, Identifier dimension, Shape shape, double centerX, double centerY,
                          double centerZ, double sizeX, double sizeY, double sizeZ, BoundaryMode boundaryMode,
                          List<AnomalyState> states) {
    private static final Codec<Shape> SHAPE_CODEC = Codec.STRING.xmap(Shape::parse, Shape::serializedName);
    private static final Codec<BoundaryMode> BOUNDARY_MODE_CODEC = Codec.STRING.xmap(
            BoundaryMode::parse, BoundaryMode::serializedName);
    private static final Codec<Collision> COLLISION_CODEC = Codec.STRING.xmap(Collision::parse, Collision::serializedName);
    private static final Codec<Visibility> VISIBILITY_CODEC = Codec.STRING.xmap(Visibility::parse, Visibility::serializedName);
    private static final Codec<BlockInteraction> BLOCK_INTERACTION_CODEC = Codec.STRING.xmap(
            BlockInteraction::parse, BlockInteraction::serializedName);

    public static final Codec<AnomalyState> STATE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("minimum_veil_level").forGetter(AnomalyState::minimumVeilLevel),
            COLLISION_CODEC.fieldOf("collision").forGetter(AnomalyState::collision),
            VISIBILITY_CODEC.fieldOf("visibility").forGetter(AnomalyState::visibility),
            BLOCK_INTERACTION_CODEC.optionalFieldOf("block_interaction", BlockInteraction.AUTOMATIC)
                    .forGetter(AnomalyState::blockInteraction)
    ).apply(instance, AnomalyState::new));

    public static final Codec<VeilAnomaly> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(VeilAnomaly::id),
            Identifier.CODEC.fieldOf("dimension").forGetter(VeilAnomaly::dimension),
            SHAPE_CODEC.fieldOf("shape").forGetter(VeilAnomaly::shape),
            Codec.DOUBLE.fieldOf("center_x").forGetter(VeilAnomaly::centerX),
            Codec.DOUBLE.fieldOf("center_y").forGetter(VeilAnomaly::centerY),
            Codec.DOUBLE.fieldOf("center_z").forGetter(VeilAnomaly::centerZ),
            Codec.DOUBLE.fieldOf("size_x").forGetter(VeilAnomaly::sizeX),
            Codec.DOUBLE.fieldOf("size_y").forGetter(VeilAnomaly::sizeY),
            Codec.DOUBLE.fieldOf("size_z").forGetter(VeilAnomaly::sizeZ),
            BOUNDARY_MODE_CODEC.optionalFieldOf("boundary_mode", BoundaryMode.KEEP_OUT)
                    .forGetter(VeilAnomaly::boundaryMode),
            STATE_CODEC.listOf().fieldOf("states").forGetter(VeilAnomaly::states)
    ).apply(instance, VeilAnomaly::new));

    // Validate geometry and publish states in ascending threshold order.
    public VeilAnomaly {
        if (id == null || dimension == null || shape == null || boundaryMode == null) {
            throw new IllegalArgumentException("Anomaly identity and boundary mode must not be null");
        }
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Anomaly center must be finite");
        }
        if (!Double.isFinite(sizeX) || !Double.isFinite(sizeY) || !Double.isFinite(sizeZ)
                || sizeX <= 0.0 || sizeY <= 0.0 || sizeZ <= 0.0) {
            throw new IllegalArgumentException("Anomaly dimensions must be positive and finite");
        }
        states = states.stream().sorted(Comparator.comparingInt(AnomalyState::minimumVeilLevel)).toList();
        if (states.isEmpty() || states.getFirst().minimumVeilLevel() != 0) {
            throw new IllegalArgumentException("Anomaly states must begin at Veil Level 0");
        }
        for (int index = 1; index < states.size(); index++) {
            if (states.get(index - 1).minimumVeilLevel() == states.get(index).minimumVeilLevel()) {
                throw new IllegalArgumentException("Duplicate anomaly state Veil Level "
                        + states.get(index).minimumVeilLevel());
            }
        }
    }

    // Resolve the highest state threshold reached by the current server Veil Level.
    public AnomalyState stateAt(int veilLevel) {
        AnomalyState result = states.getFirst();
        for (AnomalyState state : states) {
            if (state.minimumVeilLevel() > veilLevel) break;
            result = state;
        }
        return result;
    }

    // Return a copy with one threshold inserted or replaced.
    public VeilAnomaly withState(AnomalyState replacement) {
        List<AnomalyState> updated = states.stream()
                .filter(state -> state.minimumVeilLevel() != replacement.minimumVeilLevel()).collect(java.util.stream.Collectors.toList());
        updated.add(replacement);
        return new VeilAnomaly(id, dimension, shape, centerX, centerY, centerZ, sizeX, sizeY, sizeZ,
                boundaryMode, updated);
    }

    // Return a copy without one nonzero transition threshold while preserving the required level-zero state.
    public VeilAnomaly withoutState(int minimumVeilLevel) {
        if (minimumVeilLevel == 0) throw new IllegalArgumentException("The Veil Level 0 state cannot be removed");
        List<AnomalyState> updated = states.stream()
                .filter(state -> state.minimumVeilLevel() != minimumVeilLevel).toList();
        if (updated.size() == states.size()) {
            throw new IllegalArgumentException("No anomaly state exists at Veil Level " + minimumVeilLevel);
        }
        return new VeilAnomaly(id, dimension, shape, centerX, centerY, centerZ, sizeX, sizeY, sizeZ,
                boundaryMode, updated);
    }

    // Return a copy moved to a new center and optionally a different dimension.
    public VeilAnomaly movedTo(Identifier newDimension, Vec3 center) {
        return new VeilAnomaly(id, newDimension, shape, center.x, center.y, center.z, sizeX, sizeY, sizeZ,
                boundaryMode, states);
    }

    // Return a copy with the requested one-way boundary direction while preserving geometry and states.
    public VeilAnomaly withBoundaryMode(BoundaryMode replacement) {
        return new VeilAnomaly(id, dimension, shape, centerX, centerY, centerZ, sizeX, sizeY, sizeZ,
                replacement, states);
    }

    // Compute an axis-aligned broad-phase box enclosing the rendered and protected shape.
    public AABB bounds() {
        double halfX = shape == Shape.SPHERE || shape == Shape.CYLINDER ? sizeX : sizeX / 2.0;
        double halfZ = shape == Shape.SPHERE || shape == Shape.CYLINDER ? sizeX : sizeZ / 2.0;
        double halfY = shape == Shape.SPHERE ? sizeX : sizeY / 2.0;
        return new AABB(centerX - halfX, centerY - halfY, centerZ - halfZ,
                centerX + halfX, centerY + halfY, centerZ + halfZ);
    }

    // Test an entity-center point against the shape expanded by its horizontal and vertical half-size.
    public boolean contains(Vec3 point, double horizontalMargin, double verticalMargin) {
        double x = Math.abs(point.x - centerX);
        double y = Math.abs(point.y - centerY);
        double z = Math.abs(point.z - centerZ);
        return switch (shape) {
            case SPHERE -> {
                double radius = sizeX + Math.max(horizontalMargin, verticalMargin);
                yield x * x + y * y + z * z <= radius * radius;
            }
            case CYLINDER -> x * x + z * z <= square(sizeX + horizontalMargin)
                    && y <= sizeY / 2.0 + verticalMargin;
            case CUBE -> x <= sizeX / 2.0 + horizontalMargin
                    && y <= sizeY / 2.0 + verticalMargin && z <= sizeZ / 2.0 + horizontalMargin;
            case PYRAMID -> {
                double adjustedY = y - verticalMargin;
                if (adjustedY > sizeY / 2.0) yield false;
                double fromBase = Math.max(0.0, point.y - (centerY - sizeY / 2.0));
                double scale = Math.max(0.0, 1.0 - fromBase / sizeY);
                yield x <= sizeX * scale / 2.0 + horizontalMargin
                        && z <= sizeZ * scale / 2.0 + horizontalMargin;
            }
        };
    }

    // Square one shape dimension without repeating multiplication expressions.
    private static double square(double value) {
        return value * value;
    }

    public enum Shape {
        SPHERE, CYLINDER, PYRAMID, CUBE;

        // Parse a command or persisted lowercase shape name.
        public static Shape parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        // Return the stable lowercase name written to world data and network snapshots.
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Collision {
        SOLID, PASSABLE;

        // Parse a command or persisted lowercase collision mode.
        public static Collision parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        // Return the stable lowercase collision mode.
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum BoundaryMode {
        KEEP_OUT, KEEP_IN;

        // Parse a command, persisted, or network boundary-direction name.
        public static BoundaryMode parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        // Return the stable lowercase name used by commands, world data, and snapshots.
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Visibility {
        OPAQUE, TRANSLUCENT, HIDDEN;

        // Parse a command or persisted lowercase visibility mode.
        public static Visibility parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        // Return the stable lowercase visibility mode.
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum BlockInteraction {
        AUTOMATIC, PROTECTED, ALLOWED;

        // Parse a command, persisted, or network block-interaction rule.
        public static BlockInteraction parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        // Return the stable lowercase state name.
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record AnomalyState(int minimumVeilLevel, Collision collision, Visibility visibility,
                               BlockInteraction blockInteraction) {
        // Reject state thresholds that can never match the non-negative Veil Level.
        public AnomalyState {
            if (minimumVeilLevel < 0 || collision == null || visibility == null || blockInteraction == null) {
                throw new IllegalArgumentException("Invalid anomaly state");
            }
        }

        // Resolve legacy automatic behavior or an explicit protection choice for this Veil-Level state.
        public boolean protectsBlocks() {
            return blockInteraction == BlockInteraction.PROTECTED
                    || blockInteraction == BlockInteraction.AUTOMATIC && collision == Collision.SOLID;
        }
    }
}
