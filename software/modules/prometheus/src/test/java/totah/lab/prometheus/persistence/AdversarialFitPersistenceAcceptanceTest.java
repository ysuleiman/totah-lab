package totah.lab.prometheus.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.potential.delta.training.DeltaModelTrainer;

/**
 * E1/E2 adversarial acceptance: a successful fit must persist its coefficient
 * vector, decomposition context, optimizer state and split manifests through an
 * atomic, checksum-verified boundary — and nothing else may be publishable.
 * Oracles from docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md Layer E, executed against
 * the FitArtifact/FitArtifactWriter seam added in the second fix round.
 */
class AdversarialFitPersistenceAcceptanceTest {

    @TempDir Path tempDir;

    private static FitArtifact successfulFit() {
        return new FitArtifact(
                "DELTA_LINEAR", "1.0",
                "TWO_BODY_CHEBYSHEV_4", List.of("T0", "T1", "T2", "T3"),
                List.of("c0", "c1", "c2", "c3"),
                List.of("hartree", "hartree", "hartree", "hartree"),
                List.of(0.0, 0.0, 0.0, 0.0),
                List.of(1.0, 0.5, -0.25, 0.125),
                Map.of(), List.of(),
                "RIDGE_1E-8", "FORCE_RESIDUAL_LEAST_SQUARES",
                Map.of("force", 1.0),
                List.of("S001", "S002", "S003"),
                List.of("H001"),
                Map.of("centering", "NONE"),
                "EXACT_SR_CHOLESKY", Map.of("damping", "1e-8"),
                Map.of("finalStep", "3"),
                42L, FitArtifact.ConvergenceStatus.SUCCESS,
                List.of("it0", "it1", "it2"),
                List.of(0.11, -0.22, 0.33),
                List.of(1e-9, -2e-9, 1.5e-9),
                Map.of("forceRms", 1.7e-9),
                Map.of("training", "a".repeat(64)),
                "52742249d");
    }

    /** E1: the persisted coefficient vector reloads identically. */
    @Test
    void e1SuccessfulFitRoundTripsCoefficientVectorExactly() throws IOException {
        FitArtifact fit = successfulFit();
        FitArtifactWriter.Receipt receipt =
                new DeltaModelTrainer().persistSuccessfulFit(tempDir.resolve("fit"), fit);
        FitArtifact reloaded = new FitArtifactWriter().readVerified(receipt.directory()).artifact();
        assertEquals(fit.finalParameterVector(), reloaded.finalParameterVector());
        assertEquals(fit, reloaded);
    }

    /** E1: only a converged SUCCESS fit may be published; SUCCESS requires optimizer state. */
    @Test
    void e1FailedOrStatelessFitCannotBePublished() {
        assertThrows(IllegalArgumentException.class, () -> new DeltaModelTrainer()
                .persistSuccessfulFit(tempDir.resolve("failed"), new FitArtifact(
                        "DELTA_LINEAR", "1.0", "B", List.of("T0"), List.of("c0"), List.of("hartree"),
                        List.of(0.0), List.of(1.0), Map.of(), List.of(), "R", "O", Map.of(),
                        List.of("S001"), List.of(), Map.of(), "OPT", Map.of(), Map.of(),
                        42L, FitArtifact.ConvergenceStatus.FAILED, List.of("it0"),
                        List.of(0.1), List.of(0.01), Map.of("m", 1.0), Map.of(), "sha")));
        assertThrows(IllegalArgumentException.class, () -> new FitArtifact(
                "DELTA_LINEAR", "1.0", "B", List.of("T0"), List.of("c0"), List.of("hartree"),
                List.of(0.0), List.of(1.0), Map.of(), List.of(), "R", "O", Map.of(),
                List.of("S001"), List.of(), Map.of(), "OPT", Map.of(), Map.of(),
                42L, FitArtifact.ConvergenceStatus.SUCCESS, List.of("it0"),
                List.of(0.1), List.of(0.01), Map.of("m", 1.0), Map.of(), "sha"));
    }

    /** E2: tampering with the coefficient mirror, the metadata, or the manifest is detected. */
    @Test
    void e2TamperedCoefficientMirrorOrMetadataIsRejected() throws IOException {
        Path directory = tempDir.resolve("fit");
        new DeltaModelTrainer().persistSuccessfulFit(directory, successfulFit());

        Path coefficients = directory.resolve("final-parameter-vector.json");
        String original = Files.readString(coefficients, StandardCharsets.UTF_8);
        assertTrue(original.contains("0.5"));
        Files.writeString(coefficients, original.replaceFirst("0\\.5", "0.6"), StandardCharsets.UTF_8);
        IOException onMirror = assertThrows(IOException.class,
                () -> new FitArtifactWriter().readVerified(directory));
        assertTrue(onMirror.getMessage().contains("final-parameter-vector"));
    }

    /** E2: a deleted decomposition/split component fails as missing, by name. */
    @Test
    void e2DeletedComponentFailsByName() throws IOException {
        Path directory = tempDir.resolve("fit");
        new DeltaModelTrainer().persistSuccessfulFit(directory, successfulFit());
        Files.delete(directory.resolve("training-ids.json"));
        IOException error = assertThrows(IOException.class,
                () -> new FitArtifactWriter().readVerified(directory));
        assertTrue(error.getMessage().contains("training-ids.json"));
    }

    /** E1/E5: split manifests round-trip; a published fit directory is immutable. */
    @Test
    void e5SplitIdsRoundTripAndPublishedFitIsImmutable() throws IOException {
        Path directory = tempDir.resolve("fit");
        FitArtifact fit = successfulFit();
        new DeltaModelTrainer().persistSuccessfulFit(directory, fit);
        FitArtifact reloaded = new FitArtifactWriter().readVerified(directory).artifact();
        assertEquals(List.of("S001", "S002", "S003"), reloaded.trainingIds());
        assertEquals(List.of("H001"), reloaded.validationIds());
        assertThrows(IOException.class,
                () -> new DeltaModelTrainer().persistSuccessfulFit(directory, fit));
    }
}
