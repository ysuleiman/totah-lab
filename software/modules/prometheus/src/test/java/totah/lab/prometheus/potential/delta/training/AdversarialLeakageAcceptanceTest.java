package totah.lab.prometheus.potential.delta.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.potential.delta.training.BasisPreflightResult.Classification;
import totah.lab.prometheus.potential.delta.training.BasisPreflightResult.ColumnAssessment;

/**
 * Adversarial acceptance test C11 of docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md
 * (preprocessing leakage between train and validation).
 *
 * <p>NO-SEAM FINDING: the module contains no fitted transform to attack.
 * {@link DeltaModelTrainer} is an authorization gate only (its single method
 * {@code requirePreflight} enforces {@link BasisPreflightResult#pass()});
 * there is no centering, no scaling, no PCA/feature selection, no ridge/secant
 * solve anywhere under {@code totah.lab.prometheus.potential.delta.training}.
 * {@link DeltaTrainingDataset} is documented as "training-only data boundary;
 * deliberately has no holdout accessor", and {@link CrossValidationFold}
 * carries split identity only — "target values are deliberately absent". The
 * leak-amplifying oracle (holdout targets near +1000 shifting a pooled fit by
 * +200; one flipped holdout label leaving the model bit-identical) therefore
 * has no executable seam: there is no model whose training-set predictions
 * could be compared before and after a holdout-label flip. The only
 * data-derived statistics in the module are computed by {@link
 * BasisPreflightAnalyzer} on geometry/derivative feature matrices (targets
 * never enter its signature), and it owns no train/holdout split — the caller
 * chooses the row scope.
 *
 * <p>Implemented instead, labeled as the strongest applicable sub-oracles:
 * <ul>
 *   <li>C11-SUB-a — determinism: the analyzer is a pure function of its
 *       inputs; identical matrices refit to a bit-identical result.</li>
 *   <li>C11-SUB-b — scope transparency (leak-amplifying geometry): appending
 *       holdout-magnitude rows (1000x the training scale) DOES change the
 *       analyzer's statistics, proving nothing inside pools, splits, or
 *       robust-normalizes — leakage prevention is entirely delegated to the
 *       caller's choice of rows, and a caller that pools train+holdout gets
 *       pooled statistics.</li>
 *   <li>C11-SUB-c — split integrity: {@link CrossValidationFold} rejects an
 *       overlapping train/validation id partition.</li>
 *   <li>C11-SUB-d — gate wiring: {@link DeltaModelTrainer} refuses to train
 *       under a failing preflight and only under a passing one.</li>
 * </ul>
 */
class AdversarialLeakageAcceptanceTest {

    /**
     * TEST_ID: C11-SUB-a — deterministic refit. Two analyses of the same
     * feature matrices must agree bit-for-bit on every reported magnitude and
     * classification (a nondeterministic or input-leaking statistic could not).
     */
    @Test void c11_analyzerRefitsBitIdenticallyOnIdenticalInputs() {
        double[][] energy = {{0.5, -0.25}, {-0.7, 0.5}, {1.1, -0.55}, {-0.3, 0.15}};
        double[][] derivative = {{0.31, 0.4}, {-0.12, -0.2}, {0.44, 0.6}, {-0.05, -0.1}};
        BasisPreflightResult first = new BasisPreflightAnalyzer()
                .analyze(energy, derivative, true, true, true, 10);
        BasisPreflightResult second = new BasisPreflightAnalyzer()
                .analyze(energy, derivative, true, true, true, 10);
        assertThat(second.columns()).hasSameSizeAs(first.columns());
        for (int column = 0; column < first.columns().size(); column++) {
            ColumnAssessment a = first.columns().get(column);
            ColumnAssessment b = second.columns().get(column);
            assertThat(b.classification()).isEqualTo(a.classification());
            assertThat(Double.doubleToRawLongBits(b.maximumEnergyMagnitude()))
                    .isEqualTo(Double.doubleToRawLongBits(a.maximumEnergyMagnitude()));
            assertThat(Double.doubleToRawLongBits(b.maximumDerivativeMagnitude()))
                    .isEqualTo(Double.doubleToRawLongBits(a.maximumDerivativeMagnitude()));
        }
        assertThat(second.duplicateMap()).isEqualTo(first.duplicateMap());
        assertThat(second.pass()).isEqualTo(first.pass());
    }

