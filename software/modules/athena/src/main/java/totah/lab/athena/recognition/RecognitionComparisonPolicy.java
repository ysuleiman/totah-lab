package totah.lab.athena.recognition;

import totah.lab.gaia.structure.ResidueId;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Explicit caller-owned classifications; the comparator introduces no scientific defaults. */
public record RecognitionComparisonPolicy(
        Set<RecognitionEdge.Key> incompatibleTargetKeys,
        Map<RecognitionEdge.Key, InteractionRole> sourceRoles) {
    public RecognitionComparisonPolicy {
        incompatibleTargetKeys = Set.copyOf(Objects.requireNonNull(incompatibleTargetKeys,
                "incompatibleTargetKeys"));
        sourceRoles = Map.copyOf(Objects.requireNonNull(sourceRoles, "sourceRoles"));
    }

    public static RecognitionComparisonPolicy evidenceOnly() {
        return new RecognitionComparisonPolicy(Set.of(), Map.of());
    }

    public InteractionRole roleOf(RecognitionEdge edge) {
        return sourceRoles.getOrDefault(edge.key(), edge.role());
    }

    public boolean explicitlyIncompatible(String feature, ResidueId mappedResidue,
            totah.lab.athena.interaction.InteractionType type) {
        return incompatibleTargetKeys.contains(new RecognitionEdge.Key(feature, mappedResidue, type));
    }
}
