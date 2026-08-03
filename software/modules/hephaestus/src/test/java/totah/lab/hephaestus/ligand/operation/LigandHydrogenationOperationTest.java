package totah.lab.hephaestus.ligand.operation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.topology.CcdAtomCoordinates;
import totah.lab.hephaestus.ligand.topology.LigandAtomProperties;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.model.PreparedLigand;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LigandHydrogenationOperationTest {

    private final LigandHydrogenationOperation operation =
            new LigandHydrogenationOperation();

    @Test
    void furanOxygenPassesValenceCheck() {
        // Furan ring: 4 carbons + oxygen, all ring bonds aromatic,
        // one hydrogen on each carbon.
        List<Atom> atoms = new ArrayList<>(List.of(
                atom("C1", Element.C), atom("C2", Element.C),
                atom("C3", Element.C), atom("C4", Element.C),
                atom("O5", Element.O),
                atom("H1", Element.H), atom("H2", Element.H),
                atom("H3", Element.H), atom("H4", Element.H)));
        List<ChemicalBond> bonds = List.of(
                aromatic(0, 1), aromatic(1, 2), aromatic(2, 3),
                aromatic(3, 4), aromatic(4, 0),
                single(0, 5), single(1, 6), single(2, 7), single(3, 8));

        assertDoesNotThrow(() -> hydrogenate("FUR", atoms, bonds));
    }

    @Test
    void pyrroleNitrogenHydrogenPassesValenceCheck() {
        // Pyrrole ring with the pyrrole N-H hydrogen present.
        List<Atom> atoms = new ArrayList<>(List.of(
                atom("N1", Element.N), atom("C2", Element.C),
                atom("C3", Element.C), atom("C4", Element.C),
                atom("C5", Element.C), atom("HN", Element.H)));
        List<ChemicalBond> bonds = List.of(
                aromatic(0, 1), aromatic(1, 2), aromatic(2, 3),
                aromatic(3, 4), aromatic(4, 0),
                single(0, 5));

        assertDoesNotThrow(() -> hydrogenate("PYR", atoms, bonds));
    }

    @Test
    void imidazoleNitrogensPassValenceCheck() {
        // Imidazole ring: pyrrole-like N1-H and pyridine-like N3.
        List<Atom> atoms = new ArrayList<>(List.of(
                atom("N1", Element.N), atom("C2", Element.C),
                atom("N3", Element.N), atom("C4", Element.C),
                atom("C5", Element.C), atom("HN1", Element.H)));
        List<ChemicalBond> bonds = List.of(
                aromatic(0, 1), aromatic(1, 2), aromatic(2, 3),
                aromatic(3, 4), aromatic(4, 0),
                single(0, 5));

        assertDoesNotThrow(() -> hydrogenate("IMD", atoms, bonds));
    }

    @Test
    void genuineOverValenceIsStillRejected() {
        // Carbon with five single bonds exceeds any valid valence.
        List<Atom> atoms = new ArrayList<>(List.of(
                atom("C1", Element.C),
                atom("H1", Element.H), atom("H2", Element.H),
                atom("H3", Element.H), atom("H4", Element.H),
                atom("H5", Element.H)));
        List<ChemicalBond> bonds = List.of(
                single(0, 1), single(0, 2), single(0, 3),
                single(0, 4), single(0, 5));

        assertThrows(IllegalStateException.class,
                () -> hydrogenate("BAD", atoms, bonds));
    }

    private void hydrogenate(
            String componentId,
            List<Atom> atoms,
            List<ChemicalBond> bonds) {

        Residue residue = new Residue(componentId, 1, atoms);
        Ligand ligand = new Ligand(
                "lig", componentId, componentId, null, null, null,
                new Structure(List.of(new Chain("L", List.of(residue)))));

        List<LigandAtomProperties> properties = new ArrayList<>();
        List<CcdAtomCoordinates> coordinates = new ArrayList<>();
        for (int index = 0; index < atoms.size(); index++) {
            properties.add(new LigandAtomProperties(
                    atoms.get(index).getName(), 0, false, false));
            coordinates.add(new CcdAtomCoordinates(
                    index,
                    atoms.get(index).getPosition(),
                    atoms.get(index).getPosition()));
        }

        LigandTopology topology = new LigandTopology(
                componentId, atoms.size(), bonds, properties,
                List.of(), coordinates);

        operation.apply(
                PreparedLigand.of(ligand).withTopology(topology),
                LigandPreparationOptions.defaults());
    }

    private ChemicalBond aromatic(int a, int b) {
        return new ChemicalBond(a, b, BondOrder.AROMATIC, true);
    }

    private ChemicalBond single(int a, int b) {
        return new ChemicalBond(a, b, BondOrder.SINGLE, false);
    }

    private int serial = 0;

    private Atom atom(String name, Element element) {
        return Atom.builder()
                .pdbSerial(++serial)
                .name(name)
                .element(element)
                .position(new Point3D(serial, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .build();
    }
}
