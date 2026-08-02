package totah.lab.report.validation;

import totah.lab.report.model.NarrativeFinding;
import totah.lab.report.model.PocketReport;
import totah.lab.report.narrative.PocketNarrative;

import java.util.Set;
import java.util.stream.Collectors;

public final class NarrativeEvidenceValidator {

    public void validate(PocketReport report, PocketNarrative narrative) {
        Set<String> availableEvidence = report.evidence().stream()
                .map(evidence -> evidence.id())
                .collect(Collectors.toUnmodifiableSet());

        for (NarrativeFinding finding : narrative.findings()) {
            if (finding.evidenceIds().isEmpty()
                    && finding.type()
                    != NarrativeFinding.FindingType.LIMITATION) {
                throw new IllegalArgumentException(
                        "Narrative finding has no evidence: "
                                + finding.statement());
            }
            for (String evidenceId : finding.evidenceIds()) {
                if (!availableEvidence.contains(evidenceId)) {
                    throw new IllegalArgumentException(
                            "Narrative finding references unknown evidence: "
                                    + evidenceId);
                }
            }
        }
    }
}
