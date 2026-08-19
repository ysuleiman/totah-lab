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

    static AssarafCaffarelForceStatistics.Vector vector(double[] value) {
        return new AssarafCaffarelForceStatistics.Vector(value[0], value[1], value[2]);
    }
}
