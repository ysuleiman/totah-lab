package totah.lab.prometheus.variational.force;

import java.util.Objects;
import java.util.function.Consumer;

import totah.lab.prometheus.variational.DifferentiableQuantumState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;

/** Qian et al. (2022) Eqs. 6-10 AC-ZVZB estimator with no fitted auxiliary. */
public final class AssarafCaffarelZvzbForceEstimator {
    public Result evaluate(DifferentiableQuantumState state, HydrogenMoleculeHamiltonian hamiltonian,
            HydrogenMoleculeImportanceBatches batches, int nucleusIndex) {
        return evaluate(state,hamiltonian,batches,nucleusIndex,contribution->{ });
    }
    public Result evaluate(DifferentiableQuantumState state, HydrogenMoleculeHamiltonian hamiltonian,
            HydrogenMoleculeImportanceBatches batches, int nucleusIndex,Consumer<LinearContribution> consumer) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(hamiltonian, "hamiltonian");
        Objects.requireNonNull(batches, "batches");Objects.requireNonNull(consumer,"consumer"); validateNucleus(nucleusIndex);
        var accumulator = new LinearAccumulator();
        batches.forEachBatch(batch -> {
            accumulator.observeBatch(batch.size());
            batch.forEach(point -> {
                var bundle = state.evaluateWithDerivatives(point.coordinates());
                accumulator.evaluated();
                var terms = AssarafCaffarelSupport.terms(point.coordinates(), bundle,
                        hamiltonian.bondLengthBohr(), nucleusIndex);
                if (terms == null) { accumulator.rejected(); return; }
                double psi = bundle.value().real(), weight = point.weight() * psi * psi;
                double localEnergy = AssarafCaffarelSupport.localEnergy(point.coordinates(), bundle,
                        hamiltonian.potential(point.coordinates()));
                double[] constant = new double[3], coefficient = new double[3];
                for (int i = 0; i < 3; i++) {
                    constant[i] = terms.bareForce()[i] - terms.operatorRatio()[i]
                            - 2 * localEnergy * terms.q()[i];
                    coefficient[i] = 2 * terms.q()[i];
                }
                accumulator.add(weight, localEnergy, constant, coefficient);
                consumer.accept(new LinearContribution(weight,AssarafCaffarelSupport.vector(constant),
                        AssarafCaffarelSupport.vector(coefficient)));
            });
        });
        return accumulator.finish(nucleusIndex);
    }

    /** Exact second deterministic pass for per-sample forces after the sampled E_v is known. */
    public void forEachContribution(DifferentiableQuantumState state, HydrogenMoleculeHamiltonian hamiltonian,
            HydrogenMoleculeImportanceBatches batches, int nucleusIndex, double sampledMeanEnergyHartree,
            Consumer<Contribution> consumer) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(hamiltonian, "hamiltonian");
        Objects.requireNonNull(batches, "batches"); Objects.requireNonNull(consumer, "consumer");
        validateNucleus(nucleusIndex);
        batches.forEachBatch(batch -> batch.forEach(point -> {
            var bundle = state.evaluateWithDerivatives(point.coordinates());
            var terms = AssarafCaffarelSupport.terms(point.coordinates(), bundle,
                    hamiltonian.bondLengthBohr(), nucleusIndex);
            if (terms == null) return;
            double psi = bundle.value().real(), weight = point.weight() * psi * psi;
            double localEnergy = AssarafCaffarelSupport.localEnergy(point.coordinates(), bundle,
                    hamiltonian.potential(point.coordinates()));
            double[] force = new double[3];
            for (int i = 0; i < 3; i++) force[i] = terms.bareForce()[i] - terms.operatorRatio()[i]
                    + 2 * (sampledMeanEnergyHartree - localEnergy) * terms.q()[i];
            consumer.accept(new Contribution(weight, AssarafCaffarelSupport.vector(force)));
        }));
    }

    private static void validateNucleus(int index) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("H2 nucleusIndex must be 0 or 1");
    }

    public record Contribution(double importanceWeight,
            AssarafCaffarelForceStatistics.Vector forceHartreePerBohr) { }
    public record LinearContribution(double importanceWeight,AssarafCaffarelForceStatistics.Vector constant,
            AssarafCaffarelForceStatistics.Vector sampledMeanEnergyCoefficient){ }
    public record Result(AssarafCaffarelForceStatistics rawStatistics, double sampledMeanEnergyHartree,
            int nucleusIndex, String estimatorEquation, String auxiliaryEquation) { }

    private static final class LinearAccumulator {
        private double sumWeight, sumWeightSquared, weightedEnergy;
        private final double[] sumConstant = new double[3], sumCoefficient = new double[3];
        private final double[] constantSquared = new double[3], coefficientSquared = new double[3];
        private final double[] constantCoefficient = new double[3];
        private long accepted, rejected, evaluations;
        private int peakBatchSize;

        void add(double weight, double energy, double[] constant, double[] coefficient) {
            if (!Double.isFinite(weight) || weight < 0 || !Double.isFinite(energy))
                throw new IllegalArgumentException("non-finite AC-ZVZB sample");
            sumWeight += weight; sumWeightSquared += weight * weight; weightedEnergy += weight * energy;
            for (int i = 0; i < 3; i++) {
                if (!Double.isFinite(constant[i]) || !Double.isFinite(coefficient[i]))
                    throw new IllegalArgumentException("non-finite AC-ZVZB contribution");
                sumConstant[i] += weight * constant[i]; sumCoefficient[i] += weight * coefficient[i];
                constantSquared[i] += weight * constant[i] * constant[i];
                coefficientSquared[i] += weight * coefficient[i] * coefficient[i];
                constantCoefficient[i] += weight * constant[i] * coefficient[i];
            }
            accepted++;
        }
        void evaluated() { evaluations++; }
        void rejected() { rejected++; }
        void observeBatch(int size) { peakBatchSize = Math.max(peakBatchSize, size); }

        Result finish(int nucleusIndex) {
            if (!(sumWeight > 0) || !(sumWeightSquared > 0))
                throw new IllegalArgumentException("zero sampled norm");
            double energy = weightedEnergy / sumWeight;
            double effective = sumWeight * sumWeight / sumWeightSquared;
            double[] mean = new double[3], variance = new double[3], error = new double[3];
            for (int i = 0; i < 3; i++) {
                mean[i] = (sumConstant[i] + energy * sumCoefficient[i]) / sumWeight;
                double second = (constantSquared[i] + 2 * energy * constantCoefficient[i]
                        + energy * energy * coefficientSquared[i]) / sumWeight;
                variance[i] = Math.max(0, second - mean[i] * mean[i]);
                error[i] = Math.sqrt(variance[i] / effective);
            }
            var statistics = new AssarafCaffarelForceStatistics(AssarafCaffarelSupport.vector(mean),
                    AssarafCaffarelSupport.vector(variance), AssarafCaffarelSupport.vector(error), effective,
                    accepted, rejected, evaluations, peakBatchSize, "hartree/bohr");
            return new Result(statistics, energy, nucleusIndex, "Qian-2022-Eq-6", "Qian-2022-Eqs-9-10");
        }
    }
}
