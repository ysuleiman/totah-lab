package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class SameSitePoseComparatorTest {

    private final SameSitePoseComparator comparator =
            new SameSitePoseComparator();

    private static final String[] NAMES = {"C1", "C2", "C3"};

    private static final double[][] BASE = {
            {1, 0, 0},
            {2, 0, 0},
            {1, 1, 0}
    };

    private static final PocketArchitecture ARCHITECTURE =
            PocketArchitecture.of(PoseComparisonTestFixtures.pocket(
                    "1", new double[][]{
                            {0, 0, 0}, {3, 0, 0}, {6, 0, 0},
                            {1, 2, 0}, {4, 2, 0}, {1, -2, 0},
                            {4, -2, 0}, {3, 1, 2}
                    }));

    private static final PocketArchitecture AXIS_ALIGNED_ARCHITECTURE =
            PocketArchitecture.of(PoseComparisonTestFixtures.pocket(
                    "axis-aligned", new double[][]{
                            {6, 0, 0}, {-6, 0, 0},
                            {0, 2, 0}, {0, -2, 0},
                            {0, 0, 1}, {0, 0, -1}
                    }));

    @Test
    void rotatedLigandRecoversTheRotationAngle() {
        Ligand poseA = PoseComparisonTestFixtures.ligand("A", NAMES, BASE);
        // 90 degrees about z, identity frame transform.
        Ligand poseB = PoseComparisonTestFixtures.ligand("B", NAMES,
                new double[][]{
                        {0, 1, 0},
                        {0, 2, 0},
                        {-1, 1, 0}
                });

        SameSitePoseComparison comparison = comparator.compare(
                "A", poseA, "B", poseB,
                RigidTransform.identity(),
                ARCHITECTURE
        );

        assertThat(comparison.atomCorrespondence()).isEqualTo(
                LigandAtomCorrespondence.Method.INDEX_ORDER);
        assertThat(comparison.rotationAngleDegrees())
                .isCloseTo(90.0, offset(1.0e-6));
        assertThat(comparison.heavyAtomRmsd())
                .isCloseTo(Math.sqrt(14.0 / 3.0), offset(1.0e-9));
        assertThat(comparison.centroidSeparationAngstroms())
                .isCloseTo(Math.sqrt(34.0) / 3.0, offset(1.0e-9));
    }

    @Test
    void permutedLigandIsMappedAndGivesZeroRmsd() {
        Ligand poseA = PoseComparisonTestFixtures.ligand("A", NAMES, BASE);
        Ligand poseB = PoseComparisonTestFixtures.ligand("B",
                new String[]{"C3", "C1", "C2"},
                new double[][]{
                        BASE[2],
                        BASE[0],
                        BASE[1]
                });

        SameSitePoseComparison comparison = comparator.compare(
                "A", poseA, "B", poseB,
                RigidTransform.identity(),
                ARCHITECTURE
        );

        assertThat(comparison.atomCorrespondence()).isEqualTo(
                LigandAtomCorrespondence.Method.NAME_ELEMENT);
        assertThat(comparison.heavyAtomRmsd())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(comparison.centroidSeparationAngstroms())
                .isCloseTo(0.0, offset(1.0e-9));
    }

    @Test
    void unverifiedCorrespondenceNullsRmsdButKeepsCentroids() {
        Ligand poseA = PoseComparisonTestFixtures.ligand("A", NAMES, BASE);
        Ligand poseB = PoseComparisonTestFixtures.ligand("B",
                new String[]{"C1", "C2"},
                new double[][]{{1, 0, 0}, {2, 0, 0}});

        SameSitePoseComparison comparison = comparator.compare(
                "A", poseA, "B", poseB,
                RigidTransform.identity(),
                AXIS_ALIGNED_ARCHITECTURE
        );

        assertThat(comparison.atomCorrespondence()).isEqualTo(
                LigandAtomCorrespondence.Method.NONE);
        assertThat(comparison.heavyAtomRmsd()).isNull();
        assertThat(comparison.rotationAngleDegrees()).isNull();
        assertThat(comparison.correspondenceReason())
                .contains("counts differ");
        // Centroid metrics are still reported.
        assertThat(comparison.centroidSeparationAngstroms())
                .isGreaterThan(0.0);
        assertThat(comparison.orientationAnglesPoseB().getFirst())
                .isCloseTo(0.0, offset(1.0e-9));
    }

    @Test
    void translationIsDecomposedAlongThePocketAxes() {
        Ligand poseA = PoseComparisonTestFixtures.ligand("A", NAMES, BASE);
        Ligand poseB = PoseComparisonTestFixtures.ligand("B", NAMES,
                new double[][]{
                        {3, 3, 0},
                        {4, 3, 0},
                        {3, 4, 0}
                });

        SameSitePoseComparison comparison = comparator.compare(
                "A", poseA, "B", poseB,
                RigidTransform.identity(),
                AXIS_ALIGNED_ARCHITECTURE
        );

        // The fixture pocket's first principal axis is x.
        assertThat(comparison.displacementAlongU1())
                .isCloseTo(2.0, offset(1.0e-6));
        assertThat(comparison.lateralDisplacement())
                .isCloseTo(3.0, offset(1.0e-6));
        assertThat(comparison.centroidTranslation().x())
                .isCloseTo(2.0, offset(1.0e-9));
        assertThat(comparison.centroidTranslation().y())
                .isCloseTo(3.0, offset(1.0e-9));
        assertThat(comparison.heavyAtomRmsd())
                .isCloseTo(Math.sqrt(13.0), offset(1.0e-9));
    }

    @Test
    void ligandLongAxisOrientationIsMeasuredPerAxis() {
        // Ligand extended along x; the fixture pocket's u1 is x.
        Ligand pose = PoseComparisonTestFixtures.ligand("A",
                new String[]{"C1", "C2", "C3", "C4"},
                new double[][]{
                        {0, 0, 0},
                        {2, 0, 0},
                        {4, 0, 0},
                        {6, 0, 0}
                });

        SameSitePoseComparison comparison = comparator.compare(
                "A", pose, "B", pose,
                RigidTransform.identity(),
                AXIS_ALIGNED_ARCHITECTURE
        );

        assertThat(comparison.orientationAnglesPoseA().get(0))
                .isCloseTo(0.0, offset(1.0));
        assertThat(comparison.orientationAnglesPoseA().get(1))
                .isCloseTo(90.0, offset(1.0));
    }
}
