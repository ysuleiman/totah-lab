package totah.lab.athena.pocket.geometry;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PocketGeometryStrategyTest {
    private static final double TOLERANCE = 1.0e-6;

    @Test
    void fpocketGeometryIsExplicitlyAlphaSphereBased() {
        Structure structure = new Structure(List.of());
        Pocket pocket = pocket(
                PocketSource.FPOCKET,
                List.of(),
                Optional.of(new AlphaSphereSet(List.of(
                        new AlphaSphere(
                                1, new Point3D(2, 4, 6), 2)))));

        PocketGeometryResult result =
                PocketGeometry.geometry(structure, pocket);

        assertThat(result.basis())
                .isEqualTo(PocketGeometryBasis.ALPHA_SPHERES);
        assertEquals(2.0, result.centroid().x(), TOLERANCE);
        assertEquals(0.0, result.bounds().min().x(), TOLERANCE);
    }

    @Test
    void p2rankGeometryUsesResolvedHeavyAtomsAndReportsMissingIds() {
        Residue residue = new Residue(
                "GLY",
                10,
                List.of(atom(1, new Point3D(1, 2, 3)),
                        atom(2, new Point3D(5, 6, 7))));
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(residue))));
        ResidueId missing = new ResidueId("A", 99, null);
        Pocket pocket = pocket(
                PocketSource.P2RANK,
                List.of(new ResidueId("A", 10, null), missing),
                Optional.empty());

        PocketGeometryResult result =
                PocketGeometry.geometry(structure, pocket);

        assertThat(result.basis()).isEqualTo(
                PocketGeometryBasis.RESOLVED_RESIDUE_HEAVY_ATOMS);
        assertThat(result.unresolvedResidues()).containsExactly(missing);
        assertEquals(3.0, result.centroid().x(), TOLERANCE);
        assertEquals(5.0, result.bounds().max().x(), TOLERANCE);
    }

    @Test
    void centerOnlyPocketFallsBackToReportedCenterGeometry() {
        Structure structure = new Structure(List.of());
        Pocket pocket = pocket(
                PocketSource.MANUAL,
                List.of(),
                Optional.empty());

        PocketGeometryResult result =
                PocketGeometry.geometry(structure, pocket);

        assertThat(result.basis())
                .isEqualTo(PocketGeometryBasis.REPORTED_CENTER);
        assertEquals(100.0, result.centroid().x(), TOLERANCE);
        assertEquals(100.0, result.centroid().y(), TOLERANCE);
        assertEquals(100.0, result.centroid().z(), TOLERANCE);
        assertEquals(100.0, result.bounds().min().x(), TOLERANCE);
        assertEquals(100.0, result.bounds().max().x(), TOLERANCE);
        assertThat(result.unresolvedResidues()).isEmpty();
    }

    @Test
    void unresolvableResidueIdsAlsoFallBackToReportedCenter() {
        Structure structure = new Structure(List.of());
        Pocket pocket = pocket(
                PocketSource.P2RANK,
                List.of(new ResidueId("A", 42, null)),
                Optional.empty());

        PocketGeometryResult result =
                PocketGeometry.geometry(structure, pocket);

        assertThat(result.basis())
                .isEqualTo(PocketGeometryBasis.REPORTED_CENTER);
        assertEquals(100.0, result.centroid().x(), TOLERANCE);
        assertThat(result.unresolvedResidues())
                .containsExactly(new ResidueId("A", 42, null));
    }

    @Test
    void duplicateResidueIdsDoNotSkewResolvedHeavyAtomCentroid() {
        Residue first = new Residue(
                "GLY", 10, List.of(atom(1, new Point3D(0, 0, 0))));
        Residue second = new Residue(
                "ALA", 20, List.of(atom(2, new Point3D(10, 0, 0))));
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(first, second))));
        Pocket pocket = pocket(
                PocketSource.P2RANK,
                List.of(new ResidueId("A", 10, null),
                        new ResidueId("A", 10, null),
                        new ResidueId("A", 20, null)),
                Optional.empty());

        PocketGeometryResult result =
                PocketGeometry.geometry(structure, pocket);

        assertThat(result.basis()).isEqualTo(
                PocketGeometryBasis.RESOLVED_RESIDUE_HEAVY_ATOMS);
        assertEquals(5.0, result.centroid().x(), TOLERANCE);
    }

    private static Atom atom(int serial, Point3D position) {
        return Atom.builder()
                .pdbSerial(serial)
                .name("C" + serial)
                .position(position)
                .element(Element.C)
                .build();
    }

    private static Pocket pocket(
            PocketSource source,
            List<ResidueId> residues,
            Optional<AlphaSphereSet> spheres) {
        return new Pocket(
                new PocketId("1"),
                "Pocket 1",
                source,
                new Point3D(100, 100, 100),
                residues,
                List.of(),
                Optional.empty(),
                spheres,
                Map.of());
    }
}
