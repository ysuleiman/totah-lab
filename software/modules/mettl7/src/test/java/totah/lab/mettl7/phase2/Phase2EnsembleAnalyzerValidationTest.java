package totah.lab.mettl7.phase2;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Phase2EnsembleAnalyzerValidationTest {
    private static final Phase2MatchedSarPolicy POLICY = Phase2MatchedSarPolicy.frozen();
    private static final List<Phase2EnsembleAnalyzer.PoseIdentity> POSES = List.of(
            new Phase2EnsembleAnalyzer.PoseIdentity("a", 1, false),
            new Phase2EnsembleAnalyzer.PoseIdentity("b", 2, false));

    @Test void acceptsValidSymmetricMatrixAndToleranceBoundary() {
        assertThat(cluster(List.of(List.of(1.0e-9, 0.0), List.of(Math.nextDown(1.0e-9), 0.0)))).hasSize(1);
    }

    @Test void rejectsAsymmetryAndNonzeroDiagonalOutsideTolerance() {
        assertThatThrownBy(() -> cluster(List.of(List.of(0.0, 0.0), List.of(Math.nextUp(1.0e-9), 0.0))))
                .hasMessageContaining("symmetric");
        assertThatThrownBy(() -> cluster(List.of(List.of(Math.nextUp(1.0e-9), 1.0), List.of(1.0, 0.0))))
                .hasMessageContaining("diagonal");
    }

    @Test void rejectsNegativeNanInfinityAndDimensionMismatch() {
        assertThatThrownBy(() -> cluster(List.of(List.of(0.0, -1.0), List.of(-1.0, 0.0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cluster(List.of(List.of(0.0, Double.NaN), List.of(Double.NaN, 0.0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cluster(List.of(List.of(0.0, Double.POSITIVE_INFINITY), List.of(Double.POSITIVE_INFINITY, 0.0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cluster(List.of(List.of(0.0), List.of(0.0)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsDuplicateAndBlankPoseIds() {
        var duplicate = List.of(new Phase2EnsembleAnalyzer.PoseIdentity("same", 1, false),
                new Phase2EnsembleAnalyzer.PoseIdentity("same", 2, false));
        assertThatThrownBy(() -> Phase2EnsembleAnalyzer.completeLink(duplicate,
                List.of(List.of(0.0, 1.0), List.of(1.0, 0.0)), 1.0, Set.of(), POLICY))
                .hasMessageContaining("unique");
        assertThatThrownBy(() -> new Phase2EnsembleAnalyzer.PoseIdentity(" ", 1, false))
                .hasMessageContaining("id required");
    }

    @Test void validClusteringIsInvariantToPoseOrdering() {
        var three = List.of(new Phase2EnsembleAnalyzer.PoseIdentity("a", 1, false),
                new Phase2EnsembleAnalyzer.PoseIdentity("b", 2, false),
                new Phase2EnsembleAnalyzer.PoseIdentity("c", 3, false));
        var matrix = List.of(List.of(0.0, 1.0, 4.0), List.of(1.0, 0.0, 4.0), List.of(4.0, 4.0, 0.0));
        var original = Phase2EnsembleAnalyzer.completeLink(three, matrix, 2.0, Set.of(), POLICY);
        var reorderedPoses = List.of(three.get(2), three.get(0), three.get(1));
        var reordered = List.of(List.of(0.0, 4.0, 4.0), List.of(4.0, 0.0, 1.0), List.of(4.0, 1.0, 0.0));
        var permuted = Phase2EnsembleAnalyzer.completeLink(reorderedPoses, reordered, 2.0, Set.of(), POLICY);
        assertThat(memberIds(original, three)).containsExactlyInAnyOrderElementsOf(memberIds(permuted, reorderedPoses));
    }

    private static List<Set<String>> memberIds(List<Phase2EnsembleAnalyzer.Basin> basins,
                                                List<Phase2EnsembleAnalyzer.PoseIdentity> poses) {
        return basins.stream().map(basin -> basin.memberIndices().stream()
                .map(index -> poses.get(index).id()).collect(java.util.stream.Collectors.toSet())).toList();
    }

    private static List<Phase2EnsembleAnalyzer.Basin> cluster(List<List<Double>> matrix) {
        return Phase2EnsembleAnalyzer.completeLink(POSES, matrix, 1.0, Set.of(), POLICY);
    }
}
