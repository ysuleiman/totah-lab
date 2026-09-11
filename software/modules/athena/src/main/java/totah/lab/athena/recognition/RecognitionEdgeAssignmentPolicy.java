package totah.lab.athena.recognition;

import totah.lab.athena.interaction.InteractionType;

import java.util.Map;
import java.util.Objects;

/** Explicit feature correspondence and per-type geometry tolerances; contains no defaults. */
public record RecognitionEdgeAssignmentPolicy(Map<String, String> sourceToTargetFeature,
        Map<InteractionType, GeometryTolerance> geometryTolerances, String provenance) {
    public RecognitionEdgeAssignmentPolicy {
        sourceToTargetFeature = Map.copyOf(Objects.requireNonNull(sourceToTargetFeature));
        geometryTolerances = Map.copyOf(Objects.requireNonNull(geometryTolerances));
        if (provenance == null || provenance.isBlank()) throw new IllegalArgumentException("provenance required");
        if (sourceToTargetFeature.values().stream().distinct().count() != sourceToTargetFeature.size()) {
            throw new IllegalArgumentException("feature correspondence must be one-to-one");
        }
    }

    public GeometryTolerance tolerance(InteractionType type) {
        GeometryTolerance tolerance = geometryTolerances.get(type);
        if (tolerance == null) throw new IllegalArgumentException("missing geometry tolerance for " + type);
        return tolerance;
    }

    public record GeometryTolerance(double distanceAngstroms, double primaryAngleDegrees,
            double secondaryAngleDegrees) {
        public GeometryTolerance {
            require(distanceAngstroms, "distanceAngstroms");
            require(primaryAngleDegrees, "primaryAngleDegrees");
            require(secondaryAngleDegrees, "secondaryAngleDegrees");
        }
        private static void require(double value, String name) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " invalid");
        }
    }
}
