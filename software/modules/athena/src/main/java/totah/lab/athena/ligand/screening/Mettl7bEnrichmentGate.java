package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stage-2 METTL7B hypothesis enrichment, kept separate from chemical quality. */
public final class Mettl7bEnrichmentGate {

    public enum Cohort {
        DRUG_LIKE,
        FRAGMENT
    }

    public record Evidence(
            PhysicochemicalGate.Descriptors descriptors,
            int maximumFusedAromaticRingSystem,
            boolean directionalPolarFeature,
            boolean flatNonpolarNuisance) {

        public Evidence {
            Objects.requireNonNull(descriptors, "descriptors");
            if (maximumFusedAromaticRingSystem < 0) {
                throw new IllegalArgumentException(
                        "maximumFusedAromaticRingSystem must be non-negative");
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

    public Result evaluate(Cohort cohort, Evidence evidence) {
        Objects.requireNonNull(cohort, "cohort");
        Objects.requireNonNull(evidence, "evidence");
        return switch (cohort) {
            case DRUG_LIKE -> evaluateDrugLike(evidence);
            case FRAGMENT -> evaluateFragment(evidence);
        };
    }

    private static Result evaluateDrugLike(Evidence evidence) {
        PhysicochemicalGate.Descriptors value = evidence.descriptors();
        List<String> reasons = new ArrayList<>();
        outside(value.molecularWeight(), 180.0, 340.0, "MW_180_340", reasons);
        maximum(value.aromaticRings(), 2, "AROMATIC_RINGS_GT_2", reasons);
        maximum(value.rotatableBonds(), 4, "ROTATABLE_BONDS_GT_4", reasons);
        minimum(value.fractionSp3(), .35, "FSP3_LT_0_35", reasons);
        outside(value.tpsa(), 25.0, 80.0, "TPSA_25_80", reasons);
        outside(value.logP(), 1.0, 3.5, "CLOGP_1_3_5", reasons);
        maximum(value.hydrogenBondDonors(), 2, "HBD_GT_2", reasons);
        outside(value.hydrogenBondAcceptors(), 1, 5, "HBA_1_5", reasons);
        maximum(evidence.maximumFusedAromaticRingSystem(), 1,
                "FUSED_POLYAROMATIC_SYSTEM", reasons);
        require(evidence.directionalPolarFeature(),
                "DIRECTIONAL_POLAR_FEATURE_REQUIRED", reasons);
        return new Result(reasons.isEmpty(), reasons, List.of());
    }

    private static Result evaluateFragment(Evidence evidence) {
        PhysicochemicalGate.Descriptors value = evidence.descriptors();
        List<String> reasons = new ArrayList<>();
        maximum(value.rotatableBonds(), 3, "ROTATABLE_BONDS_GT_3", reasons);
        maximum(value.aromaticRings(), 1, "AROMATIC_RINGS_GT_1", reasons);
        require(evidence.directionalPolarFeature(),
                "DIRECTIONAL_POLAR_FEATURE_REQUIRED", reasons);
        require(!evidence.flatNonpolarNuisance(),
                "FLAT_NONPOLAR_NUISANCE", reasons);
        List<String> preferences = new ArrayList<>();
        if (value.fractionSp3() < .20) {
            preferences.add("FSP3_BELOW_SECONDARY_PREFERENCE_0_20");
        } else if (value.fractionSp3() < .30) {
            preferences.add("FSP3_BELOW_PRIMARY_PREFERENCE_0_30");
        }
        return new Result(reasons.isEmpty(), reasons, preferences);
    }

    private static void require(boolean condition, String reason,
                                List<String> reasons) {
        if (!condition) {
            reasons.add(reason);
        }
    }

    private static void minimum(double value, double minimum, String reason,
                                List<String> reasons) {
        require(value >= minimum, reason, reasons);
    }

    private static void maximum(double value, double maximum, String reason,
                                List<String> reasons) {
        require(value <= maximum, reason, reasons);
    }

    private static void outside(double value, double minimum, double maximum,
                                String reason, List<String> reasons) {
        require(value >= minimum && value <= maximum, reason, reasons);
    }
}
