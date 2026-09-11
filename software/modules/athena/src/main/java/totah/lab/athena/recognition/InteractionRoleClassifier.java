package totah.lab.athena.recognition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Basin-aware role classification, independent of experimental selectivity labels. */
public final class InteractionRoleClassifier {
    public Result classify(RecognitionBasin basin, MaterialContributionEvidence material) {
        return classify(basin, basin.recurrent(), material);
    }

    /** Additive recurrence-aware path for execution results using member/seed/run recurrence. */
    public Result classify(RecognitionBasin basin, boolean recurrent,
            MaterialContributionEvidence material) {
        Objects.requireNonNull(basin); Objects.requireNonNull(material);
        Map<RecognitionEdge.Key, InteractionRole> roles = new LinkedHashMap<>();
        Set<RecognitionEdge.Key> all = new java.util.LinkedHashSet<>(basin.persistentEdges());
        all.addAll(basin.variableEdges());
        boolean adequate = recurrent && basin.evidenceQuality() == EvidenceQuality.ADEQUATE
                && material.alternativeBasinEvidenceAdequate();
        for (RecognitionEdge.Key edge : all) {
            InteractionRole role = !adequate ? InteractionRole.UNRESOLVED
                    : basin.persistentEdges().contains(edge) && material.identityDiscriminatingEdges().contains(edge)
                    ? InteractionRole.DEFINING
                    : basin.persistentEdges().contains(edge) ? InteractionRole.SUPPORTING
                    : InteractionRole.INCIDENTAL;
            roles.put(edge, role);
        }
        return new Result(roles, material.provenance());
    }
    public record MaterialContributionEvidence(Set<RecognitionEdge.Key> identityDiscriminatingEdges,
            boolean alternativeBasinEvidenceAdequate, String provenance) {
        public MaterialContributionEvidence {
            identityDiscriminatingEdges = Set.copyOf(identityDiscriminatingEdges);
            if (provenance == null || provenance.isBlank()) throw new IllegalArgumentException("provenance required");
        }
    }
    public record Result(Map<RecognitionEdge.Key, InteractionRole> roles, String provenance) {
        public Result { roles = Map.copyOf(roles); }
    }
}
