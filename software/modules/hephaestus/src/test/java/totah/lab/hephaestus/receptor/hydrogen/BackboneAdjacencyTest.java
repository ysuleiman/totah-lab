package totah.lab.hephaestus.receptor.hydrogen;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackboneAdjacencyTest {

    @Test
    void insertionCodeSuccessorsCountAsConsecutive() {
        Residue ten = glycine(10, null, 0.0);
        Residue tenA = glycine(10, 'A', 4.0);
        Residue eleven = glycine(11, null, 8.0);
        Residue twelve = glycine(12, null, 12.0);

        assertTrue(ResidueHydrogenator.isConsecutive(ten, tenA));
        assertTrue(ResidueHydrogenator.isConsecutive(tenA, eleven));
        assertTrue(ResidueHydrogenator.isConsecutive(ten, eleven));
        assertFalse(ResidueHydrogenator.isConsecutive(ten, twelve));
    }

    @Test
    void insertionCodeResidueReceivesBackboneAmideHydrogen() {
        Chain chain = new Chain("A", List.of(
                glycine(10, null, 0.0),
                glycine(10, 'A', 4.0)));

        List<Residue> output = new ReceptorHydrogenator()
                .hydrogenate(chain, ProtonationConfig.defaults(), Map.of());

        assertFalse(output.get(0).containsAtom("H"));
        assertTrue(output.get(1).containsAtom("H"));
    }

    private Residue glycine(
            int residueNumber,
            Character insertionCode,
            double offset) {

        return new Residue(
                "GLY",
                residueNumber,
                insertionCode,
                List.of(
                        atom("N", Element.N, offset, 0.0, 0.0),
                        atom("CA", Element.C, offset + 1.45, 0.0, 0.0),
                        atom("C", Element.C, offset + 2.05, 1.35, 0.0),
                        atom("O", Element.O, offset + 1.45, 2.40, 0.0)));
    }

    private Atom atom(
            String name,
            Element element,
            double x,
            double y,
            double z) {

        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, y, z))
                .occupancy(1.0)
                .build();
    }
}
