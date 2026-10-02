package totah.lab.mnemosyne;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Thin read-only entry point; callers explicitly choose snapshots and reload captured views. */
public final class EvidenceQueries {
    private final EvidenceSnapshotCatalog catalog;

    /** Opens an existing dedicated catalog directory without creating or changing storage. */
    public EvidenceQueries(Path directory) throws IOException {
        catalog = new EvidenceSnapshotCatalog(Objects.requireNonNull(directory));
    }

    /** Fresh validation followed by immutable roots/parents/children/ancestry/descendants/tips queries. */
    public EvidenceLineage lineage() throws IOException {
        return EvidenceLineage.load(catalog);
    }

    /** No default snapshot. Preserves branch-local state, provenance and checked integrity failures. */
    public EvidenceBranchView evidence(ScientificReference snapshot) throws IOException {
        return EvidenceBranchView.load(catalog, snapshot);
    }
}
