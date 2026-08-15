package totah.lab.prometheus.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Exact derivative of the locked finite deterministic H2 quadrature objective. */
final class ExactFiniteObjectiveDifferentiator {
    static final double ENERGY_SCALE_HARTREE = .015;
    static final double FORCE_SCALE_HARTREE_PER_BOHR = .030;
    static final double FORCE_RADIUS_BOHR = 1.0;
    static final double DIAGNOSTIC_DELTA_BOHR = .05;
    static final double FORCE_REFERENCE = .3621964426997232;
    private static final double[] RADIAL_SCALES = {.35, .70, 1.10};
    private static final double[] R12_SCALES = {.75, -.45, .25};
    private static final double[] BIASES = {-.35, .10, .50};
    private static final double MINIMUM_NORM = 1e-14;
    private final double[] radii;
    private final double[] references;
    private final int sampleCount;

    ExactFiniteObjectiveDifferentiator(double[] radii, double[] references, int sampleCount) {
        this.radii = radii.clone(); this.references = references.clone(); this.sampleCount = sampleCount;
        if (radii.length == 0 || radii.length != references.length || sampleCount < 1) throw new IllegalArgumentException("invalid objective data");
    }

    Evaluation evaluate(ParameterVector parameters) {
        Objects.requireNonNull(parameters); int p = parameters.values().size();
        double loss = 0, energyLoss = 0, meanEnergy = 0; double[] gradient = new double[p], energyGradient = new double[p];
        double[][] covariance = new double[p][p]; long stateEvaluations = 0, localEnergyEvaluations = 0, samples = 0;
        for (int index = 0; index < radii.length; index++) {
            Single value = single(parameters, radii[index], batches(radii[index]));
            stateEvaluations += value.stateEvaluations; localEnergyEvaluations += value.localEnergyEvaluations; samples += value.samples;
            meanEnergy += value.energy / radii.length;
            double residual = value.energy - references[index];
            energyLoss += residual * residual / (ENERGY_SCALE_HARTREE * ENERGY_SCALE_HARTREE * radii.length);
            for (int i = 0; i < p; i++) {
                energyGradient[i] += value.gradient[i] / radii.length;
                gradient[i] += 2 * residual * value.gradient[i] / (ENERGY_SCALE_HARTREE * ENERGY_SCALE_HARTREE * radii.length);
                for (int j = 0; j < p; j++) covariance[i][j] += value.covariance[i][j] / radii.length;
            }
        }
        HydrogenMoleculeImportanceBatches common = batches(FORCE_RADIUS_BOHR);
        Single minus = single(parameters, FORCE_RADIUS_BOHR - DIAGNOSTIC_DELTA_BOHR, common);
        Single plus = single(parameters, FORCE_RADIUS_BOHR + DIAGNOSTIC_DELTA_BOHR, common);
        stateEvaluations += minus.stateEvaluations + plus.stateEvaluations;
        localEnergyEvaluations += minus.localEnergyEvaluations + plus.localEnergyEvaluations;
        samples += minus.samples + plus.samples;
        double force = -(plus.energy - minus.energy) / (2 * DIAGNOSTIC_DELTA_BOHR);
        double forceResidual = force - FORCE_REFERENCE;
        double forceLoss = forceResidual * forceResidual / (FORCE_SCALE_HARTREE_PER_BOHR * FORCE_SCALE_HARTREE_PER_BOHR);
        for (int i = 0; i < p; i++) {
            double forceGradient = -(plus.gradient[i] - minus.gradient[i]) / (2 * DIAGNOSTIC_DELTA_BOHR);
            gradient[i] += 2 * forceResidual * forceGradient / (FORCE_SCALE_HARTREE_PER_BOHR * FORCE_SCALE_HARTREE_PER_BOHR);
        }
        loss = energyLoss + forceLoss;
        return new Evaluation(loss, energyLoss, forceLoss, force, meanEnergy, gradient, energyGradient, covariance,
                stateEvaluations, localEnergyEvaluations, samples);
    }

    StateValue state(ParameterVector parameters, double radius, QuantumCoordinates coordinates) {
        int p = parameters.values().size(); int d = 6;
        var one = coordinates.particles().get(0); var two = coordinates.particles().get(1);
        double[] values = {one.xBohr(), one.yBohr(), one.zBohr(), two.xBohr(), two.yBohr(), two.zBohr()};
        MixedParameterSpatialJet[] xyz = new MixedParameterSpatialJet[d];
        for (int i = 0; i < d; i++) xyz[i] = MixedParameterSpatialJet.coordinate(values[i], d, p, i);
        MixedParameterSpatialJet half = MixedParameterSpatialJet.constant(radius * .5, d, p);
        MixedParameterSpatialJet r1a = distance(xyz[0], xyz[1], xyz[2].add(half));
        MixedParameterSpatialJet r1b = distance(xyz[0], xyz[1], xyz[2].subtract(half));
        MixedParameterSpatialJet r2a = distance(xyz[3], xyz[4], xyz[5].add(half));
        MixedParameterSpatialJet r2b = distance(xyz[3], xyz[4], xyz[5].subtract(half));
        MixedParameterSpatialJet u = distance(xyz[0].subtract(xyz[3]), xyz[1].subtract(xyz[4]), xyz[2].subtract(xyz[5]));
        MixedParameterSpatialJet[] features = geometryFeatures(radius, d, p);
        MixedParameterSpatialJet[] local = new MixedParameterSpatialJet[5];
        for (int output = 0; output < 5; output++) {
            local[output] = MixedParameterSpatialJet.constant(0, d, p);
            for (int feature = 0; feature < 4; feature++) {
                var coefficient = MixedParameterSpatialJet.parameter(parameters.values().get(output * 4 + feature), d, p, output * 4 + feature);
                local[output] = local[output].add(features[feature].multiply(coefficient));
            }
        }
        MixedParameterSpatialJet l1a = localized(r1a, r1b, local[0]), l1b = localized(r1b, r1a, local[0]);
        MixedParameterSpatialJet l2a = localized(r2a, r2b, local[0]), l2b = localized(r2b, r2a, local[0]);
        MixedParameterSpatialJet covalent = l1a.multiply(l2b).add(l1b.multiply(l2a));
        MixedParameterSpatialJet radial = tail(r1a).add(tail(r1b)).add(tail(r2a)).add(tail(r2b));
        MixedParameterSpatialJet neural = local[1];
        for (int i = 0; i < 3; i++) {
            MixedParameterSpatialJet hidden = radial.multiply(RADIAL_SCALES[i]).add(u.multiply(R12_SCALES[i])).add(BIASES[i]).tanh();
            neural = neural.add(hidden.multiply(local[i + 2]));
        }
        MixedParameterSpatialJet psi = covalent.multiply(MixedParameterSpatialJet.constant(1, d, p).add(u.multiply(.5)).add(u.multiply(u).multiply(neural)));
        double[] parameterDerivative = new double[p], laplacianParameterDerivative = new double[p];
        for (int i = 0; i < p; i++) {
            parameterDerivative[i] = psi.parameterDerivative(i);
            laplacianParameterDerivative[i] = psi.laplacianParameterDerivative(i, d);
        }
        return new StateValue(psi.value(), psi.laplacian(d), parameterDerivative, laplacianParameterDerivative);
    }

