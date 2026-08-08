package totah.lab.hermes.component;

import totah.lab.hermes.ccd.CcdDownloader;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Structured, deterministic summary of an inventory and its acquisition outcomes. */
public record ComponentInventorySummary(
        int totalOccurrences,
        int distinctComponents,
        int distinctEntries,
        Map<BoundComponentOccurrence.SourceKind, Integer> occurrencesBySource,
        Map<LigandClassification, Integer> componentsByClassification,
        Map<LigandClassification, Integer> occurrencesByClassification,
        Map<CcdDownloader.FetchStatus, Integer> ccdCifOutcomes,
        Map<CcdDownloader.FetchStatus, Integer> idealSdfOutcomes,
        List<ComponentCount> topComponents,
        ComponentCount sam,
        ComponentCount sah
) {
    public ComponentInventorySummary {
        occurrencesBySource = immutableEnumMap(BoundComponentOccurrence.SourceKind.class,
                occurrencesBySource);
        componentsByClassification = immutableEnumMap(LigandClassification.class,
                componentsByClassification);
        occurrencesByClassification = immutableEnumMap(LigandClassification.class,
                occurrencesByClassification);
        ccdCifOutcomes = immutableEnumMap(CcdDownloader.FetchStatus.class, ccdCifOutcomes);
        idealSdfOutcomes = immutableEnumMap(CcdDownloader.FetchStatus.class, idealSdfOutcomes);
        topComponents = List.copyOf(Objects.requireNonNull(topComponents, "topComponents"));
    }

    private static <E extends Enum<E>> Map<E, Integer> immutableEnumMap(
            Class<E> type, Map<E, Integer> source) {
        EnumMap<E, Integer> copy = new EnumMap<>(type);
        copy.putAll(Objects.requireNonNull(source, "summary map"));
        return Collections.unmodifiableMap(copy);
    }

    public record ComponentCount(
            String componentId,
            LigandClassification classification,
            int occurrences,
            int pdbEntries
    ) {
    }
}
