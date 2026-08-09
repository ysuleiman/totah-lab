package totah.lab.hephaestus.receptor.assembly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceptorAssemblyWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesProteinAsAtomAndCofactorAsHetatm() throws Exception {
        ReceptorAssembly assembly = assembly();
        Path output = temporaryDirectory.resolve("protein-with-sam.pdb");

        var result = new ReceptorAssemblyWriter().writePdb(
                assembly, output);

        List<String> lines = Files.readAllLines(output);
        assertEquals(2, result.atomCount());
        assertTrue(lines.stream().anyMatch(line ->
                line.startsWith("ATOM") && line.contains("ALA")));
        assertTrue(lines.stream().anyMatch(line ->
                line.startsWith("HETATM") && line.contains("SAM")));
    }

    @Test
    void writesRigidPdbqtWithPreparedCofactorChargeAndType()
            throws Exception {

        Path output = temporaryDirectory.resolve("protein-with-sam.pdbqt");

        var result = new ReceptorAssemblyWriter().writeRigidPdbqt(
                assembly(), output);

        List<String> atomLines = Files.readAllLines(output).stream()
                .filter(line -> line.startsWith("ATOM")
                        || line.startsWith("HETATM"))
                .toList();
        assertEquals(2, result.rigidAtomCount());
        assertEquals(2, atomLines.size());
        String[] cofactorFields = atomLines.get(1).trim().split("\\s+");
        assertEquals(
                0.5,
                Double.parseDouble(
                        cofactorFields[cofactorFields.length - 2]));
        assertEquals("C", cofactorFields[cofactorFields.length - 1]);
    }

    private static ReceptorAssembly assembly() {
        PreparedProtein protein = PreparedProtein.of(new Protein(
                "protein", null, "Protein", null, null, null,
                new Structure(List.of(new Chain("A", List.of(new Residue(
                        "ALA",
                        1,
                        List.of(preparedAtom(
                                "CA", Element.C, 0.0, 0.0, "C")))))))));
        Ligand preparedSamLigand = ligand(preparedAtom(
                "C1", Element.C, 1.0, 0.5, "C"));
        Ligand posedSamLigand = ligand(Atom.builder()
                .name("C1")
                .element(Element.C)
                .position(new Point3D(8.0, 0.0, 0.0))
                .build());
        FixedCofactor sam = new FixedCofactor(
                "sam",
                "SAM",
                new LigandPose(
                        "sam-pose-1",
                        PreparedLigand.of(preparedSamLigand),
                        posedSamLigand,
                        Map.of("run", "sam-run")));
        return ReceptorAssembly.of(protein).withFixedCofactor(sam);
    }

    private static Ligand ligand(Atom atom) {
        return new Ligand(
                "SAM", "SAM", "SAM", null, null, null,
                new Structure(List.of(new Chain(
                        "Z",
                        List.of(new Residue("SAM", 1, List.of(atom)))))));
    }

    private static Atom preparedAtom(
            String name,
            Element element,
            double x,
            double charge,
            String autoDockType) {

        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, 0.0, 0.0))
                .charge(charge)
                .autoDockType(autoDockType)
                .build();
    }
}
