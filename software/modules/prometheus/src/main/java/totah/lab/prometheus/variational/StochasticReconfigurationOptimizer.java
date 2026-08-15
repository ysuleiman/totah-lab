package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Deterministic stochastic-reconfiguration optimizer for fixed-geometry H2 states. */
public final class StochasticReconfigurationOptimizer {
    private static final double MINIMUM_NORM = 1e-14;
    private final Configuration configuration;

    public StochasticReconfigurationOptimizer(Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public Result optimize(DifferentiableQuantumState initial,
            HydrogenMoleculeHamiltonian hamiltonian, CollocationPointSet points) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(hamiltonian, "hamiltonian");
        Objects.requireNonNull(points, "points");
        return optimize(initial,hamiltonian,new PointTraversal() {
            @Override public int count() { return points.points().size(); }
            @Override public void forEach(Consumer<CollocationPointSet.WeightedPoint> consumer) {
                points.points().forEach(consumer);
            }
        });
    }

    public Result optimize(DifferentiableQuantumState initial,
            HydrogenMoleculeHamiltonian hamiltonian, HydrogenMoleculeImportanceBatches batches) {
        Objects.requireNonNull(initial, "initial"); Objects.requireNonNull(hamiltonian, "hamiltonian");
        Objects.requireNonNull(batches, "batches");
        return optimize(initial,hamiltonian,new PointTraversal() {
            @Override public int count() { return batches.count(); }
            @Override public void forEach(Consumer<CollocationPointSet.WeightedPoint> consumer) {
                batches.forEachBatch(batch -> batch.forEach(consumer));
            }
        });
    }

    private Result optimize(DifferentiableQuantumState initial,
            HydrogenMoleculeHamiltonian hamiltonian, PointTraversal points) {
        DifferentiableQuantumState state = initial;
        List<Double> energies = new ArrayList<>(configuration.iterations());
        long evaluations = 0;
        int completedIterations=0, stale=0; double bestEnergy=Double.POSITIVE_INFINITY;
        for (int iteration = 0; iteration < configuration.iterations(); iteration++) {
            Statistics statistics = statistics(state, hamiltonian, points);
            evaluations += points.count();
            energies.add(statistics.energy());
            double[] direction = solve(statistics.covariance(), statistics.energyGradient(),
                    configuration.diagonalRegularization());
            List<Double> parameters = new ArrayList<>(state.parameters().values());
            for (int parameter = 0; parameter < parameters.size(); parameter++) {
                double update=-configuration.learningRate()*direction[parameter];
                update=Math.max(-configuration.maximumAbsoluteUpdate(),Math.min(configuration.maximumAbsoluteUpdate(),update));
                parameters.set(parameter,parameters.get(parameter)+update);
            }
            state = state.withParameters(new ParameterVector(parameters));
            completedIterations=iteration+1;
            if(bestEnergy-statistics.energy()>configuration.improvementTolerance()) {
                bestEnergy=statistics.energy(); stale=0;
            } else stale++;
            if(completedIterations>=configuration.minimumIterations()&&stale>=configuration.patience()) break;
        }
        Statistics finalStatistics = statistics(state, hamiltonian, points);
        evaluations += points.count();
        energies.add(finalStatistics.energy());
        return new Result(state.parameters(), finalStatistics.energy(), finalStatistics.variance(),
                completedIterations, evaluations, energies,completedIterations<configuration.iterations());
    }

    private static Statistics statistics(DifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian hamiltonian, PointTraversal points) {
        int parameterCount = state.parameters().values().size();
        double norm = 0.0, energyMoment = 0.0, energySquareMoment = 0.0;
        double[] observableMoment = new double[parameterCount];
        double[] observableEnergyMoment = new double[parameterCount];
        double[][] observableProductMoment = new double[parameterCount][parameterCount];
        MutableStatistics moments = new MutableStatistics(norm,energyMoment,energySquareMoment);
        points.forEach(point -> {
            DifferentiableStateEvaluation evaluation =
                    state.evaluateWithDerivatives(point.coordinates());
            if (evaluation.parameterGradient().derivatives().size() != parameterCount) {
                throw new IllegalArgumentException("parameter-gradient dimension does not match parameters");
            }
            double psi = evaluation.value().real();
            if (!Double.isFinite(psi) || Math.abs(psi) < MINIMUM_NORM) return;
            double probabilityWeight = point.weight() * psi * psi;
            double localEnergy = -0.5 * evaluation.coordinateLaplacian().value().real() / psi
                    + hamiltonian.potential(point.coordinates());
            if (!Double.isFinite(probabilityWeight) || !Double.isFinite(localEnergy)) {
                throw new IllegalArgumentException("non-finite H2 state evaluation");
            }
            double[] logarithmicDerivative = new double[parameterCount];
            for (int parameter = 0; parameter < parameterCount; parameter++) {
                logarithmicDerivative[parameter] = evaluation.parameterGradient().derivatives()
                        .get(parameter).real() / psi;
            }
            moments.norm += probabilityWeight;
            moments.energy += probabilityWeight * localEnergy;
            moments.energySquare += probabilityWeight * localEnergy * localEnergy;
            for (int row = 0; row < parameterCount; row++) {
                observableMoment[row] += probabilityWeight * logarithmicDerivative[row];
                observableEnergyMoment[row] += probabilityWeight * logarithmicDerivative[row] * localEnergy;
                for (int column = 0; column < parameterCount; column++) {
                    observableProductMoment[row][column] += probabilityWeight
                            * logarithmicDerivative[row] * logarithmicDerivative[column];
                }
            }
        });
        norm=moments.norm; energyMoment=moments.energy; energySquareMoment=moments.energySquare;
        if (!Double.isFinite(norm) || norm < MINIMUM_NORM) {
            throw new IllegalArgumentException("H2 state has zero or non-finite sampled norm");
        }
        double energy = energyMoment / norm;
        double[] means = new double[parameterCount];
        double[] gradient = new double[parameterCount];
        double[][] covariance = new double[parameterCount][parameterCount];
        for (int row = 0; row < parameterCount; row++) {
            means[row] = observableMoment[row] / norm;
            gradient[row] = 2.0 * (observableEnergyMoment[row] / norm - means[row] * energy);
        }
        for (int row = 0; row < parameterCount; row++) {
            for (int column = 0; column < parameterCount; column++) {
                covariance[row][column] = observableProductMoment[row][column] / norm
                        - means[row] * means[column];
            }
        }
        double variance = Math.max(0.0, energySquareMoment / norm - energy * energy);
        return new Statistics(energy, variance, gradient, covariance);
    }

