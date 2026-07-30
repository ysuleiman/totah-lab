package totah.lab.report.evidence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PocketEvidenceBuilder {

    private final List<ReportEvidence> evidence = new ArrayList<>();
    private final Set<String> identifiers = new LinkedHashSet<>();

    public PocketEvidenceBuilder add(
            String id,
            EvidenceCategory category,
            String statement,
            Map<String, Double> metrics
    ) {
        Objects.requireNonNull(id, "id");
        if (!identifiers.add(id)) {
            throw new IllegalArgumentException(
                    "Duplicate report evidence identifier: " + id);
        }
        evidence.add(new ReportEvidence(id, category, statement, metrics));
        return this;
    }

    public List<ReportEvidence> build() {
        return List.copyOf(evidence);
    }
}
