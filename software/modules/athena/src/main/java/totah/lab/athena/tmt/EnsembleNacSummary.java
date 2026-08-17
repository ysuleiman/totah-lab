package totah.lab.athena.tmt;

import java.util.List;
import java.util.OptionalDouble;

/** Replica-resolved NAC statistics. No master or substrate score is defined. */
public record EnsembleNacSummary(
        String stateId,
        EnsembleEvidenceStatus status,
        List<ReplicaNacSummary> replicas,
        OptionalDouble meanNacFraction,
        OptionalDouble betweenReplicaStandardDeviation,
        String reason) {
    public EnsembleNacSummary {
        replicas = List.copyOf(replicas);
    }
}
