package totah.lab.hermes.rcsb;

import java.util.Objects;

/**
 * One attribute condition of an RCSB attribute search, expressed with
 * RCSB Search API attribute names and operators. Numeric attributes
 * (resolution, entity counts) are emitted as JSON numbers; everything
 * else as text. Use the factories for the common attributes.
 */
public record RcsbAttributeCondition(
        String attribute,
        String operator,
        String value
) {
    public RcsbAttributeCondition {
        attribute = requireText(attribute, "attribute");
        operator = requireText(operator, "operator");
        if (!"exists".equals(operator)) {
            value = requireText(value, "value");
        }
    }

    /** Human is {@code "9606"}. Matches the whole taxon lineage. */
    public static RcsbAttributeCondition organismTaxonomy(String taxonId) {
        String normalized = requireText(taxonId, "taxonId");
        if (!normalized.matches("[0-9]+")) {
            throw new IllegalArgumentException(
                    "taxonId must be numeric: " + taxonId);
        }
        return new RcsbAttributeCondition(
                "rcsb_entity_source_organism.taxonomy_lineage.id",
                "exact_match",
                normalized);
    }

    /**
     * EC class or full EC number, e.g. {@code "2.1.1"} matches all
     * methyltransferases via the EC lineage.
     */
    public static RcsbAttributeCondition enzymeClass(String ecNumber) {
        String normalized = requireText(ecNumber, "ecNumber");
        if (!normalized.matches("[0-9]+(?:\\.[0-9a-zA-Z]+)*")) {
            throw new IllegalArgumentException(
                    "Invalid EC number: " + ecNumber);
        }
        return new RcsbAttributeCondition(
                "rcsb_polymer_entity.rcsb_ec_lineage.id",
                "exact_match",
                normalized);
    }

    /** E.g. {@code "X-ray"}, {@code "EM"}, {@code "NMR"}. */
    public static RcsbAttributeCondition experimentalMethod(String method) {
        return new RcsbAttributeCondition(
                "rcsb_entry_info.experimental_method",
                "exact_match",
                requireText(method, "method"));
    }

    public static RcsbAttributeCondition resolutionAtMost(double angstrom) {
        if (!Double.isFinite(angstrom) || angstrom <= 0.0) {
            throw new IllegalArgumentException(
                    "angstrom must be positive and finite");
        }
        return new RcsbAttributeCondition(
                "rcsb_entry_info.resolution_combined",
                "less_or_equal",
                Double.toString(angstrom));
    }

    /**
     * Entries with at least one non-polymer entity (ligands, ions,
     * cofactors; excludes water).
     */
    public static RcsbAttributeCondition withBoundNonPolymers() {
        return new RcsbAttributeCondition(
                "rcsb_entry_info.nonpolymer_entity_count",
                "greater",
                "0");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
