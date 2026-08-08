package totah.lab.hermes.component;

import totah.lab.hermes.ccd.CcdDownloader;

import java.util.List;
import java.util.Objects;

/** Complete in-memory result of inventory, classification, and optional acquisition. */
public record ComponentInventoryResult(
        LigandInventory inventory,
        List<CcdDownloader.ComponentDownload> downloads,
        ComponentInventorySummary summary
) {
    public ComponentInventoryResult {
        Objects.requireNonNull(inventory, "inventory");
        downloads = List.copyOf(Objects.requireNonNull(downloads, "downloads"));
        Objects.requireNonNull(summary, "summary");
    }
}
