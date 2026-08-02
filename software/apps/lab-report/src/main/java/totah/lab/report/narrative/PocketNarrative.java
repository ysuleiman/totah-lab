package totah.lab.report.narrative;

import totah.lab.report.model.NarrativeFinding;

import java.util.List;
import java.util.Objects;

public record PocketNarrative(
        String executiveSummary,
        List<NarrativeFinding> findings,
        String limitations,
        String conclusions
) {
    public PocketNarrative {
        Objects.requireNonNull(executiveSummary, "executiveSummary");
        findings = List.copyOf(findings);
        Objects.requireNonNull(limitations, "limitations");
        Objects.requireNonNull(conclusions, "conclusions");
    }
}
