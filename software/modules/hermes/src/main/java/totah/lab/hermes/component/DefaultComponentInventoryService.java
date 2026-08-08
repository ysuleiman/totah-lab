package totah.lab.hermes.component;

import totah.lab.hermes.ccd.CcdClient;
import totah.lab.hermes.ccd.CcdDownloader;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Default implementation with deterministic ordering and opt-in downloads. */
public final class DefaultComponentInventoryService implements ComponentInventoryService {

    private static final int TOP_COMPONENTS = 15;

    private final LigandInventoryBuilder inventoryBuilder;
    private final CcdClient ccdClient;

    public DefaultComponentInventoryService() {
        this(new LigandInventoryBuilder(), new CcdDownloader());
    }

    public DefaultComponentInventoryService(
            LigandInventoryBuilder inventoryBuilder, CcdClient ccdClient) {
        this.inventoryBuilder = Objects.requireNonNull(inventoryBuilder, "inventoryBuilder");
        this.ccdClient = Objects.requireNonNull(ccdClient, "ccdClient");
    }

    @Override
    public ComponentInventoryResult build(ComponentInventoryRequest request)
            throws IOException {
        Objects.requireNonNull(request, "request");
        LigandInventory inventory = inventoryBuilder.build(request.structuresDirectory());
        List<CcdDownloader.ComponentDownload> downloads = new ArrayList<>();
        if (request.downloadCcd() && !request.dryRun()) {
            Path componentsRoot = request.outputDirectory()
                    .resolve("ligands").resolve("components");
            Files.createDirectories(componentsRoot);
            for (ComponentInventory component : inventory.components().values()) {
                if (downloadable(component.classification())) {
                    downloads.add(ccdClient.downloadComponent(component.id(), componentsRoot));
                }
            }
            inventory = attachDownloads(inventory, downloads);
        }
        return new ComponentInventoryResult(
                inventory, downloads, summarize(inventory, downloads));
    }

    private boolean downloadable(LigandClassification classification) {
        return classification == LigandClassification.ORGANIC_LIGAND
                || classification == LigandClassification.COFACTOR;
    }

    private LigandInventory attachDownloads(
            LigandInventory inventory,
            List<CcdDownloader.ComponentDownload> downloads) {
        Map<String, ComponentInventory> components = new TreeMap<>(inventory.components());
        for (CcdDownloader.ComponentDownload download : downloads) {
            ComponentInventory component = components.get(download.componentId());
            if (component != null) {
                components.put(component.id(), component.withDownloads(
                        download.ccdCif(), download.idealSdf()));
            }
        }
        return new LigandInventory(components, inventory.totalOccurrences(),
                inventory.countsByClassification());
    }

    private ComponentInventorySummary summarize(
            LigandInventory inventory,
            List<CcdDownloader.ComponentDownload> downloads) {
        EnumMap<BoundComponentOccurrence.SourceKind, Integer> bySource =
                new EnumMap<>(BoundComponentOccurrence.SourceKind.class);
        EnumMap<LigandClassification, Integer> occurrenceClasses =
                new EnumMap<>(LigandClassification.class);
        Set<String> pdbEntries = new HashSet<>();
        for (ComponentInventory component : inventory.components().values()) {
            occurrenceClasses.merge(component.classification(),
                    component.occurrenceCount(), Integer::sum);
            for (BoundComponentOccurrence occurrence : component.occurrences()) {
                bySource.merge(occurrence.sourceKind(), 1, Integer::sum);
                pdbEntries.add(occurrence.pdbId());
            }
        }
        List<ComponentInventorySummary.ComponentCount> top = inventory.components()
                .values().stream()
                .sorted(Comparator.comparingInt(ComponentInventory::occurrenceCount)
                        .reversed().thenComparing(ComponentInventory::id))
                .limit(TOP_COMPONENTS).map(this::count).toList();
        return new ComponentInventorySummary(
                inventory.totalOccurrences(), inventory.totalComponents(), pdbEntries.size(),
                bySource, inventory.countsByClassification(), occurrenceClasses,
                outcomes(downloads, true), outcomes(downloads, false), top,
                optionalCount(inventory, "SAM"), optionalCount(inventory, "SAH"));
    }

    private ComponentInventorySummary.ComponentCount optionalCount(
            LigandInventory inventory, String id) {
        ComponentInventory component = inventory.components().get(id);
        return component == null ? null : count(component);
    }

    private ComponentInventorySummary.ComponentCount count(ComponentInventory component) {
        int entries = (int) component.occurrences().stream()
                .map(BoundComponentOccurrence::pdbId).distinct().count();
        return new ComponentInventorySummary.ComponentCount(component.id(),
                component.classification(), component.occurrenceCount(), entries);
    }

    private Map<CcdDownloader.FetchStatus, Integer> outcomes(
            List<CcdDownloader.ComponentDownload> downloads, boolean cif) {
        EnumMap<CcdDownloader.FetchStatus, Integer> result =
                new EnumMap<>(CcdDownloader.FetchStatus.class);
        for (CcdDownloader.ComponentDownload download : downloads) {
            result.merge(cif ? download.ccdCifStatus() : download.idealSdfStatus(),
                    1, Integer::sum);
        }
        return result;
    }
}
