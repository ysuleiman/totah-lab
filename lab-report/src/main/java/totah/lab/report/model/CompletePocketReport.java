package totah.lab.report.model;

import totah.lab.report.narrative.NarrativeProvenance;
import totah.lab.report.narrative.PocketNarrative;

import java.util.Objects;
import java.util.Optional;

public record CompletePocketReport(
        PocketReport report,
        Optional<PocketNarrative> narrative,
        Optional<NarrativeProvenance> narrativeProvenance
) {
    public CompletePocketReport {
        Objects.requireNonNull(report, "report");
        narrative = Objects.requireNonNull(narrative, "narrative");
        narrativeProvenance = Objects.requireNonNull(
                narrativeProvenance,
                "narrativeProvenance"
        );
        if (narrative.isPresent() != narrativeProvenance.isPresent()) {
            throw new IllegalArgumentException(
                    "Narrative and narrative provenance must be present together");
        }
    }

    public static CompletePocketReport withoutNarrative(PocketReport report) {
        return new CompletePocketReport(
                report,
                Optional.empty(),
                Optional.empty()
        );
    }
}
