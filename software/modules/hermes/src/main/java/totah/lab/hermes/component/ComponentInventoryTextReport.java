package totah.lab.hermes.component;

import totah.lab.hermes.ccd.CcdDownloader;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

/** Stable human-readable projection of {@link ComponentInventorySummary}. */
public final class ComponentInventoryTextReport {

    private ComponentInventoryTextReport() {
    }

    public static String format(ComponentInventorySummary summary) {
        StringBuilder text = new StringBuilder("Component inventory summary\n")
                .append("===========================\n")
                .append("Total non-polymer occurrences: ").append(summary.totalOccurrences())
                .append('\n').append("Distinct component IDs: ")
                .append(summary.distinctComponents()).append('\n')
                .append("Distinct PDB entries: ").append(summary.distinctEntries()).append('\n');
        text.append("\nOccurrences by source:\n");
        for (BoundComponentOccurrence.SourceKind source
                : BoundComponentOccurrence.SourceKind.values()) {
            text.append("  ").append(source).append(": ")
                    .append(summary.occurrencesBySource().getOrDefault(source, 0))
                    .append('\n');
        }
        text.append("\nComponents / occurrences by classification:\n");
        for (LigandClassification classification : LigandClassification.values()) {
            text.append("  ").append(classification).append(": ")
                    .append(summary.componentsByClassification()
                            .getOrDefault(classification, 0)).append(" / ")
                    .append(summary.occurrencesByClassification()
                            .getOrDefault(classification, 0)).append('\n');
        }
        appendOutcomes(text, "CCD CIF", summary.ccdCifOutcomes());
        appendOutcomes(text, "Ideal SDF", summary.idealSdfOutcomes());
        text.append("\nTop components by occurrence:\n");
        summary.topComponents().forEach(component -> text.append("  ")
                .append(component.componentId()).append(": ")
                .append(component.occurrences()).append(" (")
                .append(component.classification()).append(")\n"));
        appendSpecial(text, "SAM", summary.sam());
        appendSpecial(text, "SAH", summary.sah());
        return text.toString();
    }

    private static void appendOutcomes(StringBuilder text, String label,
            java.util.Map<CcdDownloader.FetchStatus, Integer> outcomes) {
        text.append('\n').append(label).append(" outcomes:\n");
        for (CcdDownloader.FetchStatus status : CcdDownloader.FetchStatus.values()) {
            text.append("  ").append(status).append(": ")
                    .append(outcomes.getOrDefault(status, 0)).append('\n');
        }
    }

    private static void appendSpecial(StringBuilder text, String id,
            ComponentInventorySummary.ComponentCount count) {
        text.append('\n').append(id).append(": ");
        if (count == null) {
            text.append("not present\n");
        } else {
            text.append(count.occurrences()).append(" occurrences in ")
                    .append(count.pdbEntries()).append(" PDB entries\n");
        }
    }
}
