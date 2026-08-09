package totah.lab.athena.ligand.pose;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static totah.lab.athena.ligand.pose.LigandGeometryTest.atom;
import static totah.lab.athena.ligand.pose.LigandGeometryTest.ligand;

class AlphaSphereMetricsTest {

    @Test
    void atomInsideSphereHasZeroSurfaceDistance() {
        Ligand ligand = ligand(atom(1, "C1", 0, 0, 0));
        Pocket pocket = pocket(sphere(0, 0, 2.0));

        AlphaSphereOccupancy occupancy =
                AlphaSphereMetrics.calculate(ligand, pocket);

        assertThat(occupancy.basisAvailable()).isTrue();
        assertThat(occupancy.sphereCount()).isEqualTo(1);
        assertThat(occupancy.atomWithin2AOfSphereFraction()).isEqualTo(1.0);
        assertThat(occupancy.atomWithin3AOfSphereFraction()).isEqualTo(1.0);
        assertThat(occupancy.meanNearestSphereDistance()).isEqualTo(0.0);
        assertThat(occupancy.maxNearestSphereDistance()).isEqualTo(0.0);
    }

    @Test
    void atomOutsideSphereHasSurfaceGap() {
        Ligand ligand = ligand(atom(1, "C1", 5, 0, 0));
        Pocket pocket = pocket(sphere(0, 0, 2.0));

        AlphaSphereOccupancy occupancy =
                AlphaSphereMetrics.calculate(ligand, pocket);

        assertThat(occupancy.atomWithin2AOfSphereFraction()).isEqualTo(0.0);
        assertThat(occupancy.atomWithin3AOfSphereFraction()).isEqualTo(1.0);
        assertThat(occupancy.meanNearestSphereDistance())
                .isCloseTo(3.0, offset(1.0e-9));
        assertThat(occupancy.maxNearestSphereDistance())
                .isCloseTo(3.0, offset(1.0e-9));
    }

    @Test
    void aggregatesFractionsMeanAndMaxAcrossAtoms() {
        Ligand ligand = ligand(
                atom(1, "C1", 0, 0, 0),
                atom(2, "C2", 3, 0, 0),
                atom(3, "C3", 5.5, 0, 0)
        );
        Pocket pocket = pocket(sphere(0, 0, 2.0));

        AlphaSphereOccupancy occupancy =
                AlphaSphereMetrics.calculate(ligand, pocket);

        assertThat(occupancy.atomWithin2AOfSphereFraction())
                .isCloseTo(2.0 / 3.0, offset(1.0e-9));
        assertThat(occupancy.atomWithin3AOfSphereFraction())
                .isCloseTo(2.0 / 3.0, offset(1.0e-9));
        assertThat(occupancy.meanNearestSphereDistance())
                .isCloseTo(1.5, offset(1.0e-9));
        assertThat(occupancy.maxNearestSphereDistance())
                .isCloseTo(3.5, offset(1.0e-9));
    }

    @Test
    void takesMinimumOverSpheres() {
        Ligand ligand = ligand(atom(1, "C1", 10, 0, 0));
        Pocket pocket = pocket(
                sphere(0, 0, 2.0),
                sphere(13, 0, 2.0)
        );

        AlphaSphereOccupancy occupancy =
                AlphaSphereMetrics.calculate(ligand, pocket);

        assertThat(occupancy.sphereCount()).isEqualTo(2);
        assertThat(occupancy.meanNearestSphereDistance())
                .isCloseTo(1.0, offset(1.0e-9));
    }

    @Test
    void pocketWithoutSpheresReportsNoBasis() {
        Ligand ligand = ligand(atom(1, "C1", 0, 0, 0));
        Pocket pocket = pocket();

        AlphaSphereOccupancy occupancy =
                AlphaSphereMetrics.calculate(ligand, pocket);

        assertThat(occupancy.basisAvailable()).isFalse();
        assertThat(occupancy.sphereCount()).isEqualTo(0);
        assertThat(occupancy.atomWithin2AOfSphereFraction()).isEqualTo(0.0);
        assertThat(occupancy.meanNearestSphereDistance()).isEqualTo(0.0);
        assertThat(AlphaSphereMetrics.hasSpheres(pocket)).isFalse();
    }

    @Test
    void occupiedFractionRespectsTolerance() {
        Ligand ligand = ligand(
                atom(1, "C1", 0, 0, 0),
                atom(2, "C2", 3.5, 0, 0),
                atom(3, "C3", 10, 0, 0)
        );
        Pocket pocket = pocket(sphere(0, 0, 2.0));

        assertThat(AlphaSphereMetrics.occupiedFraction(
                ligand, pocket, 2.0))
                .isCloseTo(2.0 / 3.0, offset(1.0e-9));
        assertThat(AlphaSphereMetrics.occupiedFraction(
                ligand, pocket, 0.5))
                .isCloseTo(1.0 / 3.0, offset(1.0e-9));
        assertThat(AlphaSphereMetrics.occupiedFraction(
                ligand, pocket(), 2.0))
                .isEqualTo(0.0);
    }

    @Test
    void throwsOnZeroHeavyAtoms() {
        Ligand ligand = ligand(
                atom(1, "H1", 0, 0, 0,
                        totah.lab.gaia.chemistry.Element.H));

        assertThatThrownBy(() -> AlphaSphereMetrics.calculate(
                ligand, pocket(sphere(0, 0, 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static AlphaSphere sphere(double x, double z, double radius) {
        return new AlphaSphere(1L, new Point3D(x, 0, z), radius);
    }

    static Pocket pocket(AlphaSphere... spheres) {
        return pocket("A", 0, 0,
                List.of(new ResidueId("A", 10, null)),
                Optional.empty(),
                List.of(spheres));
    }

    static Pocket pocket(
            String id,
            double centerX,
            double centerZ,
            List<ResidueId> residues,
            Optional<totah.lab.gaia.geometry.BoundingBox> bounds,
            List<AlphaSphere> spheres
    ) {
        return new Pocket(
                new PocketId(id),
                "pocket-" + id,
                PocketSource.MANUAL,
                new Point3D(centerX, 0, centerZ),
                residues,
                List.of(),
                bounds,
                Optional.of(new AlphaSphereSet(spheres)),
                Map.of()
        );
    }

    static Pocket pocketWithoutSpheres(
            String id,
            double centerX,
            List<ResidueId> residues,
            Optional<totah.lab.gaia.geometry.BoundingBox> bounds
    ) {
        return new Pocket(
                new PocketId(id),
                "pocket-" + id,
                PocketSource.MANUAL,
                new Point3D(centerX, 0, 0),
                residues,
                List.of(),
                bounds,
                Optional.empty(),
                Map.of()
        );
    }
}
