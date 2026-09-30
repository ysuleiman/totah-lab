package totah.lab.mettl7.phase2;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.screening.CanonicalPocketGate;
import totah.lab.athena.ligand.screening.PoseReproducibilityGate;
import totah.lab.athena.ligand.screening.SamCompatibilityGate;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Phase2MatchedSarPolicyTest {
    private final Phase2MatchedSarPolicy policy = Phase2MatchedSarPolicy.frozen();

    @Test void freezesExactFingerprintAndContactShell() {
        assertThat(policy.parentFingerprint()).hasSize(4);
        assertThat(policy.parentDirectContacts()).containsExactlyInAnyOrder(
                29, 33, 149, 150, 151, 192, 196, 200, 201, 203, 205, 206, 207, 211);
    }

    @Test void physicalGateIncludesItsBoundaries() {
        var boundary = physical(15, 0, 0, 2.5);
        assertThat(Phase2DecisionRules.physicalPass(boundary, policy)).isTrue();
        assertThat(Phase2DecisionRules.physicalPass(
                physical(Math.nextUp(15.0), 0, 0, 2.5), policy)).isFalse();
        assertThat(Phase2DecisionRules.physicalPass(
                physical(15, 0, 0, Math.nextDown(2.5)), policy)).isFalse();
    }

    @Test void fixedFrameRmsdDoesNotSuperimposeAndChoosesSymmetryMinimum() {
        var parent = List.of(new Point3D(0, 0, 0), new Point3D(2, 0, 0));
        var translatedAndSwapped = List.of(new Point3D(3, 0, 0), new Point3D(1, 0, 0));
        var result = Phase2PoseMetrics.fixedFrameCommonCore(parent, translatedAndSwapped,
                List.of(new int[]{0, 1}, new int[]{1, 0}));
        assertThat(result.rmsdAngstroms()).isEqualTo(1.0);
        assertThat(result.centroidShiftAngstroms()).isEqualTo(1.0);
        assertThat(result.symmetryMapping()).containsExactly(1, 0);
    }

    @Test void fixedFrameRejectsInvalidMapping() {
        assertThatThrownBy(() -> Phase2PoseMetrics.fixedFrameCommonCore(
                List.of(new Point3D(0, 0, 0)), List.of(new Point3D(0, 0, 0)), List.of(new int[]{1})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void availabilityAndRecurrenceUseFrozenInclusiveCounts() {
        assertThat(Phase2DecisionRules.availability(8, Set.of(1, 2, 3, 4), policy)).isTrue();
        assertThat(Phase2DecisionRules.availability(7, Set.of(1, 2, 3, 4), policy)).isFalse();
        assertThat(Phase2DecisionRules.recurrent(Set.of(4, 5, 6), Set.of(1, 4, 5, 6), policy)).isTrue();
        assertThat(Phase2DecisionRules.recurrent(Set.of(4, 5), Set.of(1, 2, 4, 5), policy)).isFalse();
    }

    @Test void convergenceUsesAllFrozenBoundaries() {
        var pass = new Phase2DecisionRules.ConvergenceEvidence(.5, .5, List.of(.15, 0.0), false);
        assertThat(Phase2DecisionRules.converged(pass, policy)).isTrue();
        assertThat(Phase2DecisionRules.converged(
                new Phase2DecisionRules.ConvergenceEvidence(.5, .5, List.of(Math.nextUp(.15)), false), policy)).isFalse();
        assertThat(Phase2DecisionRules.converged(
                new Phase2DecisionRules.ConvergenceEvidence(.5, .5, List.of(), true), policy)).isFalse();
    }

    @Test void modelDecisionTreeIsDeterministic() {
        assertThat(Phase2DecisionRules.aModel(new Phase2DecisionRules.AEnsembleEvidence(
                true, true, false, 1, .60, false, false), policy))
                .isEqualTo(Phase2DecisionRules.AModel.RECURRENT_SINGLE_REROUTE_BASIN);
        assertThat(Phase2DecisionRules.aModel(new Phase2DecisionRules.AEnsembleEvidence(
                true, true, false, 2, .20, true, true), policy))
                .isEqualTo(Phase2DecisionRules.AModel.RECURRENT_MULTIPLE_REROUTE_BASINS);
        assertThat(Phase2DecisionRules.aModel(new Phase2DecisionRules.AEnsembleEvidence(
                true, true, false, 0, .20, true, true), policy))
                .isEqualTo(Phase2DecisionRules.AModel.HETEROGENEOUS_NON_B_ENSEMBLE);
        assertThat(Phase2DecisionRules.aModel(new Phase2DecisionRules.AEnsembleEvidence(
                true, true, true, 1, .80, true, false), policy))
                .isEqualTo(Phase2DecisionRules.AModel.INSUFFICIENT_SAMPLING);
    }

    @Test void differenceOfDifferencesIsHandVerifiable() {
        assertThat(Phase2DecisionRules.differenceOfDifferences(.8, .6, .5, .1)).isCloseTo(.2,
                org.assertj.core.data.Offset.offset(1e-12));
        assertThat(Phase2DecisionRules.classifyDifferenceOfDifferences(Math.nextUp(.2), policy))
                .isEqualTo(Phase2DecisionRules.Differential.GAINED_IN_B);
        assertThat(Phase2DecisionRules.classifyDifferenceOfDifferences(.2, policy))
                .isEqualTo(Phase2DecisionRules.Differential.UNCHANGED);
    }

    @Test void completeLinkAndSeedOccupancyAreHandVerifiable() {
        var poses = List.of(new Phase2EnsembleAnalyzer.PoseIdentity("p1", 1, false),
                new Phase2EnsembleAnalyzer.PoseIdentity("p2", 2, false),
                new Phase2EnsembleAnalyzer.PoseIdentity("p3", 3, true));
        var matrix = List.of(List.of(0.0, 1.0, 4.0), List.of(1.0, 0.0, 3.0), List.of(4.0, 3.0, 0.0));
        var basins = Phase2EnsembleAnalyzer.completeLink(poses, matrix, 2.0, Set.of(1, 2, 3), policy);
        assertThat(basins).hasSize(2);
        assertThat(basins.getFirst().memberIndices()).containsExactly(0, 1);
        assertThat(Phase2EnsembleAnalyzer.seedBalancedOccupancy(poses, Set.of(1, 2, 3, 4),
                Phase2EnsembleAnalyzer.PoseIdentity::bLike)).isEqualTo(.25);
    }

    @Test void growthVectorReportsIndependentClearances() {
        var result = RegionAGrowthVector.evaluate("REGION_A_VECTOR_V1", new Point3D(0, 0, 0),
                new Point3D(1, 0, 0), List.of(new Point3D(3, 0, 0)), List.of(new Point3D(1, 4, 0)));
        assertThat(result.vectorLengthAngstroms()).isEqualTo(1);
        assertThat(result.proteinClearanceAngstroms()).isEqualTo(2);
        assertThat(result.samClearanceAngstroms()).isEqualTo(4);
    }

    private static Phase2DecisionRules.PhysicalEvidence physical(double strain, int proteinClashes,
                                                                  int samClashes, double samDistance) {
        return new Phase2DecisionRules.PhysicalEvidence(strain, proteinClashes, samClashes, samDistance,
                new CanonicalPocketGate.Result(true, List.of()),
                new PoseReproducibilityGate.Result(true, List.of()),
                new SamCompatibilityGate.Result(true, List.of()));
    }
}
