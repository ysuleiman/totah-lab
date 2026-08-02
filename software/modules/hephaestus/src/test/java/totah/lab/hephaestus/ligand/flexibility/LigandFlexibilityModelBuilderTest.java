package totah.lab.hephaestus.ligand.flexibility;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.ligand.topology.CcdAtomCoordinates;
import totah.lab.hephaestus.ligand.topology.LigandAtomProperties;
import totah.lab.hephaestus.ligand.topology.LigandTopology;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LigandFlexibilityModelBuilderTest {
    @Test
    void createsOneBranchForInternalAcyclicSingleBond() {
        List<Atom> atoms = java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> Atom.builder().name("C" + index).element(Element.C)
                        .position(new Point3D(index, 0, 0)).charge(0).occupancy(1).bFactor(0).build())
                .toList();
        List<ChemicalBond> bonds = List.of(
                new ChemicalBond(0, 1, BondOrder.SINGLE, false),
                new ChemicalBond(1, 2, BondOrder.SINGLE, false),
                new ChemicalBond(2, 3, BondOrder.SINGLE, false));
        List<LigandAtomProperties> properties = java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> new LigandAtomProperties("C" + index, 0, false, false)).toList();
        List<CcdAtomCoordinates> coordinates = java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> new CcdAtomCoordinates(index, null, null)).toList();

        LigandFlexibilityModel model = new LigandFlexibilityModelBuilder().build(
                atoms, new LigandTopology("LIG", 4, bonds, properties, List.of(), coordinates));

        assertEquals(2, model.fragments().size());
        assertEquals(1, model.torsionalDegreesOfFreedom());
        assertEquals(List.of(0, 1), model.fragments().getFirst().atomIndices());
        assertEquals(List.of(2, 3), model.fragments().get(1).atomIndices());
    }
}
