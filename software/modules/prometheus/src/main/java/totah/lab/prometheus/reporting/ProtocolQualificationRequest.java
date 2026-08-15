package totah.lab.prometheus.reporting;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Immutable inputs for a two-geometry energy/force protocol qualification. */
public record ProtocolQualificationRequest(
        Path outputDirectory,
        Path specificationManifest,
        Path controlGeometry,
        Path controlResult,
        Path controlGradient,
        Path problemGeometry,
        Path historicalProblemResult,
        Path newProblemResult,
        Path selectionEvidence,
        Map<String, Integer> localAtomIndicesOneBased) {

    public ProtocolQualificationRequest {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(specificationManifest, "specificationManifest");
        Objects.requireNonNull(controlGeometry, "controlGeometry");
        Objects.requireNonNull(controlResult, "controlResult");
        Objects.requireNonNull(controlGradient, "controlGradient");
        Objects.requireNonNull(problemGeometry, "problemGeometry");
        Objects.requireNonNull(historicalProblemResult, "historicalProblemResult");
        Objects.requireNonNull(newProblemResult, "newProblemResult");
        Objects.requireNonNull(selectionEvidence, "selectionEvidence");
        localAtomIndicesOneBased = Map.copyOf(Objects.requireNonNull(localAtomIndicesOneBased,
                "localAtomIndicesOneBased"));
        if (localAtomIndicesOneBased.isEmpty()) {
            throw new IllegalArgumentException("at least one local atom is required");
        }
    }
}
