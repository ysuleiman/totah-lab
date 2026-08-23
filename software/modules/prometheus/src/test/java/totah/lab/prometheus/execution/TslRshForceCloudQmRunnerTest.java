package totah.lab.prometheus.execution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TslRshForceCloudQmRunnerTest {
    @TempDir Path temporary;

    @Test void deletedManifestEntryFailsClosedInsteadOfBypassingChecksum() throws Exception {
        Files.writeString(temporary.resolve("SHA256SUMS"), "0".repeat(64) + "  deleted.xyz\n");
        assertThatThrownBy(() -> TslRshForceCloudQmRunner.verifyFrozenInputs(temporary))
                .isInstanceOf(IOException.class).hasMessageContaining("frozen input missing: deleted.xyz");
    }
}
