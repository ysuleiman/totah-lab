package totah.lab.daedalus.fpocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FpocketBatchRunnerTest {
    @TempDir Path temporary;

    @Test
    void validatesContentRatherThanDirectoryExistence() throws Exception {
        Path output = temporary.resolve("1ABC-assembly1_out");
        Files.createDirectories(output.resolve("pockets"));
        assertFalse(FpocketBatchRunner.validOutput(output, "1ABC-assembly1"));
        Files.writeString(output.resolve("1ABC-assembly1_info.txt"), "Pocket 1");
        Files.writeString(output.resolve("pockets/pocket1_atm.cif"), "data_x");
        assertFalse(FpocketBatchRunner.validOutput(output, "1ABC-assembly1"));
        Files.writeString(output.resolve("pockets/pocket1_vert.pqr"), "ATOM");
        assertTrue(FpocketBatchRunner.validOutput(output, "1ABC-assembly1"));
        assertEquals(1, FpocketBatchRunner.countPockets(output));
    }
}
