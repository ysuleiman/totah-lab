package totah.lab.prometheus.variational.force;

import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.QuantumCoordinates;

final class AssarafCaffarelSupport {
    static final double MINIMUM_AMPLITUDE = 1e-14;

    private AssarafCaffarelSupport() { }

    static SampleTerms terms(QuantumCoordinates coordinates, DifferentiableStateEvaluation state,
            double bondLengthBohr, int nucleusIndex) {
        if (coordinates.particles().size() != 2) {
            throw new IllegalArgumentException("the locked H2 estimator requires exactly two electrons");
        }
        double psi = state.value().real();
        if (!Double.isFinite(psi) || Math.abs(psi) < MINIMUM_AMPLITUDE) return null;
        double nucleusZ = nucleusIndex == 0 ? -bondLengthBohr / 2 : bondLengthBohr / 2;
        double otherZ = -nucleusZ;
        double separationZ = nucleusZ - otherZ;
        double separation3 = Math.pow(Math.abs(separationZ), 3);
        double[] nuclear = {0, 0, separationZ / separation3};
        double[] bare = nuclear.clone();
        double[] q = new double[3];
        double[] gradientContraction = new double[3];
        double[] laplacianQ = new double[3];
        for (int i = 0; i < 2; i++) {
            var electron = coordinates.particles().get(i);
            double[] displacement = {electron.xBohr(), electron.yBohr(), electron.zBohr() - nucleusZ};
            double radius2 = dot(displacement, displacement);
            if (!(radius2 > 0) || !Double.isFinite(radius2)) {
                throw new IllegalArgumentException("electron-nucleus coalescence is undefined for raw AC samples");
            }
            double radius = Math.sqrt(radius2), radius3 = radius2 * radius;
            double[] logGradient = {
                    state.coordinateGradient().particleGradients().get(i).x().real() / psi,
                    state.coordinateGradient().particleGradients().get(i).y().real() / psi,
                    state.coordinateGradient().particleGradients().get(i).z().real() / psi};
            for (int component = 0; component < 3; component++) {
                q[component] += displacement[component] / radius;
                bare[component] += displacement[component] / radius3;
                laplacianQ[component] += -2 * displacement[component] / radius3;
                for (int coordinate = 0; coordinate < 3; coordinate++) {
                    double derivative = (component == coordinate ? 1 / radius : 0)
                            - displacement[component] * displacement[coordinate] / radius3;
                    gradientContraction[component] += derivative * logGradient[coordinate];
                }
            }
        }
        double[] operatorRatio = new double[3];
        for (int component = 0; component < 3; component++) {
            // Product rule for (H-E_L)(Q psi)/psi; all potentials commute with Q.
            operatorRatio[component] = -gradientContraction[component] - 0.5 * laplacianQ[component];
        }
        return new SampleTerms(nuclear, bare, q, gradientContraction, operatorRatio);
    }

    static double localEnergy(QuantumCoordinates coordinates, DifferentiableStateEvaluation state,
            double potential) {
        return -0.5 * state.coordinateLaplacian().value().real() / state.value().real() + potential;
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    record SampleTerms(double[] nuclearForce, double[] bareForce, double[] q,
            double[] gradientContraction, double[] operatorRatio) { }

    static final class Moments {
        private double sumWeight, sumWeightSquared;
        private final double[] sum = new double[3], sumSquares = new double[3];
        private long accepted, rejected, evaluations;
        private int peakBatchSize;

        void add(double weight, double[] value) {
            if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("invalid sample weight");
            for (double component : value) if (!Double.isFinite(component))
                throw new IllegalArgumentException("non-finite AC force contribution");
            sumWeight += weight;
            sumWeightSquared += weight * weight;
            for (int i = 0; i < 3; i++) {
                sum[i] += weight * value[i];
                sumSquares[i] += weight * value[i] * value[i];
            }
            accepted++;
        }

        void evaluated() { evaluations++; }
        void rejected() { rejected++; }
        void observeBatch(int size) { peakBatchSize = Math.max(peakBatchSize, size); }

        AssarafCaffarelForceStatistics finish() {
            if (!(sumWeight > 0) || !(sumWeightSquared > 0))
                throw new IllegalArgumentException("zero sampled norm");
            double effective = sumWeight * sumWeight / sumWeightSquared;
            double[] mean = new double[3], variance = new double[3], error = new double[3];
            for (int i = 0; i < 3; i++) {
                mean[i] = sum[i] / sumWeight;
                variance[i] = Math.max(0, sumSquares[i] / sumWeight - mean[i] * mean[i]);
                error[i] = Math.sqrt(variance[i] / effective);
            }
            return new AssarafCaffarelForceStatistics(vector(mean), vector(variance), vector(error),
                    effective, accepted, rejected, evaluations, peakBatchSize, "hartree/bohr");
        }
    }

    static AssarafCaffarelForceStatistics.Vector vector(double[] value) {
        return new AssarafCaffarelForceStatistics.Vector(value[0], value[1], value[2]);
    }
}
