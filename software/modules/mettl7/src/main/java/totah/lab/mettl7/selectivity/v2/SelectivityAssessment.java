package totah.lab.mettl7.selectivity.v2;

import java.util.List;
import java.util.Objects;

public record SelectivityAssessment(
        String identifier,
        String rulesetVersion,
        SelectivityClass selectivity,
        ProductivityClass productivity,
        List<String> reasons,
        String provenance) {
    public SelectivityAssessment {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(rulesetVersion, "rulesetVersion");
        Objects.requireNonNull(selectivity, "selectivity");
        Objects.requireNonNull(productivity, "productivity");
        reasons = List.copyOf(reasons);
        Objects.requireNonNull(provenance, "provenance");
    }
}
