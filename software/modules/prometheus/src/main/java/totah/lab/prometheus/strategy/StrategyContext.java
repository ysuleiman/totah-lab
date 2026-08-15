package totah.lab.prometheus.strategy;

import java.util.Objects;
import java.util.Optional;

import totah.lab.prometheus.candidate.ParameterCandidate;
import totah.lab.prometheus.diagnosis.DiagnosisReport;
import totah.lab.prometheus.identity.MoleculeIdentity;

/** Development-only inputs available to a parameterization strategy. */
public record StrategyContext(
        MoleculeIdentity molecule,
        DevelopmentEvidenceView evidence,
        DiagnosisReport diagnosis,
        Optional<ParameterCandidate> baselineCandidate) {

    public StrategyContext {
        Objects.requireNonNull(molecule, "molecule");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(diagnosis, "diagnosis");
        baselineCandidate = Objects.requireNonNull(baselineCandidate, "baselineCandidate");
        if (!diagnosis.molecule().equals(molecule)) {
            throw new IllegalArgumentException("diagnosis molecule does not match strategy molecule");
        }
        baselineCandidate.ifPresent(candidate -> {
            if (!candidate.molecule().equals(molecule)) {
                throw new IllegalArgumentException(
                        "baseline candidate molecule does not match strategy molecule");
            }
        });
    }
}
