package totah.lab.prometheus.variational.force;

import java.util.Objects;
import java.util.function.Consumer;

import totah.lab.prometheus.variational.DifferentiableQuantumState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;

/** Qian et al. (2022) Eq. 11 AC-ZV estimator with the fixed Eqs. 9-10 auxiliary. */
public final class AssarafCaffarelZvForceEstimator {
    public Result evaluate(DifferentiableQuantumState state, HydrogenMoleculeHamiltonian hamiltonian,
            HydrogenMoleculeImportanceBatches batches, int nucleusIndex) {
        return evaluate(state, hamiltonian, batches, nucleusIndex, contribution -> { });
    }

    /** The callback permits an exact deterministic second pass for the diagnostic 3-IQR transform. */
    public Result evaluate(DifferentiableQuantumState state, HydrogenMoleculeHamiltonian hamiltonian,
            HydrogenMoleculeImportanceBatches batches, int nucleusIndex, Consumer<Contribution> contributionConsumer) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(hamiltonian, "hamiltonian");
        Objects.requireNonNull(batches, "batches"); Objects.requireNonNull(contributionConsumer, "contributionConsumer");
        validateNucleus(nucleusIndex);
        var moments = new AssarafCaffarelSupport.Moments();
        batches.forEachBatch(batch -> {
            moments.observeBatch(batch.size());
            batch.forEach(point -> {
                var bundle = state.evaluateWithDerivatives(point.coordinates());
                moments.evaluated();
                var terms = AssarafCaffarelSupport.terms(point.coordinates(), bundle,
                        hamiltonian.bondLengthBohr(), nucleusIndex);
                if (terms == null) { moments.rejected(); return; }
                double psi = bundle.value().real(), weight = point.weight() * psi * psi;
                double[] force = new double[3];
                for (int i = 0; i < 3; i++) force[i] = terms.nuclearForce()[i] - terms.gradientContraction()[i];
                moments.add(weight, force);
                contributionConsumer.accept(new Contribution(weight, AssarafCaffarelSupport.vector(force)));
            });
        });
        return new Result(moments.finish(), nucleusIndex, "Qian-2022-Eq-11", "Qian-2022-Eqs-9-10");
    }

    private static void validateNucleus(int index) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("H2 nucleusIndex must be 0 or 1");
    }

    public record Contribution(double importanceWeight,
            AssarafCaffarelForceStatistics.Vector forceHartreePerBohr) { }
    public record Result(AssarafCaffarelForceStatistics rawStatistics, int nucleusIndex,
            String estimatorEquation, String auxiliaryEquation) { }
}
