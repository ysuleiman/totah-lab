package totah.lab.prometheus.neural.ferminet.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * TEST_ID: B8 (case 3) — {@link FermiNetKfacState} block accessors must not
 * leak mutable internals: mutating an array obtained from a block accessor
 * must be invisible to subsequent readers
 * (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md, B8).
 *
 * <p>Placed in this package because {@code denseBlocks()} /
 * {@code diagonalBlocks()} and the block records are package-private.
 */
class AdversarialKfacStateImmutabilityAcceptanceTest {

    /**
     * TEST_ID: B8 — mutate element 0 of a dense block's factor array obtained
     * through the accessor, re-fetch, and assert the state is unchanged.
     */
    @Test
    void mutatingDenseBlockFactorThroughAccessorIsInvisible() {
        FermiNetKfacState state = state();

        double[] leaked = state.denseBlocks().get("interaction.0").inputFactor();
        leaked[0] = 999.0;

        assertThat(state.denseBlocks().get("interaction.0").inputFactor()[0])
                .as("a second reader must see the first reader's world")
                .isEqualTo(1.0);
    }

    /**
     * TEST_ID: B8 — same invariant for a diagonal block's curvature array.
     */
    @Test
    void mutatingDiagonalBlockCurvatureThroughAccessorIsInvisible() {
        FermiNetKfacState state = state();

        double[] leaked = state.diagonalBlocks().get("output.bias").curvature();
        leaked[0] = 999.0;

        assertThat(state.diagonalBlocks().get("output.bias").curvature()[0])
                .isEqualTo(2.0);
    }

    /**
     * TEST_ID: B8 (map level) — the block maps themselves reject mutation.
     */
    @Test
    void blockMapsRejectMutation() {
        FermiNetKfacState state = state();

        assertThatThrownBy(() -> state.denseBlocks().put("forged", denseBlock()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.diagonalBlocks().put("forged",
                new FermiNetKfacState.DiagonalBlock(new double[]{1.0})))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static FermiNetKfacState state() {
        return new FermiNetKfacState(3,
                Map.of("interaction.0", denseBlock()),
                Map.of("output.bias", new FermiNetKfacState.DiagonalBlock(new double[]{2.0, 3.0})));
    }

    private static FermiNetKfacState.DenseBlock denseBlock() {
        return new FermiNetKfacState.DenseBlock(
                2, 1,
                new double[]{1.0, 0.0, 0.0, 1.0},
                new double[]{4.0},
                new double[]{1.0, 0.0, 0.0, 1.0},
                new double[]{5.0},
                0);
    }
}
