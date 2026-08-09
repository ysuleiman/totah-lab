package totah.lab.daedalus.docking.sequential;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.daedalus.docking.VinaDockingOptions;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedCofactorDockingWorkflowTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void docksCofactorThenUsesItsExplicitPoseAsRigidReceptor()
            throws Exception {

        Path cofactorTemplate = temporaryDirectory.resolve("cofactor-template.pdbqt");
        Files.writeString(cofactorTemplate, """
                MODEL 2
                REMARK VINA RESULT: -8.2 0.0 0.0
                ROOT
                ATOM      1  C1  SAM Z   1       8.000   0.000   0.000  1.00  0.00     0.100 C
                ENDROOT
                TORSDOF 0
                ENDMDL
                """);
        Path fakeVina = fakeVina("""
                #!/bin/bash
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--out" ]; then
                    output="$2"
                    break
                  fi
                  shift
                done
                if [[ "$output" == *"cofactor-poses.pdbqt" ]]; then
                  cp "%s" "$output"
                else
                  printf 'REMARK ligand poses\n' > "$output"
                fi
                echo "      1        -8.2      0.000      0.000"
                """.formatted(cofactorTemplate));

        FixedCofactorDockingRequest request = request(2);
        FixedCofactorDockingResult result =
                new FixedCofactorDockingWorkflow(fakeVina).run(request);

        assertEquals("2", result.selectedCofactorPose().provenance()
                .get("pdbqt-model"));
        assertEquals(1, result.receptorAssembly().fixedCofactors().size());
        assertEquals(0, result.cofactorDocking().exitCode());
        assertEquals(0, result.ligandDocking().exitCode());
        assertTrue(Files.readString(request.artifacts().receptorAssemblyPdb())
                .contains("HETATM"));
        String receptorPdbqt = Files.readString(
                request.artifacts().receptorAssemblyPdbqt());
        assertTrue(receptorPdbqt.contains("8.000"));
        String[] cofactorFields = receptorPdbqt.lines()
                .filter(line -> line.contains("SAM"))
                .findFirst()
                .orElseThrow()
                .trim()
                .split("\\s+");
        assertEquals(
                0.1,
                Double.parseDouble(cofactorFields[cofactorFields.length - 2]));
        assertEquals("C", cofactorFields[cofactorFields.length - 1]);
        assertTrue(Files.isRegularFile(request.artifacts().ligandPosesPdbqt()));
    }

    @Test
    void rejectsARequestedCofactorModelThatWasNotProduced()
            throws Exception {

        Path cofactorTemplate = temporaryDirectory.resolve("only-model-one.pdbqt");
        Files.writeString(cofactorTemplate, """
                MODEL 1
                ROOT
                ATOM      1  C1  SAM Z   1       8.000   0.000   0.000  1.00  0.00     0.100 C
                ENDROOT
                TORSDOF 0
                ENDMDL
                """);
        Path fakeVina = fakeVina("""
                #!/bin/bash
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--out" ]; then cp "%s" "$2"; break; fi
                  shift
                done
                """.formatted(cofactorTemplate));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new FixedCofactorDockingWorkflow(fakeVina)
                        .run(request(2)));

        assertTrue(exception.getMessage().contains("requested model 2"));
    }

    private FixedCofactorDockingRequest request(int modelNumber)
            throws IOException {
        Path proteinPdbqt = touch("protein.pdbqt");
        Path cofactorPdbqt = touch("cofactor.pdbqt");
        Path ligandPdbqt = touch("ligand.pdbqt");
        FixedCofactorDockingArtifacts artifacts =
                new FixedCofactorDockingArtifacts(
                        temporaryDirectory.resolve("cofactor-poses.pdbqt"),
                        temporaryDirectory.resolve("protein-cofactor.pdb"),
                        temporaryDirectory.resolve("protein-cofactor.pdbqt"),
                        temporaryDirectory.resolve("ligand-poses.pdbqt"));
        VinaDockingOptions options = VinaDockingOptions.ofBox(
                0.0, 0.0, 0.0, 20.0, 20.0, 20.0);
        return new FixedCofactorDockingRequest(
                "sam-run",
                PreparedProtein.of(protein()),
                PreparedLigand.of(cofactor()),
                "sam",
                "SAM",
                modelNumber,
                proteinPdbqt,
                cofactorPdbqt,
                ligandPdbqt,
                options,
                options,
                artifacts);
    }

    private static Protein protein() {
        Atom atom = preparedAtom("CA", Element.C, 0.0, 0.0, "C");
        return new Protein(
                "protein", null, "Protein", null, null, null,
                new Structure(List.of(new Chain(
                        "A", List.of(new Residue("ALA", 1, List.of(atom)))))));
    }

    private static Ligand cofactor() {
        Atom atom = preparedAtom("C1", Element.C, 1.0, 0.1, "C");
        return new Ligand(
                "SAM", "SAM", "SAM", null, null, null,
                new Structure(List.of(new Chain(
                        "Z", List.of(new Residue("SAM", 1, List.of(atom)))))));
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

    private Path touch(String name) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, "");
        return path;
    }

    private Path fakeVina(String script) throws IOException {
        Path path = temporaryDirectory.resolve(
                "fake-vina-" + System.nanoTime());
        Files.writeString(path, script);
        Files.setPosixFilePermissions(
                path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }
}
