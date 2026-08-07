package totah.lab.daedalus.analysis.disulfide;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdjacentCysteineScanTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesEveryPairWithItsSequenceContextAndDistanceClass() throws Exception {
        Path pdbDirectory = temporaryDirectory.resolve("pdb");
        Files.createDirectories(pdbDirectory);
        Files.copy(
                resource("/disulfide/adjacent-cysteines.pdb"),
                pdbDirectory.resolve("AF-TEST1-F1-model_v6.pdb"));
        Path output = temporaryDirectory.resolve("result.csv");

        AdjacentCysteineScan.ScanSummary summary =
                AdjacentCysteineScan.scan(pdbDirectory, output, 8);

        assertThat(summary.structureCount()).isEqualTo(1);
        assertThat(summary.pairCount()).isEqualTo(1);
        assertThat(summary.openPairCount()).isZero();
        assertThat(Files.readString(output))
                .contains("TEST1,A,2,3,ACCG,1,2.000,BONDED_GEOMETRY")
                .contains("91.00,93.00,92.00");
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(AdjacentCysteineScanTest.class.getResource(name).toURI());
    }
}
