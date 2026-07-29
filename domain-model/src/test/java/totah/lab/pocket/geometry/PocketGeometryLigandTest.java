package totah.lab.pocket.geometry;

import org.junit.jupiter.api.Test;
import totah.lab.ligand.Ligand;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PocketGeometryLigandTest {

    @Test
    void findsStructureResiduesWithinLigandCutoff() {
        Residue near = residue(1, 0.0);
        Residue far = residue(2, 8.0);
        Ligand ligand = new Ligand("SAM", List.of(atom(3.0)));

        var neighbors = PocketGeometry.ligandNeighbors(
                new Structure(List.of(near, far)),
                ligand,
                4.0
        );

        assertThat(neighbors).containsExactly(near);
        assertThat(PocketGeometry.calculateDistance(near, ligand))
                .isEqualTo(3.0);
        assertThat(PocketGeometry.contactingAtomPairCount(
                near,
                ligand,
                4.0
        )).isEqualTo(1);
    }

    private Residue residue(int number, double x) {
        return new Residue(
                "GLY",
                number,
                "A",
                null,
                List.of(atom(x))
        );
    }

    private Atom atom(double x) {
        return Atom.builder()
                .pdbSerial(1)
                .name("C")
                .position(new Point3D(x, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.C)
                .build();
    }
}
