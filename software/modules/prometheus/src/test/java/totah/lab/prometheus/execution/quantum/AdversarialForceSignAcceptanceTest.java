package totah.lab.prometheus.execution.quantum;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.execution.quantum.QuantumResult.CartesianField;
import totah.lab.prometheus.execution.quantum.QuantumResult.CartesianUnit;
import totah.lab.prometheus.execution.quantum.QuantumResult.Energy;
import totah.lab.prometheus.execution.quantum.QuantumResult.EnergyUnit;
import totah.lab.prometheus.execution.quantum.QuantumResult.Vector3;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial acceptance tests for the force-sign consistency gate on
 * {@link QuantumResult} — A4 and A3(c) of docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md.
 * The fixture gradient has no zero components and is not antisymmetric, so a
 * row swap or sign flip cannot cancel out.
 */
class AdversarialForceSignAcceptanceTest {

    private static final String IDENTITY = "a".repeat(64);
    private static final Instant COMPLETED = Instant.parse("2026-08-23T00:00:00Z");

    private static QuantumResult result(List<Vector3> gradient, List<Vector3> force) {
        return new QuantumResult(IDENTITY, "PYSCF_NUMERICAL_WORKER", "2.14.0",
                ConvergenceStatus.CONVERGED,
                Optional.of(new Energy(-76.4008431, EnergyUnit.HARTREE)),
                Optional.of(new CartesianField(gradient, CartesianUnit.HARTREE_PER_BOHR)),
                Optional.of(new CartesianField(force, CartesianUnit.HARTREE_PER_BOHR)),
                Map.of(), Map.of(), COMPLETED);
    }

    /**
     * TEST_ID: A4 — F = −∇E componentwise. The exact negation passes the gate;
     * the sign-flipped variant (force == gradient) fails. Both directions are
     * asserted. Fixture from the spec: gradient
     * [[0.13, -0.27, 0.41], [-0.11, 0.29, -0.37]] hartree/bohr.
     */
    @Test
    void a4_exactNegativeGradientPassesAndSignFlipFails() {
        List<Vector3> gradient = List.of(
                new Vector3(0.13, -0.27, 0.41),
                new Vector3(-0.11, 0.29, -0.37));
        List<Vector3> force = List.of(
                new Vector3(-0.13, 0.27, -0.41),
                new Vector3(0.11, -0.29, 0.37));

        assertThat(result(gradient, force).forceIsNegativeGradient(0.0)).isTrue();
        assertThat(result(gradient, gradient).forceIsNegativeGradient(0.0)).isFalse();
    }

    /**
     * TEST_ID: A3 (c) — the sign gate never accepts NaN or Infinity: a
     * non-finite component is a failed comparison, not a consistent one, even
     * under the largest finite tolerance.
     */
    @Test
    void a3_signGateNeverAcceptsNonFiniteComponents() {
        List<Vector3> gradient = List.of(
                new Vector3(0.13, -0.27, 0.41),
                new Vector3(-0.11, 0.29, -0.37));
        List<Vector3> nanForce = List.of(
                new Vector3(-0.13, Double.NaN, -0.41),
                new Vector3(0.11, -0.29, 0.37));
        assertThat(result(gradient, nanForce).forceIsNegativeGradient(Double.MAX_VALUE)).isFalse();

        List<Vector3> infiniteGradient = List.of(
                new Vector3(0.13, -0.27, 0.41),
                new Vector3(Double.POSITIVE_INFINITY, 0.29, -0.37));
        List<Vector3> infiniteForce = List.of(
                new Vector3(-0.13, 0.27, -0.41),
                new Vector3(Double.NEGATIVE_INFINITY, -0.29, 0.37));
        assertThat(result(infiniteGradient, infiniteForce).forceIsNegativeGradient(Double.MAX_VALUE))
                .isFalse();
    }
}
