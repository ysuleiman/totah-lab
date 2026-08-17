package totah.lab.athena.fragment;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pose-specific fragment/region evidence. Dimensions remain independent by design. */
public record FragmentRegionFit(
        String fragmentId,
        String conformerId,
        String structuralRegionId,
        double fragmentVolumeFractionInRequestedRegion,
        double fragmentVolumeFractionInCommonCavity,
        double proteinClashBurdenAngstrom,
        double bottleneckCompatibilityMarginAngstrom,
        double comparatorPenaltyAngstrom,
        double anchorDistanceAngstrom,
        double anchorOrientationCosine,
        double samClearanceAngstrom,
        boolean functionalInterference,
        Set<FragmentPocketChemistry> chemistryComplement,
        List<FragmentResidueContact> residueContacts,
        List<SpatialAttachmentVector> attachmentVectors
) {
    public FragmentRegionFit {
        fragmentId = requireText(fragmentId, "fragmentId");
        conformerId = requireText(conformerId, "conformerId");
        structuralRegionId = requireText(structuralRegionId, "structuralRegionId");
        checkFraction(fragmentVolumeFractionInRequestedRegion, "fragmentVolumeFractionInRequestedRegion");
        checkFraction(fragmentVolumeFractionInCommonCavity, "fragmentVolumeFractionInCommonCavity");
        if (proteinClashBurdenAngstrom < 0.0 || anchorDistanceAngstrom < 0.0 || samClearanceAngstrom < 0.0) {
            throw new IllegalArgumentException("Distance and burden values must be non-negative");
        }
        if (anchorOrientationCosine < -1.0 || anchorOrientationCosine > 1.0) {
            throw new IllegalArgumentException("anchorOrientationCosine must be in [-1, 1]");
        }
        chemistryComplement = Set.copyOf(Objects.requireNonNull(chemistryComplement, "chemistryComplement"));
        residueContacts = List.copyOf(Objects.requireNonNull(residueContacts, "residueContacts"));
        attachmentVectors = List.copyOf(Objects.requireNonNull(attachmentVectors, "attachmentVectors"));
    }

    private static void checkFraction(double value, String name) {
        if (value < 0.0 || value > 1.0) throw new IllegalArgumentException(name + " must be in [0, 1]");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
