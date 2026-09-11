package totah.lab.athena.energy.openmm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.energy.EnergyEvaluationException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMmProcessExecutorTest {
    @TempDir Path temporary;

    @Test void rejectsNonPositiveDeadline() throws Exception {
        Path script = script("exit 0");
        assertThatThrownBy(() -> new OpenMmProcessExecutor(Path.of("/bin/sh"), script,
                temporary.resolve("receipts"), Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void terminatesStalledProcessAtDeadline() throws Exception {
        Path script = script("sleep 5");
        var executor = new OpenMmProcessExecutor(Path.of("/bin/sh"), script,
                temporary.resolve("receipts"), Duration.ofMillis(50));
        assertThatThrownBy(() -> executor.execute(executor.mapper().createObjectNode()))
                .isInstanceOf(EnergyEvaluationException.class).hasMessageContaining("timed out");
    }

    private Path script(String body) throws Exception {
        Path script = temporary.resolve("runner.sh");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        return script;
    }
}
