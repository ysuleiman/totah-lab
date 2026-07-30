package totah.lab.report.narrative;

import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.NarrativeFinding;
import totah.lab.report.model.PocketReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Produces a deterministic, evidence-linked narrative when an external
 * language model is not configured.
 */
public final class EvidenceLinkedPocketNarrativeGenerator
        implements PocketNarrativeGenerator {

    @Override
    public PocketNarrative generate(PocketReport report) {
        Objects.requireNonNull(report, "report");
        List<NarrativeFinding> findings = report.evidence().stream()
                .filter(this::includeFinding)
                .map(this::finding)
                .toList();

        return new PocketNarrative(
                executiveSummary(report),
                findings,
                "Docking contact frequencies describe sampled poses and "
                        + "ligands under the stored scoring and contact "
                        + "definitions. They do not establish affinity, "
                        + "selectivity, inhibition, experimental binding, or "
                        + "covalent feasibility.",
                conclusion(report)
        );
    }

    private boolean includeFinding(ReportEvidence evidence) {
        return evidence.category() != EvidenceCategory.DOCKING
                || "D-001".equals(evidence.id());
    }

    private NarrativeFinding finding(ReportEvidence evidence) {
        return new NarrativeFinding(
                evidence.statement(),
                NarrativeFinding.FindingType.OBSERVATION,
                NarrativeFinding.FindingConfidence.HIGH,
                List.of(evidence.id())
        );
    }

    private String executiveSummary(PocketReport report) {
        List<String> statements = new ArrayList<>();
        first(report, EvidenceCategory.GEOMETRY)
                .ifPresent(evidence -> statements.add(evidence.statement()));
        first(report, EvidenceCategory.RESIDUE_COMPOSITION)
                .ifPresent(evidence -> statements.add(evidence.statement()));
        report.evidence().stream()
                .filter(evidence -> "D-001".equals(evidence.id()))
                .findFirst()
                .ifPresent(evidence -> statements.add(evidence.statement()));
        if (statements.isEmpty()) {
            return "The available evidence does not support a pocket "
                    + "summary.";
        }
        return String.join(" ", statements);
    }

    private String conclusion(PocketReport report) {
        List<String> hotspotStatements = report.evidence().stream()
                .filter(evidence ->
                        evidence.category() == EvidenceCategory.HOTSPOT)
                .map(ReportEvidence::statement)
                .toList();
        if (hotspotStatements.isEmpty()) {
            return "No residue-level docking signal leader could be "
                    + "identified from the supplied aggregates.";
        }
        return String.join(" ", hotspotStatements)
                + " These are descriptive candidates for follow-up, not "
                + "assigned biological roles.";
    }

    private java.util.Optional<ReportEvidence> first(
            PocketReport report,
            EvidenceCategory category
    ) {
        return report.evidence().stream()
                .filter(evidence -> evidence.category() == category)
                .findFirst();
    }
}
