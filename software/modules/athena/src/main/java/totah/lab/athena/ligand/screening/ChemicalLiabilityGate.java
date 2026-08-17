package totah.lab.athena.ligand.screening;

import java.util.List;
import java.util.Objects;

/** Rejects candidates with machine-readable upstream chemistry liabilities. */
public final class ChemicalLiabilityGate {

    public enum Liability {
        PAINS,
        AGGREGATION_PRONE,
        NONSPECIFIC_ALKYLATOR,
        UNSTABLE_ELECTROPHILE,
        STRONG_REDOX_CYCLER,
        REACTIVE_MICHAEL_ACCEPTOR,
        ACID_CHLORIDE,
        SULFONYL_CHLORIDE,
        NONSPECIFIC_ISOCYANATE_OR_ISOTHIOCYANATE,
        PEROXIDE,
        DIAZO_GROUP,
        HIGHLY_STRAINED_REACTIVE_RING,
        POLYPHENOLIC_NUISANCE,
        NONSPECIFIC_CHELATOR,
        DETERGENT_LIKE_AMPHIPHILE
    }

    public record Finding(Liability code, String reason) {
        public Finding {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }

    public record Result(boolean accepted, List<Finding> findings) {
        public Result {
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
            if (accepted == !findings.isEmpty()) {
                throw new IllegalArgumentException(
                        "accepted must be equivalent to having no findings");
            }
        }
    }

    public Result evaluate(List<Finding> findings) {
        List<Finding> copy = List.copyOf(Objects.requireNonNull(findings, "findings"));
        return new Result(copy.isEmpty(), copy);
    }
}
