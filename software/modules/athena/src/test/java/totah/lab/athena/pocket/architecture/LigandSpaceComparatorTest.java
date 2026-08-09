package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class LigandSpaceComparatorTest {

    private final AlphaSphereArchitectureAnalyzer sphereAnalyzer =
            new AlphaSphereArchitectureAnalyzer();

    @Test
    void posesInDifferentComponentsYieldDifferentCompartment() {
        // Two well-separated clusters; each pose occupies its own.
        Structure receptor = ArchitectureTestFixtures.receptor();
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                new double[][]{
                        {0, 0, 0},
                        {2.5, 0, 0},
                        {0, 2.5, 0},
                        {20, 0, 0},
                        {22.5, 0, 0},
                        {20, 2.5, 0}
                });

        Ligand poseA = ArchitectureTestFixtures.pose("pose-A",
                new double[][]{
                        {0.5, 0.5, 0},
                        {1, 1, 0},
                        {1.5, 0.5, 0}
                });
        Ligand poseB = ArchitectureTestFixtures.pose("pose-B",
                new double[][]{
                        {20.5, 0.5, 0},
                        {21, 1, 0},
                        {21.5, 0.5, 0}
                });

        LigandSpaceComparison comparison = compare(
                receptor, pocket, poseA, poseB);

        assertThat(comparison.dominantDifference()).isEqualTo(
                DominantArchitectureDifference.DIFFERENT_COMPARTMENT);
        assertThat(comparison.occupancyJaccard()).isEqualTo(0.0);
        assertThat(comparison.occupiedComponentsPoseA())
                .isNotEqualTo(comparison.occupiedComponentsPoseB());
        assertThat(comparison.occupiedSpheresA()).isNotEmpty();
        assertThat(comparison.occupiedSpheresB()).isNotEmpty();

        // Per-atom analysis is populated.
        assertThat(comparison.poseA().atoms()).hasSize(3);
        assertThat(comparison.poseA().atoms().get(0)
                .nearestResidue()).isNotNull();
        assertThat(comparison.poseA().atoms().get(0)
                .shellFreeFraction()).isBetween(0.0, 1.0);
        assertThat(comparison.poseA().atoms().get(0)
                .nearestSphereSurfaceDistance())
                .isCloseTo(0.0, offset(1.0e-9));
    }

    @Test
    void posesAtDifferentDepthsYieldDifferentDepth() {
        // Single connected shallow box along x; both poses occupy it
        // but sit at very different depths along the principal axis.
        Structure receptor = ArchitectureTestFixtures.receptor();
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                new double[][]{
                        {0, 0, 0},
                        {1.5, 0, 0},
                        {3, 0, 0},
                        {4.5, 0, 0},
                        {6, 0, 0},
                        {0, 0, 2},
                        {1.5, 0, 2},
                        {3, 0, 2},
                        {4.5, 0, 2},
                        {6, 0, 2},
                        {0, 2, 1},
                        {6, 2, 1}
                });

        Ligand poseA = ArchitectureTestFixtures.pose("pose-A",
                new double[][]{
                        {0.5, 0.5, 0},
                        {0.5, -0.5, 0},
                        {1, 0, 0.5}
                });
        Ligand poseB = ArchitectureTestFixtures.pose("pose-B",
                new double[][]{
                        {5.5, 0.5, 0},
                        {5.5, -0.5, 0},
                        {6, 0, 0.5}
                });

        LigandSpaceComparison comparison = compare(
                receptor, pocket, poseA, poseB);

        assertThat(comparison.dominantDifference())
                .isEqualTo(DominantArchitectureDifference.DIFFERENT_DEPTH);
        assertThat(comparison.depthPoseA())
                .isGreaterThan(comparison.depthPoseB());
        assertThat(Math.abs(comparison.depthPoseA()
                        - comparison.depthPoseB()))
                .isGreaterThan(LigandSpaceOptions.defaults()
                        .depthDifferenceAngstroms());
    }

    @Test
    void occupancyDefaultsToAtomInsideSphere() {
        // Large spheres (radius 4): a pose atom 1.5 A outside the
        // nearest sphere surface saturates the old surface-distance
        // criterion but is outside the sphere itself.
        Structure receptor = ArchitectureTestFixtures.receptor();
        double[][] spherePositions = {
                {0, 0, 0},
                {9, 0, 0},
                {18, 0, 0},
                {4.5, 4, 0},
                {13.5, 4, 0},
                {9, 2, 7}
        };
        Pocket pocket = bigSpherePocket(spherePositions, 4.0);
        Ligand poseA = ArchitectureTestFixtures.pose("pose-A",
                new double[][]{
                        {0, 5.5, 0},
                        {0.4, 5.8, 0},
                        {-0.4, 5.8, 0}
                });
        Ligand poseB = ArchitectureTestFixtures.pose("pose-B",
                new double[][]{
                        {0, 5.5, 0},
                        {0.4, 5.8, 0},
                        {-0.4, 5.8, 0}
                });

        LigandSpaceComparison defaults = compare(
                receptor, pocket, poseA, poseB);
        assertThat(defaults.occupiedSpheresA()).isEmpty();

        // The legacy surface-distance criterion remains available.
        LigandSpaceOptions legacy = new LigandSpaceOptions(
                1.4, 1.0, 2.0, 1.5, 1.5, 1.5, 1.0);
        LigandSpaceComparison legacyResult = compare(
                receptor, pocket, poseA, poseB,
                new LigandSpaceComparator(legacy));
        assertThat(legacyResult.occupiedSpheresA()).isNotEmpty();
    }

    @Test
    void lateralDisplacementYieldsLateralShift() {
        // Elongated pocket (u1 = x); the poses are displaced mostly
        // along y, i.e. laterally.
        Structure receptor = ArchitectureTestFixtures.receptor();
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                new double[][]{
                        {0, 0, 0},
                        {1.5, 0, 0},
                        {3, 0, 0},
                        {4.5, 0, 0},
                        {6, 0, 0},
                        {0, 0, 2},
                        {1.5, 0, 2},
                        {3, 0, 2},
                        {4.5, 0, 2},
                        {6, 0, 2},
                        {0, 2, 1},
                        {6, 2, 1}
                });

        Ligand poseA = ArchitectureTestFixtures.pose("pose-A",
                new double[][]{
                        {2.5, 0.5, 0},
                        {2.5, -0.5, 0},
                        {3, 0, 0.5}
                });
        Ligand poseB = ArchitectureTestFixtures.pose("pose-B",
                new double[][]{
                        {3.5, 3.5, 0},
                        {3.5, 2.5, 0},
                        {4, 3, 0.5}
                });

        LigandSpaceComparison comparison = compare(
                receptor, pocket, poseA, poseB);

        assertThat(comparison.dominantDifference())
                .isEqualTo(DominantArchitectureDifference.LATERAL_SHIFT);
        assertThat(comparison.alignedCentroidDisplacement())
                .isCloseTo(Math.sqrt(10.0), offset(1.0e-6));
        assertThat(comparison.lateralDisplacement())
                .isCloseTo(3.0, offset(1.0e-6));
        assertThat(Math.abs(comparison.displacementAlongU1()))
                .isCloseTo(1.0, offset(1.0e-6));
        assertThat(comparison.reason()).contains("lateral");
    }

    private static Pocket bigSpherePocket(
            double[][] spherePositions,
            double radius
    ) {
        List<AlphaSphere> spheres = new ArrayList<>();

        for (int index = 0; index < spherePositions.length; index++) {
            spheres.add(new AlphaSphere(
                    index + 1L,
                    ArchitectureTestFixtures.point(
                            spherePositions[index]),
                    radius
            ));
        }

        List<ResidueId> residues = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            residues.add(new ResidueId("A", index + 1, null));
        }

        return new Pocket(
                new PocketId("big"),
                "pocket-big",
                PocketSource.FPOCKET,
                ArchitectureTestFixtures.point(spherePositions[0]),
                residues,
                List.of(),
                Optional.empty(),
                Optional.of(new AlphaSphereSet(spheres)),
                Map.of()
        );
    }

    private LigandSpaceComparison compare(
            Structure receptor,
            Pocket pocket,
            Ligand poseA,
            Ligand poseB
    ) {
        return compare(receptor, pocket, poseA, poseB,
                new LigandSpaceComparator());
    }

    private LigandSpaceComparison compare(
            Structure receptor,
            Pocket pocket,
            Ligand poseA,
            Ligand poseB,
            LigandSpaceComparator comparator
    ) {
        AlphaSphereArchitectureComparison spheres =
                sphereAnalyzer.compare(receptor, pocket, receptor,
                        pocket);

        return comparator.compare(
                receptor,
                pocket,
                poseA,
                receptor,
                pocket,
                poseB,
                PocketArchitecture.of(pocket),
                PocketArchitecture.of(pocket),
                spheres.alignment().alignment().transform(),
                spheres.componentsA()
        );
    }
}
