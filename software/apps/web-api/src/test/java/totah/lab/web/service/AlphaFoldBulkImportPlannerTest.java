package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AlphaFoldBulkImportPlannerTest {

    private static final String AAA = "AF-AAA-F1-model_v6";
    private static final String BBB = "AF-BBB-F1-model_v6";
    private static final String CCC = "AF-CCC-F1-model_v6";

    @TempDir
    Path pdbDirectory;

    @TempDir
    Path rootOne;

    @TempDir
    Path rootTwo;

    @Test
    void pairsPdbGzWithCompleteFpocketOutput() throws IOException {
        writePdb(AAA);
        writePdb(BBB);
        writePdb(CCC);
        // Not a .pdb.gz: must be strictly filtered out.
        Files.writeString(
                pdbDirectory.resolve(AAA + ".cif.gz"),
                "cif"
        );

        Path aaaOut = completeRun(rootOne, AAA, "x1");
        // AAA also complete in the second root: first root wins, logged.
        completeRun(rootTwo, AAA, "z2");
        // BBB incomplete in root one, complete in root two.
        incompleteRun(rootOne, BBB, "y9");
        Path bbbOut = completeRun(rootTwo, BBB, "z3");
        // CCC has no run directory anywhere.

        AlphaFoldBulkImportPlanner.Plan plan =
                AlphaFoldBulkImportPlanner.plan(
                        pdbDirectory,
                        java.util.List.of(rootOne, rootTwo)
                );

        assertThat(plan.totalPdbFiles()).isEqualTo(3);
        assertThat(plan.pairs())
                .extracting(AlphaFoldBulkImportPlanner.StructurePair
                        ::compressedPdb)
                .extracting(path -> path.getFileName().toString())
                .containsExactly(
                        AAA + ".pdb.gz",
                        BBB + ".pdb.gz"
                );
        assertThat(plan.pairs())
                .extracting(AlphaFoldBulkImportPlanner.StructurePair
                        ::fpocketOutDirectory)
                .containsExactly(aaaOut, bbbOut);

        assertThat(plan.pairedInMultipleRoots())
                .extracting(path -> path.getFileName().toString())
                .containsExactly(AAA + ".pdb.gz");

        assertThat(plan.missingFpocket())
                .extracting(path -> path.getFileName().toString())
                .containsExactly(CCC + ".pdb.gz");

        assertThat(plan.incompleteRunDirectories())
                .extracting(path -> path.getFileName().toString())
                .containsExactly(BBB + "-y9");
    }

    @Test
    void runDirectoryWithoutSuffixDoesNotMatch() throws IOException {
        writePdb(AAA);
        // A directory named exactly like the base (no random suffix) is
        // not a run directory.
        Path out = rootOne.resolve(AAA).resolve(AAA + "_out");
        Files.createDirectories(out.resolve("pockets"));
        Files.writeString(out.resolve(AAA + "_info.txt"), "Pocket 1 :");

        AlphaFoldBulkImportPlanner.Plan plan =
                AlphaFoldBulkImportPlanner.plan(
                        pdbDirectory,
                        java.util.List.of(rootOne)
                );

        assertThat(plan.pairs()).isEmpty();
        assertThat(plan.missingFpocket()).hasSize(1);
    }

    @Test
    void missingFpocketRootIsTolerated() throws IOException {
        writePdb(AAA);
        completeRun(rootTwo, AAA, "z2");

        AlphaFoldBulkImportPlanner.Plan plan =
                AlphaFoldBulkImportPlanner.plan(
                        pdbDirectory,
                        java.util.List.of(
                                rootOne.resolve("does-not-exist"),
                                rootTwo
                        )
                );

        assertThat(plan.pairs()).hasSize(1);
    }

    @Test
    void infoFileAndPocketsDirectoryAreBothRequired()
            throws IOException {

        writePdb(AAA);
        Path out = rootOne.resolve(AAA + "-x1").resolve(AAA + "_out");
        // pockets/ present, _info.txt missing.
        Files.createDirectories(out.resolve("pockets"));

        AlphaFoldBulkImportPlanner.Plan plan =
                AlphaFoldBulkImportPlanner.plan(
                        pdbDirectory,
                        java.util.List.of(rootOne)
                );

        assertThat(plan.pairs()).isEmpty();
        assertThat(plan.incompleteRunDirectories()).hasSize(1);
    }

    private void writePdb(String baseName) throws IOException {
        Files.writeString(
                pdbDirectory.resolve(baseName + ".pdb.gz"),
                "pdb"
        );
    }

    private Path completeRun(Path root, String baseName, String suffix)
            throws IOException {

        Path out = root.resolve(baseName + "-" + suffix)
                .resolve(baseName + "_out");
        Files.createDirectories(out.resolve("pockets"));
        Files.writeString(out.resolve(baseName + "_info.txt"), "Pocket 1 :");
        return out;
    }

    private void incompleteRun(Path root, String baseName, String suffix)
            throws IOException {

        Path out = root.resolve(baseName + "-" + suffix)
                .resolve(baseName + "_out");
        Files.createDirectories(out.resolve("pockets"));
        // No _info.txt.
    }
}
