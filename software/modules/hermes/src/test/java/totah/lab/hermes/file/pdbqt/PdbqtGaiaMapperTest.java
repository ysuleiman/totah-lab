package totah.lab.hermes.file.pdbqt;

import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Structure;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdbqtGaiaMapperTest {

    @Test
    void mapsLigandModelToGaiaLigand() throws Exception {
        PdbqtModel model = new PdbqtReader().read(new StringReader(
                String.join("\n", List.of(
                        "ROOT",
                        atom(1, "C1", "UNL", "L", 1, 0, 0, 0, 0.05, "C"),
                        atom(2, "CL1", "UNL", "L", 1, 1.7, 0, 0, -0.1, "Cl"),
                        atom(3, "H1", "UNL", "L", 1, 0, 1.0, 0, 0.05, "HD"),
                        "ENDROOT",
                        "TORSDOF 1"
                )))).firstModel();

        Ligand ligand = PdbqtGaiaMapper.toLigand(model, "DCMB");

        List<totah.lab.gaia.structure.Atom> atoms =
                ligand.structure().getChains().getFirst()
                        .residues().getFirst().getAtoms();
        assertEquals("DCMB", ligand.id());
        assertEquals(3, atoms.size());
        assertEquals(2, ligand.structure().getChains().getFirst()
                .residues().getFirst().getHeavyAtomCount());
        assertEquals("Cl", atoms.get(1).getElement().symbol());
        assertEquals(1.7, atoms.get(1).getPosition().x(), 1e-9);
        assertEquals(-0.1, atoms.get(1).getCharge(), 1e-9);
        assertEquals("Cl", atoms.get(1).getAutoDockType());
    }

    @Test
    void reconstructsCompleteDcmbGraphFromFrozenMeekoEvidence()
            throws Exception {
        Path path = Path.of(getClass().getResource(
                "/vina/dcmb-diffdock-vina_out.pdbqt").toURI());
        PdbqtModel model = new PdbqtReader().read(path).firstModel();

        Ligand ligand = PdbqtGaiaMapper.toLigandWithMeekoTopology(
                model, "DCMB_R");
        Structure structure = ligand.structure();

        assertEquals(ConnectivityProvenance.EXPLICIT,
                structure.getConnectivityMetadata().provenance());
        assertEquals(13, structure.getChains().getFirst().residues()
                .getFirst().getAtomCount());
        assertEquals(13, structure.getBonds().size());
        assertEquals(6, structure.getBonds().stream()
                .filter(bond -> bond.order() == BondOrder.AROMATIC).count());
        assertEquals(2, structure.getBonds().stream()
                .filter(bond -> bond.atom1().atomName().startsWith("H")
                        || bond.atom2().atomName().startsWith("H"))
                .count());
        assertEquals(2, structure.getBonds().stream()
                .filter(bond -> (bond.atom1().atomName().startsWith("H")
                        && bond.atom2().atomName().equals("N"))
                        || (bond.atom2().atomName().startsWith("H")
                        && bond.atom1().atomName().equals("N")))
                .count());
        assertTrue(structure.getConnectivityMetadata().diagnostics().contains(
                "SMILES=C[C@@H](N)c1cccc(Cl)c1Cl"));
    }

    @Test
    void preservesEachEmbeddedDcmbStereochemicalIdentity() throws Exception {
        String rInput = new String(getClass().getResourceAsStream(
                "/vina/dcmb-diffdock-vina_out.pdbqt").readAllBytes());
        String sInput = rInput.replace(
                "C[C@@H](N)c1cccc(Cl)c1Cl",
                "C[C@H](N)c1cccc(Cl)c1Cl");

        Structure r = topology(rInput, "DCMB_R");
        Structure s = topology(sInput, "DCMB_S");

        assertTrue(r.getConnectivityMetadata().diagnostics().contains(
                "SMILES=C[C@@H](N)c1cccc(Cl)c1Cl"));
        assertTrue(s.getConnectivityMetadata().diagnostics().contains(
                "SMILES=C[C@H](N)c1cccc(Cl)c1Cl"));
        assertEquals(r.getBonds(), s.getBonds());
    }

    @Test
    void rejectsIncompleteHeavyAtomMapping() throws Exception {
        String input = new String(getClass().getResourceAsStream(
                "/vina/dcmb-diffdock-vina_out.pdbqt").readAllBytes())
                .replace(" 3 11\n", "\n");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> topology(input, "DCMB_R"));
        assertTrue(error.getMessage().contains(
                "Incomplete Meeko heavy-atom mapping"));
    }

    @Test
    void rejectsDuplicateAndOutOfRangeHeavyAtomMappings() throws Exception {
        String input = new String(getClass().getResourceAsStream(
                "/vina/dcmb-diffdock-vina_out.pdbqt").readAllBytes());

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> topology(input.replace(" 3 11\n", " 2 11\n"),
                        "duplicate"))
                .getMessage().contains("Duplicate SMILES atom index"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> topology(input.replace(" 3 11\n", " 99 11\n"),
                        "out-of-range"))
                .getMessage().contains("SMILES atom index out of range"));
    }

    @Test
    void rejectsElementAndHydrogenParentContradictions() throws Exception {
        String input = new String(getClass().getResourceAsStream(
                "/vina/dcmb-diffdock-vina_out.pdbqt").readAllBytes());

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> topology(input.replace(
                                "11 9 9 10 3 11",
                                "11 9 3 10 9 11"),
                        "element"))
                .getMessage().contains("Element mismatch"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> topology(input.replace(
                                "REMARK H PARENT 3 12 3 13",
                                "REMARK H PARENT 99 12 3 13"),
                        "hydrogen-parent"))
                .getMessage().contains(
                        "Hydrogen parent references unmapped SMILES atom"));
    }

    @Test
    void rejectsTorsionTreeContradiction() throws Exception {
        String input = new String(getClass().getResourceAsStream(
                "/vina/dcmb-diffdock-vina_out.pdbqt").readAllBytes())
                .replace("BRANCH   1  11", "BRANCH   2  11")
                .replace("ENDBRANCH   1  11", "ENDBRANCH   2  11");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> topology(input, "DCMB_R"));
        assertTrue(error.getMessage().contains(
                "Torsion-tree bond is absent"));
    }

    @Test
    void legacyAtomOnlyMappingRemainsConnectivityAbsent() throws Exception {
        Path path = Path.of(getClass().getResource(
                "/vina/dcmb-diffdock-vina_out.pdbqt").toURI());
        PdbqtModel model = new PdbqtReader().read(path).firstModel();

        Structure structure = PdbqtGaiaMapper.toLigand(model, "DCMB")
                .structure();

        assertEquals(ConnectivityProvenance.ABSENT,
                structure.getConnectivityMetadata().provenance());
        assertTrue(structure.getBonds().isEmpty());
    }

    @Test
    void mapsReceptorToStructureGroupedByResidue() throws Exception {
        PdbqtFile file = new PdbqtReader().read(new StringReader(
                String.join("\n", List.of(
                        atom(1, "N", "MET", "A", 1, 25, 14, -16, 0.0, "NA"),
                        atom(2, "CA", "MET", "A", 1, 24, 13.5, -16.3, 0.1, "C"),
                        atom(3, "CA", "ALA", "A", 2, 20, 12, -15, 0.0, "C"),
                        atom(4, "CA", "GLY", "B", 3, 10, 10, -10, 0.0, "C")
                ))));

        Structure structure = PdbqtGaiaMapper.toStructure(file);

        assertEquals(2, structure.getChains().size());
        assertEquals(2, structure.getChains().getFirst().residues().size());
        assertEquals("MET", structure.getChains().getFirst()
                .residues().getFirst().getName());
        assertEquals(2, structure.getChains().getFirst()
                .residues().getFirst().getAtomCount());
        assertTrue(structure.findResidue(
                new totah.lab.gaia.structure.ResidueId("A", 2, null))
                .isPresent());
    }

    private static String atom(
            int serial,
            String name,
            String residueName,
            String chain,
            int residueNumber,
            double x,
            double y,
            double z,
            double charge,
            String type
    ) {
        return String.format(
                Locale.ROOT,
                "ATOM  %5d %-4s %-3s %1s%4d    %8.3f%8.3f%8.3f"
                        + "  1.00  0.00    %+6.3f %-2s",
                serial, name, residueName, chain, residueNumber,
                x, y, z, charge, type
        );
    }

    private static Structure topology(String input, String name)
            throws Exception {
        PdbqtModel model = new PdbqtReader().read(new StringReader(input))
                .firstModel();
        return PdbqtGaiaMapper.toLigandWithMeekoTopology(model, name)
                .structure();
    }
}
