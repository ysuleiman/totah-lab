package totah.lab.hephaestus.receptor.hydrogen;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structure-aware
 * {@link ReceptorHydrogenator#hydrogenate(Structure, String, ProtonationConfig)}
 * overload detects disulfide bonds across the whole structure, so a cysteine
 * whose disulfide partner is in another chain is not protonated on SG. The
 * chain-level overload cannot see the partner chain and keeps its documented
 * limitation.
 */
class ReceptorHydrogenatorStructureDisulfideTest {

    private final ReceptorHydrogenator hydrogenator =
            new ReceptorHydrogenator();

    @Test
    void crossChainDisulfideKeepsSulfurUnprotonated() {
        Structure structure = disulfideLinkedStructure();

        List<Residue> output = hydrogenator.hydrogenate(
                structure, "A", ProtonationConfig.defaults());

        assertFalse(output.getFirst().containsAtom("HG"),
                "cross-chain disulfide cysteine must not be protonated on SG");
    }

    @Test
    void chainLevelOverloadStillProtonatesCrossChainDisulfide() {
        Structure structure = disulfideLinkedStructure();

        List<Residue> output = hydrogenator.hydrogenate(
                structure.getChains().getFirst(),
                ProtonationConfig.defaults());

        assertTrue(output.getFirst().containsAtom("HG"),
                "chain-level overload cannot see the partner chain");
    }

    @Test
    void unknownChainIdIsRejected() {
        Structure structure = disulfideLinkedStructure();

        assertThrows(IllegalArgumentException.class, () ->
                hydrogenator.hydrogenate(
                        structure, "Z", ProtonationConfig.defaults()));
    }

    private Structure disulfideLinkedStructure() {
        // The two cysteines are identical but translated so their SG atoms
        // are 2.05 Å apart (within the default 2.2 Å disulfide cutoff).
        return new Structure(List.of(
                new Chain("A", List.of(cysteine(5, 0.0))),
                new Chain("B", List.of(cysteine(5, 2.05)))));
    }

    private Residue cysteine(int residueNumber, double xOffset) {
        return new Residue(
                "CYS",
                residueNumber,
                List.of(
                        atom("N", Element.N, xOffset + 0.0, 0.0, 0.0),
                        atom("CA", Element.C, xOffset + 1.45, 0.0, 0.0),
                        atom("C", Element.C, xOffset + 2.05, 1.35, 0.0),
                        atom("O", Element.O, xOffset + 1.45, 2.40, 0.0),
                        atom("CB", Element.C, xOffset + 2.10, -1.20, 0.80),
                        atom("SG", Element.S, xOffset + 3.80, -1.40, 0.40)));
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
