package totah.lab.mettl7.phase2;

import totah.lab.athena.ligand.screening.CanonicalPocketGate;
import totah.lab.athena.ligand.screening.PoseReproducibilityGate;
import totah.lab.athena.ligand.screening.SamCompatibilityGate;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic decision rules for the frozen Phase-2 and bounded A audit. */
public final class Phase2DecisionRules {
    private Phase2DecisionRules() { }

    public enum BState { PRESERVED, DISTORTED, LOST, NEW_BASIN }
    public enum AModel { RECURRENT_SINGLE_REROUTE_BASIN, RECURRENT_MULTIPLE_REROUTE_BASINS,
        HETEROGENEOUS_NON_B_ENSEMBLE, INSUFFICIENT_SAMPLING }
    public enum Differential { PRESERVED, GAINED_IN_B, LOST_IN_B, AVOIDED_IN_A, CHANGED, UNCHANGED }

    public static boolean physicalPass(PhysicalEvidence evidence, Phase2MatchedSarPolicy policy) {
        return evidence.strainKcalMol() <= policy.strainMaximumKcalMol()
                && evidence.proteinSevereClashCount() == 0
                && evidence.samPairsBelowClashCutoff() == 0
                && evidence.samMinimumDistanceAngstroms() >= policy.samMinimumClearanceAngstroms()
                && evidence.canonicalPocket().accepted()
                && evidence.reproducibility().accepted()
                && evidence.samCompatibility().accepted();
    }

    public static BState bState(double rmsd, double centroid, int seeds,
                                boolean recurrentNonParentBasin, Phase2MatchedSarPolicy policy) {
        if (rmsd <= policy.bPreservedRmsdMaximumAngstroms()
                && centroid <= policy.bPreservedCentroidMaximumAngstroms()
                && seeds >= policy.bMinimumSeeds()) return BState.PRESERVED;
        if (rmsd <= policy.bDistortedRmsdMaximumAngstroms()
                && centroid <= policy.bDistortedCentroidMaximumAngstroms()) return BState.DISTORTED;
        return recurrentNonParentBasin ? BState.NEW_BASIN : BState.LOST;
    }

    public static boolean availability(int physicalPoses, Set<Integer> seeds,
                                       Phase2MatchedSarPolicy policy) {
        return physicalPoses >= policy.availabilityMinimumPoses()
                && seeds.size() >= policy.availabilityMinimumSeeds();
    }

    public static boolean recurrent(Set<Integer> newSeeds, Set<Integer> allSeeds,
                                    Phase2MatchedSarPolicy policy) {
        return newSeeds.size() >= policy.recurrenceMinimumNewSeeds()
                && allSeeds.size() >= policy.recurrenceMinimumTotalSeeds();
    }

    public static boolean converged(ConvergenceEvidence evidence, Phase2MatchedSarPolicy policy) {
        return evidence.medianDistanceChangeAngstroms() <= policy.convergenceDistanceChangeMaximumAngstroms()
                && evidence.medianPairwiseRmsdChangeAngstroms() <= policy.convergenceDispersionChangeMaximumAngstroms()
                && evidence.majorBasinOccupancyChanges().stream().allMatch(change ->
                        change <= policy.convergenceBasinOccupancyChangeMaximum())
                && !evidence.lateNonrecurrentBLikeState();
    }

    public static AModel aModel(AEnsembleEvidence evidence, Phase2MatchedSarPolicy policy) {
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.availabilityPass() || !evidence.convergencePass()) return AModel.INSUFFICIENT_SAMPLING;
        if (evidence.recurrentBLikeBasin()) return AModel.INSUFFICIENT_SAMPLING;
        if (evidence.recurrentNonBBasinsAtAdjacentThresholds() >= 2) return AModel.RECURRENT_MULTIPLE_REROUTE_BASINS;
        if (evidence.recurrentNonBBasinsAtAdjacentThresholds() == 1
                && evidence.dominantSeedBalancedOccupancy() >= policy.singleBasinMinimumSeedBalancedOccupancy())
            return AModel.RECURRENT_SINGLE_REROUTE_BASIN;
        if (evidence.recurrentInteractionNetworkAtAdjacentThresholds()
                && evidence.geometricallyDistributed()) return AModel.HETEROGENEOUS_NON_B_ENSEMBLE;
        return AModel.INSUFFICIENT_SAMPLING;
    }

    public static double differenceOfDifferences(double parentB, double analogueB,
                                                 double parentA, double analogueA) {
        return (analogueB - parentB) - (analogueA - parentA);
    }

    public static Differential classifyDifferenceOfDifferences(double value,
                                                               Phase2MatchedSarPolicy policy) {
        if (value > policy.differenceOfDifferencesEpsilon()) return Differential.GAINED_IN_B;
        if (value < -policy.differenceOfDifferencesEpsilon()) return Differential.AVOIDED_IN_A;
        return Differential.UNCHANGED;
    }

    public record PhysicalEvidence(double strainKcalMol, int proteinSevereClashCount,
                                   int samPairsBelowClashCutoff, double samMinimumDistanceAngstroms,
                                   CanonicalPocketGate.Result canonicalPocket,
                                   PoseReproducibilityGate.Result reproducibility,
                                   SamCompatibilityGate.Result samCompatibility) {
        public PhysicalEvidence {
            Objects.requireNonNull(canonicalPocket, "canonicalPocket");
            Objects.requireNonNull(reproducibility, "reproducibility");
            Objects.requireNonNull(samCompatibility, "samCompatibility");
        }
    }
    public record ConvergenceEvidence(double medianDistanceChangeAngstroms,
                                      double medianPairwiseRmsdChangeAngstroms,
                                      List<Double> majorBasinOccupancyChanges,
                                      boolean lateNonrecurrentBLikeState) {
        public ConvergenceEvidence { majorBasinOccupancyChanges = List.copyOf(majorBasinOccupancyChanges); }
    }
    public record AEnsembleEvidence(boolean availabilityPass, boolean convergencePass,
                                    boolean recurrentBLikeBasin,
                                    int recurrentNonBBasinsAtAdjacentThresholds,
                                    double dominantSeedBalancedOccupancy,
                                    boolean recurrentInteractionNetworkAtAdjacentThresholds,
                                    boolean geometricallyDistributed) { }
}
