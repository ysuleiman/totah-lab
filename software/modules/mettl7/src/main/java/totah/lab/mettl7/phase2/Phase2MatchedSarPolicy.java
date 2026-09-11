package totah.lab.mettl7.phase2;

import totah.lab.athena.interaction.InteractionType;

import java.util.List;
import java.util.Set;

/** Immutable, named definitions for the frozen netarsudil Phase-2 campaign. */
public record Phase2MatchedSarPolicy(
        String id,
        Set<TypedFeature> parentFingerprint,
        Set<Integer> parentDirectContacts,
        double directContactCutoffAngstroms,
        double strainMaximumKcalMol,
        double samPairClashCutoffAngstroms,
        double samMinimumClearanceAngstroms,
        double bPreservedRmsdMaximumAngstroms,
        double bPreservedCentroidMaximumAngstroms,
        double bDistortedRmsdMaximumAngstroms,
        double bDistortedCentroidMaximumAngstroms,
        int bMinimumSeeds,
        List<Double> geometricThresholdsAngstroms,
        List<Double> networkJaccardDistanceThresholds,
        int availabilityMinimumSeeds,
        int availabilityMinimumPoses,
        int recurrenceMinimumNewSeeds,
        int recurrenceMinimumTotalSeeds,
        double convergenceDistanceChangeMaximumAngstroms,
        double convergenceDispersionChangeMaximumAngstroms,
        double convergenceBasinOccupancyChangeMaximum,
        double majorBasinMinimumOccupancy,
        double singleBasinMinimumSeedBalancedOccupancy,
        double differenceOfDifferencesEpsilon) {

    public static final String FROZEN_ID = "NETARSUDIL_PHASE2_MATCHED_NONCOVALENT_SAR_V1";
    public static final String FINGERPRINT_ID = "NETARSUDIL_B_PARENT_FINGERPRINT_V1";

    public Phase2MatchedSarPolicy {
        if (!FROZEN_ID.equals(id)) throw new IllegalArgumentException("unexpected policy id");
        parentFingerprint = Set.copyOf(parentFingerprint);
        parentDirectContacts = Set.copyOf(parentDirectContacts);
        geometricThresholdsAngstroms = List.copyOf(geometricThresholdsAngstroms);
        networkJaccardDistanceThresholds = List.copyOf(networkJaccardDistanceThresholds);
        positive(directContactCutoffAngstroms, "direct contact cutoff");
        positive(strainMaximumKcalMol, "strain maximum");
        positive(samPairClashCutoffAngstroms, "SAM clash cutoff");
        positive(samMinimumClearanceAngstroms, "SAM clearance");
        if (availabilityMinimumSeeds < 1 || availabilityMinimumPoses < 1
                || recurrenceMinimumNewSeeds < 1 || recurrenceMinimumTotalSeeds < 1) {
            throw new IllegalArgumentException("seed and pose counts must be positive");
        }
    }

    public static Phase2MatchedSarPolicy frozen() {
        return new Phase2MatchedSarPolicy(FROZEN_ID,
                Set.of(new TypedFeature(151, InteractionType.HYDROPHOBIC_CONTACT),
                        new TypedFeature(196, InteractionType.HYDROPHOBIC_CONTACT),
                        new TypedFeature(196, InteractionType.PI_CATION),
                        new TypedFeature(206, InteractionType.HYDROPHOBIC_CONTACT)),
                Set.of(29, 33, 149, 150, 151, 192, 196, 200, 201, 203, 205, 206, 207, 211),
                4.5, 15.0, 2.0, 2.5, 2.5, 2.0, 4.0, 3.0, 2,
                List.of(1.5, 2.0, 2.5, 3.0), List.of(.15, .25, .35),
                4, 8, 3, 4, .5, .5, .15, .20, .60, .20);
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    public record TypedFeature(int residueNumber, InteractionType type) {
        public TypedFeature {
            if (residueNumber < 1) throw new IllegalArgumentException("residue number must be positive");
            if (type == null) throw new NullPointerException("type");
        }
    }
}
