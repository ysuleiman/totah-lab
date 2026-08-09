package totah.lab.athena.ligand.pose;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static totah.lab.athena.ligand.pose.AlphaSphereMetricsTest.pocket;
import static totah.lab.athena.ligand.pose.AlphaSphereMetricsTest.sphere;

class LigandPocketOccupancyAnalyzerTest {

    private final LigandPocketOccupancyAnalyzer analyzer =
            new LigandPocketOccupancyAnalyzer();

    @Test
    void summarizesCountsFractionsAffinitiesAndScores() {
        Pocket pocketA = pocket("A", 0, 0,
                List.of(new ResidueId("A", 10, null)),
                Optional.empty(),
                List.of(sphere(0, 0, 2.0)));
        Pocket pocketB = pocket("B", 20, 0,
                List.of(new ResidueId("A", 20, null)),
                Optional.empty(),
                List.of(sphere(20, 0, 2.0)));

        List<PosePocketAssignment> assignments = List.of(
                assigned(pocketA, 0.8),
                assigned(pocketA, 0.6),
                assigned(pocketB, 0.5),
                notAssigned()
        );
        List<PoseAffinity> affinities = List.of(
                new PoseAffinity("pose-1", -9.5),
                new PoseAffinity("pose-2", -7.0),
                new PoseAffinity("pose-3", -8.0),
                new PoseAffinity("pose-4", -6.0)
        );

        LigandPocketOccupancy occupancy =
                analyzer.summarize(assignments, affinities);

        assertThat(occupancy.notAssignedCount()).isEqualTo(1);
        assertThat(occupancy.entries()).hasSize(2);

        PocketOccupancyEntry entryA = occupancy.entries().get(0);
        assertThat(entryA.pocket().id().value()).isEqualTo("A");
        assertThat(entryA.poseCount()).isEqualTo(2);
        assertThat(entryA.fractionOfPoses())
                .isCloseTo(0.5, offset(1.0e-9));
        assertThat(entryA.bestAffinity())
                .isCloseTo(-9.5, offset(1.0e-9));
        assertThat(entryA.medianAffinity())
                .isCloseTo(-8.25, offset(1.0e-9));
        assertThat(entryA.meanAssignmentScore())
                .isCloseTo(0.7, offset(1.0e-9));
        assertThat(entryA.bestAssignmentScore())
                .isCloseTo(0.8, offset(1.0e-9));
        assertThat(entryA.poseLabels())
                .containsExactly("pose-1", "pose-2");

        PocketOccupancyEntry entryB = occupancy.entries().get(1);
        assertThat(entryB.pocket().id().value()).isEqualTo("B");
        assertThat(entryB.poseCount()).isEqualTo(1);
        assertThat(entryB.fractionOfPoses())
                .isCloseTo(0.25, offset(1.0e-9));
        assertThat(entryB.bestAffinity())
                .isCloseTo(-8.0, offset(1.0e-9));
        assertThat(entryB.medianAffinity())
                .isCloseTo(-8.0, offset(1.0e-9));
        assertThat(entryB.poseLabels()).containsExactly("pose-3");
    }

    @Test
    void ambiguousAssignmentsCountTowardBestPocket() {
        Pocket pocketA = pocket("A", 0, 0,
                List.of(new ResidueId("A", 10, null)),
                Optional.empty(),
                List.of(sphere(0, 0, 2.0)));

        LigandPocketOccupancy occupancy = analyzer.summarize(
                List.of(ambiguous(pocketA, 0.4)),
                List.of(new PoseAffinity("pose-1", -8.0)));

        assertThat(occupancy.notAssignedCount()).isEqualTo(0);
        assertThat(occupancy.entries()).hasSize(1);
        assertThat(occupancy.entries().get(0).poseCount()).isEqualTo(1);
        assertThat(occupancy.entries().get(0).fractionOfPoses())
                .isEqualTo(1.0);
    }

    @Test
    void emptyInputYieldsEmptyReport() {
        LigandPocketOccupancy occupancy =
                analyzer.summarize(List.of(), List.of());

        assertThat(occupancy.entries()).isEmpty();
        assertThat(occupancy.notAssignedCount()).isEqualTo(0);
    }

    @Test
    void rejectsSizeMismatch() {
        Pocket pocketA = pocket("A", 0, 0,
                List.of(new ResidueId("A", 10, null)),
                Optional.empty(),
                List.of(sphere(0, 0, 2.0)));

        assertThatThrownBy(() -> analyzer.summarize(
                List.of(assigned(pocketA, 0.8)),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same size");
    }

    private static PosePocketAssignment assigned(
            Pocket pocket,
            double score
    ) {
        return new PosePocketAssignment(
                pocket,
                score,
                null,
                null,
                null,
                score,
                false,
                AssignmentStatus.ASSIGNED,
                "test"
        );
    }

    private static PosePocketAssignment ambiguous(
            Pocket pocket,
            double score
    ) {
        return new PosePocketAssignment(
                pocket,
                score,
                null,
                null,
                null,
                0.0,
                true,
                AssignmentStatus.AMBIGUOUS,
                "test"
        );
    }

    private static PosePocketAssignment notAssigned() {
        return new PosePocketAssignment(
                null,
                null,
                null,
                null,
                null,
                0.0,
                false,
                AssignmentStatus.NOT_ASSIGNED,
                "test"
        );
    }
}