    private static double[] solve(double[][] covariance, double[] gradient, double regularization) {
        int size = gradient.length;
        double[][] augmented = new double[size][size + 1];
        for (int row = 0; row < size; row++) {
            System.arraycopy(covariance[row], 0, augmented[row], 0, size);
            augmented[row][row] += regularization;
            augmented[row][size] = gradient[row];
        }
        for (int pivot = 0; pivot < size; pivot++) {
            int selected = pivot;
            for (int row = pivot + 1; row < size; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[selected][pivot])) selected = row;
            }
            double[] swap = augmented[pivot]; augmented[pivot] = augmented[selected]; augmented[selected] = swap;
            if (Math.abs(augmented[pivot][pivot]) < 1e-15) {
                throw new IllegalArgumentException("regularized covariance matrix is singular");
            }
            for (int row = pivot + 1; row < size; row++) {
                double factor = augmented[row][pivot] / augmented[pivot][pivot];
                for (int column = pivot; column <= size; column++) {
                    augmented[row][column] -= factor * augmented[pivot][column];
                }
            }
        }
        double[] solution = new double[size];
        for (int row = size - 1; row >= 0; row--) {
            double remainder = augmented[row][size];
            for (int column = row + 1; column < size; column++) remainder -= augmented[row][column] * solution[column];
            solution[row] = remainder / augmented[row][row];
        }
        return solution;
    }

    public record Configuration(int iterations, double learningRate, double diagonalRegularization,
            int minimumIterations,int patience,double improvementTolerance,double maximumAbsoluteUpdate) {
        public Configuration(int iterations,double learningRate,double diagonalRegularization) {
            this(iterations,learningRate,diagonalRegularization,iterations,1,0.0,Double.MAX_VALUE);
        }
        public Configuration {
            if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
            if (!Double.isFinite(learningRate) || learningRate <= 0.0) {
                throw new IllegalArgumentException("learningRate must be finite and positive");
            }
            if (!Double.isFinite(diagonalRegularization) || diagonalRegularization <= 0.0) {
                throw new IllegalArgumentException("diagonalRegularization must be finite and positive");
            }
            if(minimumIterations<1||minimumIterations>iterations) throw new IllegalArgumentException("invalid minimumIterations");
            if(patience<1) throw new IllegalArgumentException("patience must be positive");
            if(!Double.isFinite(improvementTolerance)||improvementTolerance<0) throw new IllegalArgumentException("invalid improvementTolerance");
            if(!Double.isFinite(maximumAbsoluteUpdate)||maximumAbsoluteUpdate<=0) throw new IllegalArgumentException("invalid maximumAbsoluteUpdate");
        }
    }

    public record Result(ParameterVector parameters, double energy, double localEnergyVariance,
            int iterations, long stateEvaluations, List<Double> energyHistory,boolean converged) {
        public Result {
            Objects.requireNonNull(parameters, "parameters");
            energyHistory = List.copyOf(Objects.requireNonNull(energyHistory, "energyHistory"));
        }
    }

    private record Statistics(double energy, double variance, double[] energyGradient,
            double[][] covariance) { }
    private static final class MutableStatistics {
        private double norm,energy,energySquare;
        private MutableStatistics(double norm,double energy,double energySquare) {
            this.norm=norm;this.energy=energy;this.energySquare=energySquare;
        }
    }
    private interface PointTraversal {
        int count();
        void forEach(Consumer<CollocationPointSet.WeightedPoint> consumer);
    }
}
