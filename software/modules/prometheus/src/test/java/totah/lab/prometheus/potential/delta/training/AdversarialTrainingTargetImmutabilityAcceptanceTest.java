package totah.lab.prometheus.potential.delta.training;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * TEST_ID: B8 (case 2) — {@code DeltaTrainingDataset.TrainingTarget} must be
 * immutable through every accessor: mutating the array returned by
 * {@code residualForces()} must be invisible to subsequent readers
 * (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md, B8).
 */
class AdversarialTrainingTargetImmutabilityAcceptanceTest {

    /**
     * TEST_ID: B8 — mutate element [0][0] of the returned residual-force
     * array, re-fetch, and assert the record's state is unchanged.
     */
    @Test
    void mutatingReturnedResidualForcesIsInvisibleToLaterReaders() {
        DeltaTrainingDataset.TrainingTarget target = new DeltaTrainingDataset.TrainingTarget(
                "snapshot-001", "qm", -0.75, new double[][]{{0.13, -0.27, 0.41}});

        double[][] leaked = target.residualForces();
        leaked[0][0] = 999.0;

        assertThat(target.residualForces()[0])
                .as("a second reader must see the first reader's world")
                .containsExactly(0.13, -0.27, 0.41);
    }

    /**
     * TEST_ID: B8 (constructor side) — mutating the caller's array after
     * construction must not alter the record.
     */
    @Test
    void mutatingCallerArrayAfterConstructionIsInvisible() {
        double[][] forces = {{0.13, -0.27, 0.41}};
        DeltaTrainingDataset.TrainingTarget target = new DeltaTrainingDataset.TrainingTarget(
                "snapshot-001", "qm", -0.75, forces);

        forces[0][0] = 999.0;

        assertThat(target.residualForces()[0]).containsExactly(0.13, -0.27, 0.41);
    }
}
