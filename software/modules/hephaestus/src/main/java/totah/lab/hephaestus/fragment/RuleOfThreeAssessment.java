package totah.lab.hephaestus.fragment;

import java.util.LinkedHashSet;
import java.util.Set;

/** Deterministic Congreve-style Rule-of-Three assessment. */
public record RuleOfThreeAssessment(boolean satisfies, Set<String> failedCriteria) {
    public RuleOfThreeAssessment {
        failedCriteria = Set.copyOf(failedCriteria);
        if (satisfies == !failedCriteria.isEmpty()) {
            throw new IllegalArgumentException("satisfies must agree with failedCriteria");
        }
    }

    public static RuleOfThreeAssessment assess(FragmentDescriptors descriptors) {
        var failures = new LinkedHashSet<String>();
        if (descriptors.molecularWeight() > 300.0) failures.add("MOLECULAR_WEIGHT_GT_300");
        if (descriptors.calculatedLogP() > 3.0) failures.add("CLOGP_GT_3");
        if (descriptors.hydrogenBondDonors() > 3) failures.add("HBD_GT_3");
        if (descriptors.hydrogenBondAcceptors() > 3) failures.add("HBA_GT_3");
        if (descriptors.rotatableBonds() > 3) failures.add("ROTATABLE_BONDS_GT_3");
        return new RuleOfThreeAssessment(failures.isEmpty(), failures);
    }
}
