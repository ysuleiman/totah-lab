package totah.lab.prometheus.variational;

import java.util.Map;
import java.util.Objects;

/** Streaming H2 Rayleigh evaluation using constant-size sufficient statistics. */
public final class HydrogenMoleculeStreamingRayleighEvaluator {
    public FunctionalEvaluation evaluate(DifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian hamiltonian, HydrogenMoleculeImportanceBatches batches) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(hamiltonian, "hamiltonian");
        Objects.requireNonNull(batches, "batches");
        Accumulator accumulator = new Accumulator();
        batches.forEachBatch(batch -> batch.forEach(point -> {
            DifferentiableStateEvaluation evaluation = state.evaluateWithDerivatives(point.coordinates());
            double psi = evaluation.value().real(), weight = point.weight();
            double kineticPsi = -0.5 * evaluation.coordinateLaplacian().value().real();
            double potential = hamiltonian.potential(point.coordinates());
            double hPsi = kineticPsi + potential * psi;
            accumulator.norm += weight * psi * psi;
            accumulator.kinetic += weight * psi * kineticPsi;
            accumulator.potential += weight * psi * psi * potential;
            accumulator.hamiltonianSquare += weight * hPsi * hPsi;
            accumulator.evaluations++;
        }));
        if (accumulator.norm < 1e-14) {
            return new FunctionalEvaluation(Double.MAX_VALUE, Map.of("norm", accumulator.norm));
        }
        double energy = (accumulator.kinetic + accumulator.potential) / accumulator.norm;
        double variance = Math.max(0.0, accumulator.hamiltonianSquare / accumulator.norm - energy * energy);
        return new FunctionalEvaluation(energy, Map.of("norm", accumulator.norm,
                "kinetic", accumulator.kinetic / accumulator.norm,
                "potential", accumulator.potential / accumulator.norm,
                "virial_ratio", -2 * (accumulator.kinetic / accumulator.norm)
                        / (accumulator.potential / accumulator.norm),
                "local_energy_variance", variance,
                "state_evaluations", (double) accumulator.evaluations,
                "expected_state_evaluations", (double) batches.count(),
                "redundant_state_evaluations", 0.0));
    }

    private static final class Accumulator {
        private double norm, kinetic, potential, hamiltonianSquare;
        private long evaluations;
    }
}
