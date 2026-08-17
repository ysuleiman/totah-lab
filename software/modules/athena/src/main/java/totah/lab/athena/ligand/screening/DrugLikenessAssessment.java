package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Advisory Lipinski Rule-of-5 and explicitly tagged fragment Rule-of-3 evidence. */
public final class DrugLikenessAssessment {

    public enum Pool {
        PRIMARY_DRUG_LIKE,
        COMPACT_FRAGMENT
    }

    public record Violation(String property, double observed,
                            double limit, boolean preferredCriterion) {
        public Violation {
            Objects.requireNonNull(property, "property");
            if (property.isBlank() || !Double.isFinite(observed)
                    || !Double.isFinite(limit)) {
                throw new IllegalArgumentException("invalid violation evidence");
            }
        }
    }

    public record Lipinski(double molecularWeight, double clogP,
                           int hBondDonors, int hBondAcceptors,
                           List<Violation> violations) {
        public Lipinski {
            violations = List.copyOf(violations);
        }

        public boolean preferred() {
            return violations.size() <= 1;
        }
    }

    public record FragmentRuleOf3(boolean applicable, boolean passes,
                                  List<Violation> violations) {
        public FragmentRuleOf3 {
            violations = List.copyOf(violations);
            if (!applicable && (!passes || !violations.isEmpty())) {
                throw new IllegalArgumentException(
                        "non-applicable Rule-of-3 must pass with no violations");
            }
        }
    }

    public record Result(Pool pool, Lipinski lipinski,
                         FragmentRuleOf3 fragmentRuleOf3) {
        public Result {
            Objects.requireNonNull(pool, "pool");
            Objects.requireNonNull(lipinski, "lipinski");
            Objects.requireNonNull(fragmentRuleOf3, "fragmentRuleOf3");
        }
    }

    public Result assess(PhysicochemicalGate.Descriptors value, Pool pool) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(pool, "pool");
        List<Violation> ro5 = new ArrayList<>();
        violation(value.molecularWeight(), 500.0, "molecularWeight", false, ro5);
        violation(value.logP(), 5.0, "clogP", false, ro5);
        violation(value.hydrogenBondDonors(), 5.0, "hBondDonors", false, ro5);
        violation(value.hydrogenBondAcceptors(), 10.0, "hBondAcceptors", false, ro5);
        Lipinski lipinski = new Lipinski(value.molecularWeight(), value.logP(),
                value.hydrogenBondDonors(), value.hydrogenBondAcceptors(), ro5);

        if (pool != Pool.COMPACT_FRAGMENT) {
            return new Result(pool, lipinski,
                    new FragmentRuleOf3(false, true, List.of()));
        }
        List<Violation> ro3 = new ArrayList<>();
        violation(value.molecularWeight(), 300.0, "molecularWeight", false, ro3);
        violation(value.logP(), 3.0, "clogP", false, ro3);
        violation(value.hydrogenBondDonors(), 3.0, "hBondDonors", false, ro3);
        violation(value.hydrogenBondAcceptors(), 3.0, "hBondAcceptors", false, ro3);
        violation(value.rotatableBonds(), 3.0, "rotatableBonds", true, ro3);
        violation(value.tpsa(), 60.0, "tpsa", true, ro3);
        return new Result(pool, lipinski,
                new FragmentRuleOf3(true, ro3.isEmpty(), ro3));
    }

    private static void violation(double observed, double limit,
                                  String property, boolean preferred,
                                  List<Violation> violations) {
        if (observed > limit) {
            violations.add(new Violation(property, observed, limit, preferred));
        }
    }
}
