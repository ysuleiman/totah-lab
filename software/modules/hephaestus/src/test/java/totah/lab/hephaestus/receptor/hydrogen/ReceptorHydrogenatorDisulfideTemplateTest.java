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

/**
 * The chain-level API only detects disulfides within the given chain; the
 * default pipeline covers cross-chain disulfides by assigning CYX residue
 * states structure-wide and passing the templates through. This test pins
 * that CYX-template fallback: a cysteine carrying a CYX template must not
 * be protonated on SG even when its disulfide partner is in another chain.
 */
class ReceptorHydrogenatorDisulfideTemplateTest {

    private final ReceptorHydrogenator hydrogenator =
            new ReceptorHydrogenator();

    @Test
    void cyxTemplateKeepsSulfurUnprotonated() {
        Chain chain = new Chain("A", List.of(cysteine(5)));

        List<Residue> output = hydrogenator.hydrogenate(
                chain,
                ProtonationConfig.defaults(),
                Map.of("A:5", "CYX"));

        assertFalse(output.getFirst().containsAtom("HG"));
    }

    @Test
    void plainCysteineIsProtonatedOnSulfur() {
        Chain chain = new Chain("A", List.of(cysteine(5)));

        List<Residue> output = hydrogenator.hydrogenate(
                chain,
                ProtonationConfig.defaults(),
                Map.of("A:5", "CYS"));

        assertTrue(output.getFirst().containsAtom("HG"));
    }

    private Residue cysteine(int residueNumber) {
        return new Residue(
                "CYS",
                residueNumber,
                List.of(
                        atom("N", Element.N, 0.0, 0.0, 0.0),
                        atom("CA", Element.C, 1.45, 0.0, 0.0),
                        atom("C", Element.C, 2.05, 1.35, 0.0),
                        atom("O", Element.O, 1.45, 2.40, 0.0),
                        atom("CB", Element.C, 2.10, -1.20, 0.80),
                        atom("SG", Element.S, 3.80, -1.40, 0.40)));
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
