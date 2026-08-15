package totah.lab.prometheus.execution.quantum;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable operational controls. These values may affect how a calculation is
 * executed, but may not override scientific fields in CalculationSpecification.
 */
public record QuantumExecutionOptions(
        int threadCount,
        long memoryLimitMib,
        Path workingDirectory,
        List<String> preferredBackendIds,
        Optional<VerifiedInitialGuess> initialGuess) {

    public QuantumExecutionOptions {
        if (threadCount < 1) throw new IllegalArgumentException("threadCount must be positive");
        if (memoryLimitMib < 1) throw new IllegalArgumentException("memoryLimitMib must be positive");
        workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        preferredBackendIds = List.copyOf(Objects.requireNonNull(preferredBackendIds, "preferredBackendIds"));
        initialGuess = Objects.requireNonNull(initialGuess, "initialGuess");
    }

    public static QuantumExecutionOptions local(Path workingDirectory, int threadCount, long memoryLimitMib) {
        return new QuantumExecutionOptions(threadCount, memoryLimitMib, workingDirectory, List.of(), Optional.empty());
    }

    /** A checksum-bound reusable intermediate; it never changes scientific identity. */
    public record VerifiedInitialGuess(String scientificIdentity, Path artifact, String sha256) {
        public VerifiedInitialGuess {
            requireNonBlank(scientificIdentity, "scientificIdentity");
            artifact = Objects.requireNonNull(artifact, "artifact").toAbsolutePath().normalize();
            requireSha256(sha256);
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
    }

    private static void requireSha256(String value) {
        requireNonBlank(value, "sha256");
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be lowercase hexadecimal");
    }
}
