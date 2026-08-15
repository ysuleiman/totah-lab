package totah.lab.prometheus.validation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A development (fitting) dataset: an ordered set of evidence hashes that a
 * strategy may freely inspect before candidate freeze.
 *
 * <p>Development evidence is kept strictly separate from holdout evidence:
 * {@link DatasetSplitter} refuses any hash that appears in both, so development
 * data can never masquerade as holdout.
 */
public final class DevelopmentDataset {

    private final String datasetId;
    private final LinkedHashSet<String> evidenceHashes;
    private final String description;

    public DevelopmentDataset(String datasetId, LinkedHashSet<String> evidenceHashes, String description) {
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

    /** Evidence hashes in insertion order; unmodifiable. */
    public Set<String> hashes() {
        return Collections.unmodifiableSet(evidenceHashes);
    }

    public boolean contains(String evidenceHash) {
        return evidenceHashes.contains(evidenceHash);
    }

    public int size() {
        return evidenceHashes.size();
    }

    public String description() {
        return description;
    }
}
