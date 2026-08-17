package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies the v1 hard physicochemical discovery-space bounds. */
public final class PhysicochemicalGate {

    public record Policy(
            double minimumMolecularWeight,
            double maximumMolecularWeight,
            int minimumFormalCharge,
            int maximumFormalCharge,
            int maximumHydrogenBondDonors,
            int minimumHydrogenBondAcceptors,
            int maximumHydrogenBondAcceptors,
            int maximumRotatableBonds,
            double minimumTpsa,
            double maximumTpsa,
            double minimumLogP,
            double maximumLogP,
            int maximumAromaticRings,
            int preferredMaximumAromaticRings,
            int minimumHeavyAtoms,
            int maximumHeavyAtoms,
            double preferredMinimumFractionSp3) {

        public static Policy mettl7Discovery() {
            return new Policy(150.0, 425.0, -1, 1, 3, 1, 7, 6,
                    20.0, 100.0, 0.5, 4.5, 3, 2, 10, 30, 0.20);
        }
    }

    public record Descriptors(
            double molecularWeight,
            int formalCharge,
            int hydrogenBondDonors,
            int hydrogenBondAcceptors,
            int rotatableBonds,
            double tpsa,
            double logP,
            int aromaticRings,
            int heavyAtoms,
            double fractionSp3) {

        public Descriptors {
            requireFiniteNonNegative(molecularWeight, "molecularWeight");
            requireNonNegative(hydrogenBondDonors, "hydrogenBondDonors");
            requireNonNegative(hydrogenBondAcceptors, "hydrogenBondAcceptors");
            requireNonNegative(rotatableBonds, "rotatableBonds");
            requireFiniteNonNegative(tpsa, "tpsa");
            if (!Double.isFinite(logP)) {
                throw new IllegalArgumentException("logP must be finite");
            }
            requireNonNegative(aromaticRings, "aromaticRings");
            requireNonNegative(heavyAtoms, "heavyAtoms");
            if (!Double.isFinite(fractionSp3)
                    || fractionSp3 < 0.0 || fractionSp3 > 1.0) {
                throw new IllegalArgumentException(
                        "fractionSp3 must be between 0 and 1");
            }
        }
    }

    public record Result(boolean accepted, List<String> reasons,
                         List<String> preferences) {
        public Result {
            reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
            preferences = List.copyOf(Objects.requireNonNull(
                    preferences, "preferences"));
            if (accepted == !reasons.isEmpty()) {
                throw new IllegalArgumentException(
                        "accepted must be equivalent to having no reasons");
            }
        }
    }

    private final Policy policy;

    public PhysicochemicalGate() {
        this(Policy.mettl7Discovery());
    }

    public PhysicochemicalGate(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public Result evaluate(Descriptors value) {
        Objects.requireNonNull(value, "value");
        List<String> reasons = new ArrayList<>();
        outside(value.molecularWeight(), policy.minimumMolecularWeight(),
                policy.maximumMolecularWeight(),
                "molecular weight must be 150-425 Da", reasons);
        if (value.formalCharge() < policy.minimumFormalCharge()
                || value.formalCharge() > policy.maximumFormalCharge()) {
            reasons.add("formal charge must be -1, 0, or +1");
        }
        outside(value.hydrogenBondDonors(), 0, policy.maximumHydrogenBondDonors(),
                "H-bond donors must be 0-3", reasons);
        outside(value.hydrogenBondAcceptors(), policy.minimumHydrogenBondAcceptors(),
                policy.maximumHydrogenBondAcceptors(),
                "H-bond acceptors must be 1-7", reasons);
        if (value.rotatableBonds() > policy.maximumRotatableBonds()) {
            reasons.add("rotatable bonds must be at most 6");
        }
        outside(value.tpsa(), policy.minimumTpsa(), policy.maximumTpsa(),
                "TPSA must be 20-100 A^2", reasons);
        outside(value.logP(), policy.minimumLogP(), policy.maximumLogP(),
                "calculated logP must be 0.5-4.5", reasons);
        if (value.aromaticRings() > policy.maximumAromaticRings()) {
            reasons.add("aromatic rings must be at most 3");
        }
        outside(value.heavyAtoms(), policy.minimumHeavyAtoms(),
                policy.maximumHeavyAtoms(),
                "heavy atoms must be 10-30", reasons);

        List<String> preferences = new ArrayList<>();
        if (value.aromaticRings() > policy.preferredMaximumAromaticRings()) {
            preferences.add("three aromatic rings: above preferred maximum of two");
        }
        if (value.fractionSp3() < policy.preferredMinimumFractionSp3()) {
            preferences.add("fraction sp3 below preferred 0.20");
        }
        return new Result(reasons.isEmpty(), reasons, preferences);
    }

    private static void outside(double value, double minimum, double maximum,
                                String reason, List<String> reasons) {
        if (value < minimum || value > maximum) {
            reasons.add(reason);
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
