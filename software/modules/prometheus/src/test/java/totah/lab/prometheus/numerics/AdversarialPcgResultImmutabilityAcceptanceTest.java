package totah.lab.prometheus.numerics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * TEST_ID: B8 (case 1) — {@code PreconditionedConjugateGradientSolver.Result}
 * must be immutable through every accessor: mutating the array returned by
 * {@code solution()} must be invisible to subsequent readers
 * (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md, B8).
 */
class AdversarialPcgResultImmutabilityAcceptanceTest {

    /**
     * TEST_ID: B8 — mutate element 0 of the returned solution array, re-fetch,
     * and assert the record's state is unchanged.
     */
    @Test
    void mutatingReturnedSolutionArrayIsInvisibleToLaterReaders() {
        PreconditionedConjugateGradientSolver.Result result =
                new PreconditionedConjugateGradientSolver.Result(
                        new double[]{1.25, -2.5}, 1, 1, 0.5, 0.25, List.of(0.5), true);

        double[] leaked = result.solution();
        leaked[0] = 999.0;
        leaked[1] = -999.0;

        assertThat(result.solution())
                .as("a second reader must see the first reader's world")
                .containsExactly(1.25, -2.5);
    }

    /**
     * TEST_ID: B8 (constructor side) — mutating the caller's array after
     * construction must not alter the record.
     */
    @Test
    void mutatingCallerArrayAfterConstructionIsInvisible() {
        double[] solution = {1.25, -2.5};
        PreconditionedConjugateGradientSolver.Result result =
                new PreconditionedConjugateGradientSolver.Result(
                        solution, 1, 1, 0.5, 0.25, List.of(0.5), true);

        solution[0] = 999.0;

        assertThat(result.solution()).containsExactly(1.25, -2.5);
    }
}
