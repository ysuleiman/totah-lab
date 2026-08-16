package totah.lab.prometheus.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;

/**
 * A holdout (validation) dataset. The holdout is <em>opaque to strategies</em>:
 * it deliberately exposes no accessor for its hashes or for any numerical
 * evidence values. A strategy can ask for the id, the size, and whether a hash
 * is a member (for overlap checks) — nothing more.
 *
 * <p>The only way to read holdout content is {@link #revealTo(FrozenCandidate, EvidenceBundle)}:
 * a frozen candidate — the point of no return — unlocks the holdout for
 * validation. Before freeze, holdout numerical values are unreachable through
 * this API, so a strategy cannot tune against them.
 */
public final class HoldoutDataset {

    private final String datasetId;
    private final LinkedHashSet<String> evidenceHashes;
    private final String description;

    public HoldoutDataset(String datasetId, LinkedHashSet<String> evidenceHashes, String description) {
        Objects.requireNonNull(datasetId, "datasetId");
        if (datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId must be non-blank");
        }
        this.datasetId = datasetId;
        this.evidenceHashes = new LinkedHashSet<>(
                Objects.requireNonNull(evidenceHashes, "evidenceHashes"));
        this.description = Objects.requireNonNull(description, "description");
    }

    public String datasetId() {
        return datasetId;
    }

    public int size() {
        return evidenceHashes.size();
    }

    public String description() {
        return description;
    }

    /** Membership test, intended for overlap checks only. */
    public boolean containsHash(String evidenceHash) {
        return evidenceHashes.contains(evidenceHash);
    }

    /**
     * Reveals the ACCEPTED {@link QuantumEvidence} members of this holdout, in
     * holdout order. This is the single sanctioned way to read holdout content,
     * and it requires a frozen candidate: the candidate is frozen first, so the
     * revealed values can never flow back into fitting.
     *
     * @throws IllegalStateException if {@code frozen} is null — an unfrozen
     *         (mutable) candidate must never see holdout values
     */
    public List<QuantumEvidence> revealTo(FrozenCandidate frozen, EvidenceBundle bundle) {
        if (frozen == null) {
            throw new IllegalStateException(
                    "holdout content is only revealed to a frozen candidate; "
                            + "a mutable candidate must never see holdout values");
        }
        if (!datasetId.equals(frozen.plan().holdoutDatasetId())) {
            throw new IllegalArgumentException("frozen validation plan names holdout '"
                    + frozen.plan().holdoutDatasetId() + "', not '" + datasetId + "'");
        }
        Objects.requireNonNull(bundle, "bundle");
        Map<String, QuantumEvidence> byHash = new LinkedHashMap<>();
        for (QuantumEvidence evidence : bundle.quantum()) {
            byHash.putIfAbsent(evidence.identity().evidenceHash(), evidence);
        }
        List<QuantumEvidence> revealed = new ArrayList<>();
        for (String hash : evidenceHashes) {
            QuantumEvidence evidence = byHash.get(hash);
            if (evidence != null && evidence.acceptance() == EvidenceAcceptanceState.ACCEPTED) {
                revealed.add(evidence);
            }
        }
        return List.copyOf(revealed);
    }
}
