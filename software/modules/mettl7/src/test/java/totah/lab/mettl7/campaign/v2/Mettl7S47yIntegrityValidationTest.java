package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7S47yIntegrityValidationTest {
    @Test
    void chiRotationPreservesBackboneAndAxisWhileMovingRing() {
        Residue tyr = new Residue("TYR", 47, List.of(
                atom(1, "N", 0, 1, 0, Element.N), atom(2, "CA", 0, 0, 0, Element.C),
                atom(3, "C", -1, 0, 0, Element.C), atom(4, "O", -2, 0, 0, Element.O),
                atom(5, "CB", 1, 0, 0, Element.C), atom(6, "CG", 2, 1, 0, Element.C),
                atom(7, "CD1", 2, 2, 1, Element.C), atom(8, "CD2", 2, 2, -1, Element.C),
                atom(9, "CE1", 2, 3, 1, Element.C), atom(10, "CE2", 2, 3, -1, Element.C),
                atom(11, "CZ", 2, 4, 0, Element.C), atom(12, "OH", 2, 5, 0, Element.O)));
        Structure source = new Structure(List.of(new Chain("A", List.of(tyr))));
        Structure rotated = Mettl7S47yIntegrityValidation.rotateTyr(source,
                new Mettl7S47yIntegrityValidation.LocatedResidue("A", tyr), Math.PI / 2, Math.PI / 2);
        Residue result = rotated.findResidue("A", 47).orElseThrow();
        assertThat(result.findAtom("CA").orElseThrow().getPosition()).isEqualTo(new Point3D(0, 0, 0));
        assertThat(result.findAtom("CB").orElseThrow().getPosition()).isEqualTo(new Point3D(1, 0, 0));
        assertThat(result.findAtom("CG").orElseThrow().getPosition()).isNotEqualTo(new Point3D(2, 1, 0));
        assertThat(result.findAtom("CZ").orElseThrow().getPosition()).isNotEqualTo(new Point3D(2, 4, 0));
    }

    private static Atom atom(int serial, String name, double x, double y, double z, Element element) {
        return Atom.builder().pdbSerial(serial).name(name).autoDockType(element.name())
                .position(new Point3D(x, y, z)).element(element).build();
    }
}
