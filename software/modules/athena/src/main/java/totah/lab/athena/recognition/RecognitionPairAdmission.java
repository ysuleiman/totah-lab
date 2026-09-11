package totah.lab.athena.recognition;

import java.util.List;

/** Explicit, evidence-retaining pair-membership decision. */
public record RecognitionPairAdmission(boolean geometryThresholdPassed,
        boolean topologyThresholdPassed, boolean stableFeatureReroutePresent,
        boolean matchedEdgeGeometryPassed, boolean topologyRelevantAmbiguityPresent,
        EvidenceQuality evidenceQuality, boolean sameBasinAdmissible,
        List<String> reasons, String provenance) {
    public RecognitionPairAdmission {
        reasons = List.copyOf(reasons);
        if (evidenceQuality == null) throw new IllegalArgumentException("evidenceQuality required");
        if (provenance == null || provenance.isBlank()) throw new IllegalArgumentException("provenance required");
        boolean expected = geometryThresholdPassed && topologyThresholdPassed
                && !stableFeatureReroutePresent && matchedEdgeGeometryPassed
                && !topologyRelevantAmbiguityPresent;
        if (sameBasinAdmissible != expected) throw new IllegalArgumentException("admission contradicts components");
    }
}
