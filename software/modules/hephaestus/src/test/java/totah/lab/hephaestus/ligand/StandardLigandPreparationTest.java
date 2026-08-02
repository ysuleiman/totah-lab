package totah.lab.hephaestus.ligand;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.ligand.flexibility.LigandFlexibilityModel;
import totah.lab.hephaestus.ligand.operation.LigandPdbqtExportOperation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardLigandPreparationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void preparesAndExportsGaiaLigandWithoutOldPipelineTypes() throws Exception {
        ChemComp component = component();
        Ligand ligand = ligand();

        LigandPreparationResult result = DefaultLigandPreparer
                .standard(identifier -> component)
                .prepare(new LigandPreparationRequest(ligand));

        assertTrue(result.successful());
        assertEquals(2, result.preparedLigand().topologyOptional().orElseThrow()
                instanceof totah.lab.hephaestus.ligand.topology.LigandTopology topology
                ? topology.atomCount() : -1);
        assertEquals(2, result.preparedLigand().chargesOptional().orElseThrow().atomCount());
        assertEquals(2, result.preparedLigand().atomTypesOptional().orElseThrow().atomCount());
        assertNotNull(result.preparedLigand().attributes().get(
                LigandFlexibilityModel.ATTRIBUTE_KEY));
        assertEquals("C", result.preparedLigand().ligand().structure().getChains()
                .getFirst().residues().getFirst().getAtoms().getFirst().getAutoDockType());
        assertEquals(null, ligand.structure().getChains().getFirst().residues()
                .getFirst().getAtoms().getFirst().getAutoDockType());

        Path output = temporaryDirectory.resolve("ligand.pdbqt");
        new LigandPdbqtExportOperation().export(result.preparedLigand(), output);
        String pdbqt = Files.readString(output);
        assertTrue(pdbqt.startsWith("ROOT" + System.lineSeparator()));
        assertTrue(pdbqt.endsWith("TORSDOF 0" + System.lineSeparator()));
        assertEquals(2, pdbqt.lines().filter(line -> line.startsWith("ATOM")).count());
    }

    private Ligand ligand() {
        Residue residue = new Residue("LIG", 1, List.of(atom("C1", 0), atom("C2", 1.5)));
        return new Ligand("lig", "Ligand", "LIG", null, null, null,
                new Structure(List.of(new Chain("L", List.of(residue)))));
    }

    private Atom atom(String name, double x) {
        return Atom.builder().pdbSerial((int) x + 1).name(name).element(Element.C)
                .position(new Point3D(x, 0, 0)).charge(0).occupancy(1).bFactor(0).build();
    }

    private ChemComp component() {
        ChemComp result = new ChemComp();
        result.setId("LIG");
        result.setAtoms(List.of(ccdAtom("C1"), ccdAtom("C2")));
        ChemCompBond bond = new ChemCompBond();
        bond.setAtomId1("C1"); bond.setAtomId2("C2"); bond.setValueOrder("SING");
        bond.setPdbxAromaticFlag("N");
        result.setBonds(List.of(bond));
        return result;
    }

    private ChemCompAtom ccdAtom(String name) {
        ChemCompAtom atom = new ChemCompAtom();
        atom.setAtomId(name); atom.setTypeSymbol("C"); atom.setCharge(0);
        atom.setPdbxAromaticFlag("N"); atom.setPdbxLeavingAtomFlag("N");
        return atom;
    }
}
