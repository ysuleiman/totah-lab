package totah.lab.athena.ligand.pose;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class LigandGeometryTest {

    @Test
    void computesHeavyAtomOnlyShape() {
        Ligand ligand = ligand(
                atom(1, "C1", 0, 0, 0),
                atom(2, "C2", 2, 0, 0),
                atom(3, "H1", 100, 0, 0, Element.H)
        );

        LigandShape shape = LigandGeometry.shape(ligand);

        assertThat(shape.heavyAtomCount()).isEqualTo(2);
        assertThat(shape.centroid()).isEqualTo(new Point3D(1, 0, 0));
        assertThat(shape.bounds().min()).isEqualTo(new Point3D(0, 0, 0));
        assertThat(shape.bounds().max()).isEqualTo(new Point3D(2, 0, 0));
        assertThat(shape.radiusFromCentroid())
                .isCloseTo(1.0, offset(1.0e-9));
    }

    @Test
    void radiusReachesFurthestHeavyAtom() {
        Ligand ligand = ligand(
                atom(1, "C1", 0, 0, 0),
                atom(2, "C2", 0, 3, 0),
                atom(3, "C3", 0, 0, -6)
        );

        LigandShape shape = LigandGeometry.shape(ligand);

        assertThat(shape.centroid())
                .isEqualTo(new Point3D(0, 1, -2));
        assertThat(shape.radiusFromCentroid())
                .isCloseTo(Math.sqrt(17.0), offset(1.0e-9));
    }

    @Test
    void throwsOnZeroHeavyAtoms() {
        Ligand ligand = ligand(
                atom(1, "H1", 0, 0, 0, Element.H),
                atom(2, "H2", 1, 0, 0, Element.H)
        );

        assertThatThrownBy(() -> LigandGeometry.shape(ligand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no heavy atoms");
    }

    static Ligand ligand(Atom... atoms) {
        Residue residue = new Residue("LIG", 1, List.of(atoms));
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))));
        return new Ligand("L", "L", null, null, null, null, structure);
    }

    static Atom atom(int serial, String name,
            double x, double y, double z) {
        return atom(serial, name, x, y, z, Element.C);
    }

    static Atom atom(int serial, String name,
            double x, double y, double z, Element element) {
        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(element)
                .build();
    }
}
