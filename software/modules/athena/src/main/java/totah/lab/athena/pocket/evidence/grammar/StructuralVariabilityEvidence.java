package totah.lab.athena.pocket.evidence.grammar;

import totah.lab.athena.pocket.evidence.EvaluationStatus;

import java.util.Objects;
import java.util.OptionalDouble;

/** Structural dispersion for one UniProt position across experimental observations. */
public record StructuralVariabilityEvidence(
        EvaluationStatus status,
        int observationCount,
        OptionalDouble caRmsfAngstroms,
        OptionalDouble sideChainCentroidRmsfAngstroms,
        String method,
        String methodVersion,
        String reason) {

    public StructuralVariabilityEvidence {
        Objects.requireNonNull(status);
        Objects.requireNonNull(caRmsfAngstroms);
        Objects.requireNonNull(sideChainCentroidRmsfAngstroms);
        Objects.requireNonNull(method);
        Objects.requireNonNull(methodVersion);
        Objects.requireNonNull(reason);
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be non-negative");
        }
        if (status != EvaluationStatus.PRESENT
                && (caRmsfAngstroms.isPresent()
                || sideChainCentroidRmsfAngstroms.isPresent())) {
            throw new IllegalArgumentException(
                    "Unavailable variability cannot carry coordinate values");
        }
    }
}
