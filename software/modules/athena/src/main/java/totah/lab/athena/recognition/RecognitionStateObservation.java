package totah.lab.athena.recognition;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** One observed pose/state and its non-thermodynamic provenance. */
public record RecognitionStateObservation(String poseId, RecognitionGraph graph,
        String seedId, String runId, Optional<String> familyId,
        String ligandFeatureMapProvenance, EvidenceQuality quality,
        List<RecognitionConstraint> constraints) {
    public RecognitionStateObservation {
        require(poseId, "poseId"); Objects.requireNonNull(graph); require(seedId, "seedId");
        require(runId, "runId"); familyId = Objects.requireNonNull(familyId);
        require(ligandFeatureMapProvenance, "ligandFeatureMapProvenance"); Objects.requireNonNull(quality);
        constraints = List.copyOf(Objects.requireNonNull(constraints));
        if (!graph.provenance().poseId().equals(poseId)) throw new IllegalArgumentException("pose id mismatch");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
    }
}
