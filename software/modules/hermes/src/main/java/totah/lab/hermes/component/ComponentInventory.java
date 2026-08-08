package totah.lab.hermes.component;

import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Inventory of one non-polymer component: its classification, every bound
 * occurrence across the scanned structures, and the local CCD download
 * files (nullable when not downloaded).
 */
public record ComponentInventory(
        String id,
        LigandClassification classification,
        List<BoundComponentOccurrence> occurrences,
        Path ccdCif,
        Path idealSdf
) {
    public ComponentInventory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(classification, "classification");
        occurrences = List.copyOf(Objects.requireNonNull(occurrences, "occurrences"));
    }

    /** Number of occurrences of this component across all scanned files. */
    public int occurrenceCount() {
        return occurrences.size();
    }

    /** Returns a copy with the download paths set. */
    public ComponentInventory withDownloads(Path ccdCif, Path idealSdf) {
        return new ComponentInventory(id, classification, occurrences, ccdCif, idealSdf);
    }
}
