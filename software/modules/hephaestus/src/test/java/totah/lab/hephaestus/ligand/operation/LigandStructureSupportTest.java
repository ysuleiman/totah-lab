package totah.lab.hephaestus.ligand.operation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that {@link LigandStructureSupport#replaceAtoms} carries structure
 * bonds and connectivity provenance through atom replacement instead of
 * silently resetting them to an empty ABSENT graph.
 */
class LigandStructureSupportTest {

    @Test
    void bondsSurviveAtomReplacementWhenEndpointsRemain() {
        Ligand ligand = bondedLigand();

        List<Atom> replaced = ligand.structure()
                .getChains().getFirst()
                .residues().getFirst()
                .getAtoms().stream()
                .map(atom -> atom.toBuilder().charge(-0.5).build())
                .toList();

        Ligand updated = LigandStructureSupport.replaceAtoms(ligand, replaced);

        assertEquals(1, updated.structure().bonds().size(),
                "bond must survive atom replacement");
        assertTrue(updated.structure().hasBond(
                new AtomReference("L", 1, ' ', "C1"),
                new AtomReference("L", 1, ' ', "O1")));
        assertEquals(ConnectivityProvenance.EXPLICIT,
                updated.structure().getConnectivityMetadata().provenance());
    }

    @Test
    void droppedEndpointIsReportedWithHonestProvenance() {
        Ligand ligand = bondedLigand();

        List<Atom> withoutOxygen = List.of(
                atom("C1", Element.C, 0.0));

        Ligand updated = LigandStructureSupport.replaceAtoms(
                ligand, withoutOxygen);

        assertTrue(updated.structure().bonds().isEmpty(),
                "bond with a missing endpoint cannot be carried");
        ConnectivityMetadata metadata =
                updated.structure().getConnectivityMetadata();
        assertEquals(ConnectivityProvenance.PARTIAL, metadata.provenance(),
                "dropping a bond must degrade provenance to PARTIAL");
        assertFalse(metadata.diagnostics().isEmpty(),
                "dropping a bond must be reported in the diagnostics");
    }

    private Ligand bondedLigand() {
        Residue residue = new Residue("LIG", 1, List.of(
                atom("C1", Element.C, 0.0),
                atom("O1", Element.O, 1.2)));
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))),
                List.of(new Bond(
                        new AtomReference("L", 1, ' ', "C1"),
                        new AtomReference("L", 1, ' ', "O1"),
                        BondOrder.SINGLE)),
                new ConnectivityMetadata(
                        ConnectivityProvenance.EXPLICIT, List.of()));
        return new Ligand("lig", "LIG", "LIG", null, null, null, structure);
    }

    private Atom atom(String name, Element element, double x) {
        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .build();
    }
}
