package totah.lab.prometheus.execution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ExternalPythonExecutionPolicyTest {
    @Test
    void authorizesOnlyTheNamedHardenedNumericalWorker() {
        assertDoesNotThrow(() -> ExternalPythonExecutionPolicy.requireAuthorizedNumericalWorker(
                ExternalPythonExecutionPolicy.HARDENED_TSLRSH_WORKER));
        assertThrows(EvidenceExecutionException.class,
                () -> ExternalPythonExecutionPolicy.requireAuthorizedNumericalWorker("historical-or-arbitrary"));
    }
}