    /**
     * TEST_ID: C11-SUB-b — leak-amplifying scope transparency. Training rows
     * have O(1) magnitudes; holdout rows are ~1000x larger. A train-only
     * analysis reports train-scale magnitudes; a pooled analysis reports
     * holdout-dominated magnitudes. The difference proves the row scope is
     * fully caller-controlled: the analyzer has no internal holdout concept
     * and no robust normalization that could mask a pooling mistake. Any
     * future leakage fix or regression must therefore be visible at the
     * call site, and this test pins that delegation honestly.
     */
    @Test void c11_analyzerScopeIsCallerControlled_pooledRowsShiftStatistics() {
        double[][] trainEnergy = {{0.5}, {-0.7}, {1.1}, {-0.3}};
        double[][] trainDerivative = {{0.31}, {-0.12}, {0.44}, {-0.05}};
        double[][] pooledEnergy = {{0.5}, {-0.7}, {1.1}, {-0.3}, {1000.0}, {-1001.0}};
        double[][] pooledDerivative = {{0.31}, {-0.12}, {0.44}, {-0.05}, {2000.0}, {-1999.0}};

        BasisPreflightResult trainOnly = new BasisPreflightAnalyzer()
                .analyze(trainEnergy, trainDerivative, true, true, true, 10);
        BasisPreflightResult pooled = new BasisPreflightAnalyzer()
                .analyze(pooledEnergy, pooledDerivative, true, true, true, 10);

        assertThat(trainOnly.columns().get(0).maximumEnergyMagnitude()).isEqualTo(1.1);
        assertThat(trainOnly.columns().get(0).classification())
                .isEqualTo(Classification.OBSERVABLE);
        assertThat(pooled.columns().get(0).maximumEnergyMagnitude()).isEqualTo(1001.0);
        assertThat(pooled.columns().get(0).maximumEnergyMagnitude())
                .isNotEqualTo(trainOnly.columns().get(0).maximumEnergyMagnitude());
    }

    /**
     * TEST_ID: C11-SUB-c — split integrity: a fold whose training and
     * validation id sets overlap is rejected before any fitting can be
     * authorized against it.
     */
    @Test void c11_crossValidationFoldRejectsTrainValidationOverlap() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new CrossValidationFold("fold-0", Set.of("S001", "S002"), Set.of("S002")));
        assertThatCode(() -> new CrossValidationFold("fold-0",
                Set.of("S001", "S002"), Set.of("S003"))).doesNotThrowAnyException();
    }

    /**
     * TEST_ID: C11-SUB-d — gate wiring: the trainer refuses to fit under a
     * failing preflight verdict and accepts a passing one, so no training can
     * be driven by an unauthorized (e.g. structurally null or duplicate)
     * basis.
     */
    @Test void c11_trainerIsGatedOnPreflightVerdict() {
        BasisPreflightResult passing = new BasisPreflightResult(
                List.of(new ColumnAssessment(0, Classification.OBSERVABLE, 1.1, 0.44)),
                Map.of(), true, true, true, true);
        BasisPreflightResult failing = new BasisPreflightResult(
                List.of(new ColumnAssessment(0, Classification.STRUCTURALLY_NULL, 0.0, 0.0)),
                Map.of(), true, true, true, true);
        DeltaModelTrainer trainer = new DeltaModelTrainer();
        assertThatIllegalStateException().isThrownBy(() -> trainer.requirePreflight(failing));
        assertThatCode(() -> trainer.requirePreflight(passing)).doesNotThrowAnyException();
    }
}
