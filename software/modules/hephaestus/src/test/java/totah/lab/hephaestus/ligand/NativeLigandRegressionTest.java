package totah.lab.hephaestus.ligand;

import org.biojava.nbio.structure.chem.ReducedChemCompProvider;
import org.biojava.nbio.structure.chem.DownloadChemCompProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.ligand.flexibility.LigandFlexibilityModel;
import totah.lab.hephaestus.ligand.operation.LigandPdbqtExportOperation;
import totah.lab.hermes.file.pdb.reader.PdbReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLigandRegressionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesDepositedGlycerolDeterministicallyWithNativePipeline() throws Exception {
        Residue deposited = glycerol();
        List<String> originalNames = names(deposited.getAtoms());
        var originalPositions = deposited.getAtoms().stream().map(Atom::getPosition).toList();
        Ligand ligand = ligand(deposited);
        DefaultLigandPreparer preparer =
                DefaultLigandPreparer.standard(new ReducedChemCompProvider());

        LigandPreparationResult first = preparer.prepare(new LigandPreparationRequest(ligand));
        LigandPreparationResult second = preparer.prepare(new LigandPreparationRequest(ligand));

        assertTrue(first.successful());
        List<Atom> atoms = first.preparedLigand().ligand().structure().getChains()
                .getFirst().residues().getFirst().getAtoms();
        assertEquals(14, atoms.size());
        assertEquals(originalNames, names(atoms.subList(0, 6)));
        assertEquals(originalPositions,
                atoms.subList(0, 6).stream().map(Atom::getPosition).toList());
        assertTrue(atoms.stream().allMatch(atom -> Double.isFinite(atom.getCharge())));
        assertTrue(atoms.stream().allMatch(atom -> atom.getAutoDockType() != null));

        String firstPdbqt = export(first, "first.pdbqt");
        String secondPdbqt = export(second, "second.pdbqt");
        assertEquals(firstPdbqt, secondPdbqt);
        assertPdbqtIntegrity(first, firstPdbqt);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_ONLINE_CCD_TESTS", matches = "true")
    void preparesOneA4wQweThroughNativePipeline() throws Exception {
        Path pdb = copiedResource("/pipeline/1A4W.pdb");
        Residue qwe = new PdbReader().read(pdb)
                .findResidue("H", 373).orElseThrow();
        assertEquals("QWE", qwe.getName());
        DownloadChemCompProvider provider =
                new DownloadChemCompProvider(temporaryDirectory.resolve("ccd").toString());

        LigandPreparationResult result = DefaultLigandPreparer.standard(provider)
                .prepare(new LigandPreparationRequest(new Ligand(
                        "1A4W-QWE", "RWJ-50215", "QWE", null, null, null,
                        new Structure(List.of(new Chain("H", List.of(qwe)))))));

        assertTrue(result.successful());
        String pdbqt = export(result, "qwe.pdbqt");
        assertPdbqtIntegrity(result, pdbqt);
        assertTrue(result.preparedLigand().ligand().structure().getAtomCount()
                > qwe.getAtomCount());
    }

    private Residue glycerol() throws Exception {
        Path fixture = Path.of(getClass().getResource(
                "/ligand/4E1J-glycerol-panel.pdb").toURI());
        return new PdbReader().read(fixture)
                .findResidue("A", 601).orElseThrow();
    }

    private Path copiedResource(String name) throws Exception {
        Path copy = temporaryDirectory.resolve(Path.of(name).getFileName().toString());
        try (var input = getClass().getResourceAsStream(name)) {
            if (input == null) {
                throw new AssertionError("missing test resource " + name);
            }
            Files.copy(input, copy, StandardCopyOption.REPLACE_EXISTING);
        }
        return copy;
    }

    private Ligand ligand(Residue residue) {
        return new Ligand("GOL-A-601", "Glycerol", "GOL", null, null, null,
                new Structure(List.of(new Chain("A", List.of(residue)))));
    }

    private String export(LigandPreparationResult result, String name) throws Exception {
        Path output = temporaryDirectory.resolve(name);
        new LigandPdbqtExportOperation().export(result.preparedLigand(), output);
        return Files.readString(output);
    }

    private void assertPdbqtIntegrity(LigandPreparationResult result, String pdbqt) {
        List<String> lines = pdbqt.lines().toList();
        List<String> atomLines = lines.stream().filter(line -> line.startsWith("ATOM")).toList();
        int atomCount = result.preparedLigand().ligand().structure().getAtomCount();
        LigandFlexibilityModel flexibility = (LigandFlexibilityModel)
                result.preparedLigand().attributes().get(LigandFlexibilityModel.ATTRIBUTE_KEY);
        assertEquals(atomCount, atomLines.size());
        assertEquals(1, lines.stream().filter("ROOT"::equals).count());
        assertEquals(1, lines.stream().filter("ENDROOT"::equals).count());
        assertEquals(flexibility.torsionalDegreesOfFreedom(),
                lines.stream().filter(line -> line.startsWith("BRANCH ")).count());
        assertEquals(flexibility.torsionalDegreesOfFreedom(),
                lines.stream().filter(line -> line.startsWith("ENDBRANCH ")).count());
        assertEquals("TORSDOF " + flexibility.torsionalDegreesOfFreedom(), lines.getLast());
        HashSet<Integer> serials = new HashSet<>();
        atomLines.forEach(line -> serials.add(Integer.parseInt(line.substring(6, 11).trim())));
        assertEquals(atomCount, serials.size());
    }

    private List<String> names(List<Atom> atoms) {
        return atoms.stream().map(Atom::getName).toList();
    }
}
