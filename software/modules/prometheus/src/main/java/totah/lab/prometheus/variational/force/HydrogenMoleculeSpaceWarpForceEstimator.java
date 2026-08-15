package totah.lab.prometheus.variational.force;

import java.util.Objects;
import java.util.function.Consumer;

import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.GeometryDifferentiableQuantumState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Qian et al. Eqs. 12--15 SWCT estimator for the centered H2 bond coordinate. */
public final class HydrogenMoleculeSpaceWarpForceEstimator {
    /** Locked numerical derivative used because the state API lacks d(laplacian)/dR. */
    public static final double DIFFERENCE_STEP_BOHR = 1e-3;

    public Result evaluate(GeometryDifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian hamiltonian, HydrogenMoleculeImportanceBatches batches) {
        return evaluate(state,hamiltonian,batches,contribution->{ });
    }

    public Result evaluate(GeometryDifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian hamiltonian, HydrogenMoleculeImportanceBatches batches,
            Consumer<LinearContribution> contributionConsumer) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(hamiltonian, "hamiltonian");
        Objects.requireNonNull(batches, "batches");
        Objects.requireNonNull(contributionConsumer,"contributionConsumer");
        double radius = hamiltonian.bondLengthBohr();
        if (Double.doubleToLongBits(state.geometryCoordinateBohr()) != Double.doubleToLongBits(radius)) {
            throw new IllegalArgumentException("state and Hamiltonian bond lengths must match exactly");
        }
        var plusState = state.atGeometry(radius + DIFFERENCE_STEP_BOHR);
        var minusState = state.atGeometry(radius - DIFFERENCE_STEP_BOHR);
        var plusHamiltonian = new HydrogenMoleculeHamiltonian(radius + DIFFERENCE_STEP_BOHR);
        var minusHamiltonian = new HydrogenMoleculeHamiltonian(radius - DIFFERENCE_STEP_BOHR);
        Accumulator a = new Accumulator();
        batches.forEachBatch(batch -> {
            a.peakBatchSize = Math.max(a.peakBatchSize, batch.size());
            batch.forEach(point -> accumulate(point.coordinates(), point.weight(), state, hamiltonian,
                    plusState, minusState, plusHamiltonian, minusHamiltonian, radius, a,contributionConsumer));
        });
        if (!(a.norm > 1e-14) || !Double.isFinite(a.norm)) {
            throw new IllegalArgumentException("zero sampled norm");
        }
        double energy = a.energy / a.norm;
        double meanBase = a.base / a.norm;
        double meanLogDerivative = a.logDerivative / a.norm;
        double force = meanBase + 2 * energy * meanLogDerivative;
        double bareForce = a.bareForce / a.norm;
        double warpForce = a.totalHfmForce / a.norm - bareForce;
        double pulayForce = force - a.totalHfmForce / a.norm;
        double secondMoment = (a.baseSquare + 4 * energy * a.baseLogDerivative
                + 4 * energy * energy * a.logDerivativeSquare) / a.norm;
        double variance = Math.max(0, secondMoment - force * force);
        return new Result(force, bareForce, warpForce, pulayForce, energy, variance,
                a.configurations, a.stateEvaluations, a.localEnergyEvaluations,
                a.peakBatchSize, DIFFERENCE_STEP_BOHR, "CENTRAL_NUMERICAL_EXACT_SWCT_EXPRESSION",
                "hartree/bohr");
    }

    private static void accumulate(QuantumCoordinates coordinates, double quadratureWeight,
            GeometryDifferentiableQuantumState state, HydrogenMoleculeHamiltonian hamiltonian,
            GeometryDifferentiableQuantumState plusState, GeometryDifferentiableQuantumState minusState,
            HydrogenMoleculeHamiltonian plusHamiltonian, HydrogenMoleculeHamiltonian minusHamiltonian,
            double radius, Accumulator a,Consumer<LinearContribution> contributionConsumer) {
        DifferentiableStateEvaluation center = state.evaluateWithDerivatives(coordinates);
        a.stateEvaluations++;
        double psi = center.value().real();
        if (!Double.isFinite(psi) || Math.abs(psi) < 1e-14) return;
        double weight = quadratureWeight * psi * psi;
        double localEnergy = localEnergy(center, psi, hamiltonian, coordinates);
        a.localEnergyEvaluations++;

        var plusWarp = HydrogenMoleculeSpaceWarp.transform(coordinates, radius, DIFFERENCE_STEP_BOHR);
        var minusWarp = HydrogenMoleculeSpaceWarp.transform(coordinates, radius, -DIFFERENCE_STEP_BOHR);
        DifferentiableStateEvaluation plus = plusState.evaluateWithDerivatives(plusWarp.coordinates());
        DifferentiableStateEvaluation minus = minusState.evaluateWithDerivatives(minusWarp.coordinates());
        a.stateEvaluations += 2;
        double plusPsi = plus.value().real(), minusPsi = minus.value().real();
        requireNonzeroFinite(plusPsi, minusPsi);
        double plusEnergy = localEnergy(plus, plusPsi, plusHamiltonian, plusWarp.coordinates());
        double minusEnergy = localEnergy(minus, minusPsi, minusHamiltonian, minusWarp.coordinates());
        a.localEnergyEvaluations += 2;
        double totalEnergyDerivative = central(plusEnergy, minusEnergy);
        double logDerivative = central(Math.log(Math.abs(plusPsi)) + 0.5 * Math.log(plusWarp.jacobian()),
                Math.log(Math.abs(minusPsi)) + 0.5 * Math.log(minusWarp.jacobian()));

        // Eq. 15 fixture/decomposition. These two evaluations are genuinely distinct:
        // geometry changes while electron coordinates remain fixed.
        DifferentiableStateEvaluation plusFixed = plusState.evaluateWithDerivatives(coordinates);
        DifferentiableStateEvaluation minusFixed = minusState.evaluateWithDerivatives(coordinates);
        a.stateEvaluations += 2;
        double plusFixedPsi = plusFixed.value().real(), minusFixedPsi = minusFixed.value().real();
        requireNonzeroFinite(plusFixedPsi, minusFixedPsi);
        double bareDerivative = central(localEnergy(plusFixed, plusFixedPsi, plusHamiltonian, coordinates),
                localEnergy(minusFixed, minusFixedPsi, minusHamiltonian, coordinates));
        a.localEnergyEvaluations += 2;

        double hfmForce = -totalEnergyDerivative;
        double base = hfmForce - 2 * localEnergy * logDerivative;
        if (!Double.isFinite(weight) || !Double.isFinite(localEnergy) || !Double.isFinite(base)
                || !Double.isFinite(logDerivative) || !Double.isFinite(bareDerivative)) {
            throw new IllegalArgumentException("non-finite SWCT contribution");
        }
        a.norm += weight;
        a.energy += weight * localEnergy;
        a.base += weight * base;
        a.logDerivative += weight * logDerivative;
        a.totalHfmForce += weight * hfmForce;
        a.bareForce += weight * -bareDerivative;
        a.baseSquare += weight * base * base;
        a.baseLogDerivative += weight * base * logDerivative;
        a.logDerivativeSquare += weight * logDerivative * logDerivative;
        a.configurations++;
        contributionConsumer.accept(new LinearContribution(weight,base,logDerivative));
    }

    private static double localEnergy(DifferentiableStateEvaluation evaluation, double psi,
            HydrogenMoleculeHamiltonian hamiltonian, QuantumCoordinates coordinates) {
        return -0.5 * evaluation.coordinateLaplacian().value().real() / psi
                + hamiltonian.potential(coordinates);
    }

    private static double central(double plus, double minus) {
        return (plus - minus) / (2 * DIFFERENCE_STEP_BOHR);
    }

    private static void requireNonzeroFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value) || Math.abs(value) < 1e-14) {
                throw new IllegalArgumentException("non-finite or nodal transformed state");
            }
        }
    }

    public record Result(double forceHartreePerBohr, double bareHfmForceHartreePerBohr,
            double warpHfmForceHartreePerBohr, double pulayForceHartreePerBohr,
            double energyHartree, double forceEstimatorVarianceHartree2PerBohr2,
            long configurations, long stateEvaluations, long localEnergyEvaluations,
            int peakBatchSize, double numericalDerivativeStepBohr,
            String derivativeImplementation, String forceUnits) {
        public Result {
            Objects.requireNonNull(derivativeImplementation, "derivativeImplementation");
            Objects.requireNonNull(forceUnits, "forceUnits");
        }
    }
    public record LinearContribution(double importanceWeight,double baseForceHartreePerBohr,
            double sampledMeanEnergyCoefficientPerBohr){ }

    private static final class Accumulator {
        private double norm, energy, base, logDerivative, totalHfmForce, bareForce;
        private double baseSquare, baseLogDerivative, logDerivativeSquare;
        private long configurations, stateEvaluations, localEnergyEvaluations;
        private int peakBatchSize;
    }
}
