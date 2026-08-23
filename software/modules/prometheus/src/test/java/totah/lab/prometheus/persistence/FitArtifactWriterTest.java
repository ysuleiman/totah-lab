package totah.lab.prometheus.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.prometheus.potential.QuantumCoordinates;
import totah.lab.prometheus.potential.delta.basis.BasisEvaluation;
import totah.lab.prometheus.potential.delta.basis.ManyBodyBasis;
import totah.lab.prometheus.potential.delta.model.DeltaModelIdentity;
import totah.lab.prometheus.potential.delta.model.DeltaModelParameters;
import totah.lab.prometheus.potential.delta.model.LinearDeltaModel;
import totah.lab.prometheus.potential.delta.training.DeltaModelTrainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitArtifactWriterTest {
    @TempDir Path temp;

    @Test
    void deltaFitPersistsReloadsAndReproducesPredictionsBitExactly() throws Exception {
        FitArtifact fit = fixture();
        Path bundle = temp.resolve("delta-fit");
        var receipt = new DeltaModelTrainer().persistSuccessfulFit(bundle, fit);
        long expectedBits = Double.doubleToLongBits(model(fit.finalParameterVector()).evaluate(point()).energy());

        fit = null; // The in-memory fit/model state is no longer the source of truth.
        FitArtifact reloaded = new FitArtifactWriter().readVerified(bundle).artifact();
        long actualBits = Double.doubleToLongBits(model(reloaded.finalParameterVector()).evaluate(point()).energy());

        assertThat(actualBits).isEqualTo(expectedBits);
        assertThat(receipt.artifactSha256()).hasSize(64);
        assertThat(Files.readString(bundle.resolve(FitArtifactWriter.ARTIFACT_SHA256)))
                .startsWith(receipt.artifactSha256());
    }

    @Test
    void deletedCoefficientVectorFailsCompleteness() throws Exception {
        Path bundle = persist();
        Files.delete(bundle.resolve("final-parameter-vector.json"));
        assertMissing(bundle, "final-parameter-vector.json");
    }

    @Test
    void deletedBasisOrderFailsCompleteness() throws Exception {
        Path bundle = persist();
        Files.delete(bundle.resolve("basis-order.json"));
        assertMissing(bundle, "basis-order.json");
    }

    @Test
    void namesAndParameterVectorLengthMismatchFailsBeforePersistence() {
        FitArtifact valid = fixture();
        assertThatIllegalArgumentException().isThrownBy(() -> new FitArtifact(
                valid.modelFamily(), valid.modelVersion(), valid.basisDefinition(), valid.basisOrder(),
                List.of("c0"), valid.parameterUnits(), valid.initialParameterVector(),
                valid.finalParameterVector(), valid.frozenParameters(), valid.parameterBounds(),
                valid.regularization(), valid.objectiveDefinition(), valid.objectiveWeights(),
                valid.trainingIds(), valid.validationIds(), valid.normalizationState(), valid.optimizer(),
                valid.optimizerConfiguration(), valid.optimizerState(), valid.seed(), valid.convergenceStatus(),
                valid.iterationHistory(), valid.predictions(), valid.residuals(), valid.finalMetrics(),
                valid.sourceDatasetChecksums(), valid.codeCommitSha()))
                .withMessageContaining("equal length");
    }

    @Test
    void missingTrainingOrValidationSplitFailsCompleteness() throws Exception {
        Path trainingMissing = persist("training-missing");
        Files.delete(trainingMissing.resolve("training-ids.json"));
        assertMissing(trainingMissing, "training-ids.json");
        Path validationMissing = persist("validation-missing");
        Files.delete(validationMissing.resolve("validation-ids.json"));
        assertMissing(validationMissing, "validation-ids.json");
    }

    @Test
    void missingOptimizerStateFailsCompleteness() throws Exception {
        Path bundle = persist();
        Files.delete(bundle.resolve("optimizer-state.json"));
        assertMissing(bundle, "optimizer-state.json");
    }

    private void assertMissing(Path bundle, String component) {
        assertThatThrownBy(() -> new FitArtifactWriter().readVerified(bundle))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining(component);
    }

    private Path persist() throws Exception { return persist("fit"); }
    private Path persist(String name) throws Exception {
        Path bundle = temp.resolve(name);
        new FitArtifactWriter().persistSuccessful(bundle, fixture());
        return bundle;
    }

    private static FitArtifact fixture() {
        return new FitArtifact(
                "DELTA_LINEAR_CONSERVATIVE", "1", "two-feature deterministic oracle",
                List.of("constant", "x"), List.of("c0", "cx"), List.of("kcal/mol", "kcal/mol/angstrom"),
                List.of(0.0, 0.0), List.of(1.25, -0.5), Map.of(),
                List.of("c0:unbounded", "cx:unbounded"), "NONE",
                "ordinary least squares energy residual", Map.of("energy", 1.0),
                List.of("TRAIN-001", "TRAIN-002"), List.of("VALID-001"), Map.of("state", "IDENTITY"),
                "CLOSED_FORM_QR", Map.of("pivoting", "false"),
                Map.of("state", "NOT_APPLICABLE_STATELESS_CLOSED_FORM"), 240824L,
                FitArtifact.ConvergenceStatus.SUCCESS, List.of("iteration=0;converged=true"),
                List.of(1.0, 2.0, 3.0), List.of(0.1, -0.2, 0.0), Map.of("rms", 0.12909944487358058),
                Map.of("training", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                "52742249d7f623e3e8a7b8c6a0ed98415c12266c");
    }

    private static LinearDeltaModel model(List<Double> coefficients) {
        ManyBodyBasis basis = new ManyBodyBasis() {
            @Override public int dimension() { return 2; }
            @Override public BasisEvaluation evaluate(QuantumCoordinates coordinates) {
                double x = coordinates.coordinate(0, 0);
                return new BasisEvaluation(new double[] {1.0, x},
                        new double[][][] {{{0.0, 0.0, 0.0}}, {{1.0, 0.0, 0.0}}});
            }
        };
        return new LinearDeltaModel(basis,
                new DeltaModelParameters(new double[] {coefficients.get(0), coefficients.get(1)}),
                new DeltaModelIdentity("test", "basis", "types", "split", "commit"));
    }

    private static QuantumCoordinates point() {
        return new QuantumCoordinates(new double[][] {{2.0, 0.0, 0.0}});
    }
}