    private Single single(ParameterVector parameters, double radius, HydrogenMoleculeImportanceBatches batches) {
        int p = parameters.values().size(); Mutable m = new Mutable(p); HydrogenMoleculeHamiltonian h = new HydrogenMoleculeHamiltonian(radius);
        batches.forEachBatch(batch -> batch.forEach(point -> {
            StateValue state = state(parameters, radius, point.coordinates()); m.stateEvaluations++; m.localEnergyEvaluations++; m.samples++;
            double psi = state.value; if (!Double.isFinite(psi) || Math.abs(psi) < MINIMUM_NORM) return;
            double laplacian = state.laplacian; double local = -.5 * laplacian / psi + h.potential(point.coordinates());
            double q = point.weight() * psi * psi; m.norm += q; m.numerator += q * local;
            for (int i = 0; i < p; i++) {
                double dpsi = state.parameterDerivative[i];
                double dlocal = -.5 * (state.laplacianParameterDerivative[i] * psi - laplacian * dpsi) / (psi * psi);
                double dq = point.weight() * 2 * psi * dpsi;
                m.dNorm[i] += dq; m.dNumerator[i] += dq * local + q * dlocal;
                double observable = dpsi / psi; m.o[i] += q * observable;
                for (int j = 0; j < p; j++) m.oo[i][j] += q * observable * (state.parameterDerivative[j] / psi);
            }
        }));
        if (!Double.isFinite(m.norm) || m.norm < MINIMUM_NORM) throw new IllegalArgumentException("invalid exact finite objective norm");
        double energy = m.numerator / m.norm; double[] gradient = new double[p]; double[][] covariance = new double[p][p];
        for (int i = 0; i < p; i++) {
            gradient[i] = (m.dNumerator[i] * m.norm - m.numerator * m.dNorm[i]) / (m.norm * m.norm);
            double meanI = m.o[i] / m.norm;
            for (int j = 0; j < p; j++) covariance[i][j] = m.oo[i][j] / m.norm - meanI * (m.o[j] / m.norm);
        }
        return new Single(energy, gradient, covariance, m.stateEvaluations, m.localEnergyEvaluations, m.samples);
    }

    private HydrogenMoleculeImportanceBatches batches(double radius) { return new HydrogenMoleculeImportanceBatches(sampleCount, radius, 1.15, 43, 512); }
    private static MixedParameterSpatialJet[] geometryFeatures(double radius, int d, int p) {
        var x = MixedParameterSpatialJet.constant((radius - .8) * 2 / (6.0 - .8) - 1, d, p);
        return new MixedParameterSpatialJet[] {MixedParameterSpatialJet.constant(1, d, p), x,
                x.multiply(x).multiply(2).add(-1), x.multiply(x).multiply(x).multiply(4).add(x.multiply(-3))};
    }
    private static MixedParameterSpatialJet localized(MixedParameterSpatialJet own, MixedParameterSpatialJet other, MixedParameterSpatialJet response) { return own.add(other).multiply(-1).add(tail(other).multiply(response)).exp(); }
    private static MixedParameterSpatialJet tail(MixedParameterSpatialJet value) { return value.multiply(value).divide(value.add(1)); }
    private static MixedParameterSpatialJet distance(MixedParameterSpatialJet x, MixedParameterSpatialJet y, MixedParameterSpatialJet z) { return x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt(); }

    record StateValue(double value, double laplacian, double[] parameterDerivative, double[] laplacianParameterDerivative) { }
    record Evaluation(double loss, double energyLoss, double forceLoss, double force, double meanEnergy, double[] gradient,
            double[] energyGradient, double[][] covariance, long stateEvaluations, long localEnergyEvaluations, long sampleCount) { }
    private record Single(double energy, double[] gradient, double[][] covariance, long stateEvaluations, long localEnergyEvaluations, long samples) { }
    private static final class Mutable {
        double norm, numerator; long stateEvaluations, localEnergyEvaluations, samples;
        final double[] dNorm, dNumerator, o; final double[][] oo;
        Mutable(int p) { dNorm = new double[p]; dNumerator = new double[p]; o = new double[p]; oo = new double[p][p]; }
    }
}
