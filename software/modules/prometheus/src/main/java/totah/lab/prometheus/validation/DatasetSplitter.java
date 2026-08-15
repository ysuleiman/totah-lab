package totah.lab.prometheus.validation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;

/**
 * Splits an {@link EvidenceBundle} into a development dataset and a holdout
 * dataset, enforcing the Prometheus separation rules structurally:
 *
 * <ul>
 *   <li>no hash may appear in both datasets — development data cannot
 *       masquerade as holdout;</li>
 *   <li>every hash must exist in the bundle;</li>
 *   <li>only ACCEPTED, CONVERGED evidence may enter a validation dataset —
 *       geometry-invalid or numerically failed evidence is refused by name;</li>
 *   <li>the holdout must be non-empty.</li>
 * </ul>
 */
public final class DatasetSplitter {

    private DatasetSplitter() {
    }

    /** The two halves of a split: development (visible) and holdout (opaque). */
    public record DatasetSplit(DevelopmentDataset development, HoldoutDataset holdout) {

        public DatasetSplit {
            Objects.requireNonNull(development, "development");
            Objects.requireNonNull(holdout, "holdout");
        }
    }

    /**
     * Splits {@code bundle} into development and holdout datasets by evidence hash.
     *
     * @throws IllegalArgumentException if dev and holdout overlap, if a hash is
     *         unknown to the bundle, if a member is not ACCEPTED and CONVERGED,
     *         or if the holdout is empty
     */
    public static DatasetSplit split(
            EvidenceBundle bundle,
            String devId,
            Collection<String> devHashes,
            String holdoutId,
            Collection<String> holdoutHashes) {

        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(devHashes, "devHashes");
        Objects.requireNonNull(holdoutHashes, "holdoutHashes");

        LinkedHashSet<String> dev = new LinkedHashSet<>(devHashes);
        LinkedHashSet<String> holdout = new LinkedHashSet<>(holdoutHashes);

        Set<String> overlap = new LinkedHashSet<>(dev);
        overlap.retainAll(holdout);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                    "development data cannot masquerade as holdout: " + overlap);
        }
        if (holdout.isEmpty()) {
            throw new IllegalArgumentException("holdout dataset must be non-empty");
        }

        Map<String, QuantumEvidence> quantumByHash = new LinkedHashMap<>();
        for (QuantumEvidence evidence : bundle.quantum()) {
            quantumByHash.putIfAbsent(evidence.identity().evidenceHash(), evidence);
        }
        Map<String, ClassicalEvidence> classicalByHash = new LinkedHashMap<>();
        for (ClassicalEvidence evidence : bundle.classical()) {
            classicalByHash.putIfAbsent(evidence.identity().evidenceHash(), evidence);
        }

        for (String hash : union(dev, holdout)) {
            QuantumEvidence quantum = quantumByHash.get(hash);
            if (quantum != null) {
                if (quantum.acceptance() != EvidenceAcceptanceState.ACCEPTED
                        || quantum.convergence() != ConvergenceStatus.CONVERGED) {
                    throw new IllegalArgumentException(
                            "evidence " + hash + " cannot enter a validation dataset: "
                                    + "acceptance=" + quantum.acceptance()
                                    + ", convergence=" + quantum.convergence()
                                    + " (geometry-invalid or failed evidence is not admissible)");
                }
                continue;
            }
            ClassicalEvidence classical = classicalByHash.get(hash);
            if (classical != null) {
                if (classical.acceptance() != EvidenceAcceptanceState.ACCEPTED) {
                    throw new IllegalArgumentException(
                            "evidence " + hash + " cannot enter a validation dataset: "
                                    + "acceptance=" + classical.acceptance());
                }
                continue;
            }
            throw new IllegalArgumentException(
                    "unknown evidence hash " + hash + ": not present in the bundle");
        }

        return new DatasetSplit(
                new DevelopmentDataset(devId, dev, ""),
                new HoldoutDataset(holdoutId, holdout, ""));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new LinkedHashSet<>(a);
        all.addAll(b);
        return all;
    }
}
