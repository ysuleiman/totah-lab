package totah.lab.hermes.rcsb;

import java.util.List;
import java.util.Objects;

/**
 * Attribute-based RCSB search over PDB entries: all conditions are
 * ANDed and matching four-character PDB entry identifiers are returned.
 * Example: all human methyltransferase structures is
 * {@code organismTaxonomy("9606")} plus {@code enzymeClass("2.1.1")}.
 */
public record RcsbAttributeSearch(
        List<RcsbAttributeCondition> conditions
) implements RcsbSearchCriteria {

    public RcsbAttributeSearch {
        conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one attribute condition is required");
        }
    }

    public RcsbAttributeSearch(RcsbAttributeCondition... conditions) {
        this(List.of(conditions));
    }
}
