package totah.lab.prometheus.neural.ferminet.reference;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetRuntimeSampling;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Streaming frozen-parameter, common-configuration central-FD force reference. */
public final class FermiNetCorrelatedFiniteDifferenceForceReference {

    /** Locked by NUCLEAR_FORCE_ESTIMATOR_CAPABILITY_PROTOCOL_LOCKED.md. */
    public static final double STEP_BOHR = 1.0e-3;

    public Result evaluate(
            FermiNetV1State center,
            Path configurationFile,
            int walkerCount) throws IOException {
        Objects.requireNonNull(center, "center");
        var dataset = FermiNetCorrelatedFdConfigurationFile.inspect(
                configurationFile, walkerCount);
        String parameterChecksum =
                FermiNetPretrainingQualification.parameterChecksum(center);
        int componentCount = 3 * center.molecule().nuclei().size();
        List<ComponentResult> results = new ArrayList<>(componentCount);
        for (int component = 0; component < componentCount; component++) {
            int nucleus = component / 3;
            int axis = component % 3;
            FermiNetV1State plus = FermiNetStateAccess.withGeometry(
                    center, displace(center.molecule(), nucleus, axis, STEP_BOHR));
            FermiNetV1State minus = FermiNetStateAccess.withGeometry(
                    center, displace(center.molecule(), nucleus, axis, -STEP_BOHR));
            verifyParameters(parameterChecksum, plus, minus);
            verifyDisplacement(center.molecule(), plus.molecule(), nucleus, axis, STEP_BOHR);
            verifyDisplacement(center.molecule(), minus.molecule(), nucleus, axis, -STEP_BOHR);
            ComponentWork work = new ComponentWork(
                    nucleus, axis, plus, minus, dataset.sampleCount(), walkerCount);

            // Pass one: log-sum-exp normalization for this component.
            FermiNetCorrelatedFdConfigurationFile.forEach(
                    configurationFile, walkerCount, (sample, chain, retained, coordinates) -> {
                        double centerLog = FermiNetStateAccess.sampling(center, coordinates)
                                .logAbsoluteWavefunction();
                        double plusLog = FermiNetStateAccess.sampling(
                                work.plus, coordinates).logAbsoluteWavefunction();
                        double minusLog = FermiNetStateAccess.sampling(
                                work.minus, coordinates).logAbsoluteWavefunction();
                        work.plusWeights.add(2.0 * (plusLog - centerLog));
                        work.minusWeights.add(2.0 * (minusLog - centerLog));
                    });

            // Pass two: one spatial +/- pair survives at a time.
            FermiNetCorrelatedFdConfigurationFile.forEach(
                    configurationFile, walkerCount, (sample, chain, retained, coordinates) -> {
                        double centerLog = FermiNetStateAccess.sampling(center, coordinates)
                                .logAbsoluteWavefunction();
                        var plusValue = FermiNetRuntimeSampling.localEnergyWithLog(
                                work.plus, coordinates);
                        var minusValue = FermiNetRuntimeSampling.localEnergyWithLog(
                                work.minus, coordinates);
                        double plusWeight = work.plusWeights.normalizedToMean(
                                2.0 * (plusValue.logAbsoluteWavefunction() - centerLog));
                        double minusWeight = work.minusWeights.normalizedToMean(
                                2.0 * (minusValue.logAbsoluteWavefunction() - centerLog));
                        double plusContribution = plusWeight
                                * plusValue.localEnergy().totalHartree();
                        double minusContribution = minusWeight
                                * minusValue.localEnergy().totalHartree();
                        double force = -(plusContribution - minusContribution)
                                / (2.0 * STEP_BOHR);
                        work.add(sample, chain, plusContribution, minusContribution, force);
                        System.gc();
                    });
            results.add(work.finish(parameterChecksum));
            System.out.printf("FERMINET_CORRELATED_FD_COMPONENT=%d/%d%n",
                    component + 1, componentCount);
            System.gc();
        }
        return new Result(
                STEP_BOHR, parameterChecksum,
                FermiNetPretrainingQualification.geometryIdentity(center.molecule()),
                dataset, List.copyOf(results));
    }

    private static void verifyParameters(
            String expected, FermiNetV1State plus, FermiNetV1State minus) {
        if (!expected.equals(FermiNetPretrainingQualification.parameterChecksum(plus))
                || !expected.equals(FermiNetPretrainingQualification.parameterChecksum(minus))) {
            throw new IllegalStateException("displaced FermiNet parameters changed");
        }
    }

    private static void verifyDisplacement(
            Molecule center, Molecule displaced, int nucleus, int axis, double delta) {
        if (center.nuclei().size() != displaced.nuclei().size()) {
            throw new IllegalStateException("displaced geometry changed nuclear count");
        }
        for (int atom = 0; atom < center.nuclei().size(); atom++) {
            var before = center.nuclei().get(atom).position().inBohr();
            var after = displaced.nuclei().get(atom).position().inBohr();
            double[] left = {before.x(), before.y(), before.z()};
            double[] right = {after.x(), after.y(), after.z()};
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                double expected = left[coordinate]
                        + (atom == nucleus && coordinate == axis ? delta : 0.0);
                if (Double.doubleToRawLongBits(expected)
                        != Double.doubleToRawLongBits(right[coordinate])) {
                    throw new IllegalStateException("geometry changed outside selected displacement");
                }
            }
        }
    }

    private static Molecule displace(
            Molecule molecule, int nucleus, int axis, double displacement) {
        List<NuclearCenter> nuclei = new ArrayList<>(molecule.nuclei());
        NuclearCenter old = nuclei.get(nucleus);
        CartesianPosition position = old.position().inBohr();
        double[] xyz = {position.x(), position.y(), position.z()};
        xyz[axis] += displacement;
        nuclei.set(nucleus, new NuclearCenter(
                old.orderedIndex(), old.element(), old.charge(),
                new CartesianPosition(xyz[0], xyz[1], xyz[2], LengthUnit.BOHR)));
        return new Molecule(molecule.moleculeId(), nuclei, molecule.charge(),
                molecule.electrons(), molecule.spin());
    }

    private static double sampleVariance(double sum, double sumSquares, int count) {
        double value = (sumSquares - sum * sum / count) / (count - 1);
        return Math.max(0.0, value);
    }

    private static TailDiagnostics tails(double[] values, double mean, double sd) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        long beyondFive = 0, beyondTen = 0;
        for (double value : values) {
            double distance = Math.abs(value - mean);
            if (distance > 5.0 * sd) beyondFive++;
            if (distance > 10.0 * sd) beyondTen++;
        }
        return new TailDiagnostics(sorted[0], quantile(sorted, 0.001),
                quantile(sorted, 0.01), quantile(sorted, 0.5),
                quantile(sorted, 0.99), quantile(sorted, 0.999),
                sorted[sorted.length - 1], beyondFive, beyondTen);
    }

    private static double quantile(double[] sorted, double probability) {
        double position = (sorted.length - 1) * probability;
        int lower = (int) Math.floor(position), upper = (int) Math.ceil(position);
        if (lower == upper) return sorted[lower];
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
    }

    private static String axisName(int axis) {
        return switch (axis) {
            case 0 -> "x"; case 1 -> "y"; case 2 -> "z";
            default -> throw new IllegalArgumentException("invalid Cartesian axis");
        };
    }

    public record Result(
            double stepBohr,
            String parameterChecksum,
            String centerGeometryChecksum,
            FermiNetCorrelatedFdConfigurationFile.Identity dataset,
            List<ComponentResult> components) {
        public Result { components = List.copyOf(components); }
    }

    public record ComponentResult(
            int nucleus,
            int axis,
            String axisName,
            double energyPlusHartree,
            double energyMinusHartree,
            double energyContributionCovariance,
            double forceHartreePerBohr,
            double forceStandardError,
            double naiveIndependentSampleStandardError,
            double forceVariance,
            double plusEffectiveSampleSize,
            double minusEffectiveSampleSize,
            double pairedEffectiveSampleSize,
            String parameterChecksum,
            String plusGeometryChecksum,
            String minusGeometryChecksum,
            TailDiagnostics tails,
            double[] rawForceSamples) {
        public ComponentResult { rawForceSamples = rawForceSamples.clone(); }
        @Override public double[] rawForceSamples() { return rawForceSamples.clone(); }
    }

    public record TailDiagnostics(
            double minimum, double percentilePointOne, double percentileOne,
            double median, double percentileNinetyNine,
            double percentileNinetyNinePointNine, double maximum,
            long beyondFiveSigma, long beyondTenSigma) {}

    private static final class ComponentWork {
        private final int nucleus, axis, count, walkers;
        private final FermiNetV1State plus, minus;
        private final LogWeights plusWeights = new LogWeights();
        private final LogWeights minusWeights = new LogWeights();
        private final double[] forces;
        private final double[] chainSums;
        private double sumPlus, sumMinus, sumProduct, sumForce, sumForceSquared;

        private ComponentWork(int nucleus, int axis, FermiNetV1State plus,
                              FermiNetV1State minus, int count, int walkers) {
            this.nucleus = nucleus; this.axis = axis; this.plus = plus; this.minus = minus;
            this.count = count; this.walkers = walkers;
            this.forces = new double[count]; this.chainSums = new double[walkers];
        }

        private void add(int sample, int chain, double plusValue,
                         double minusValue, double force) {
            sumPlus += plusValue; sumMinus += minusValue;
            sumProduct += plusValue * minusValue;
            sumForce += force; sumForceSquared += force * force;
            forces[sample] = force; chainSums[chain] += force;
        }

        private ComponentResult finish(String parameters) {
            double energyPlus = sumPlus / count, energyMinus = sumMinus / count;
            double force = -(energyPlus - energyMinus) / (2.0 * STEP_BOHR);
            double variance = sampleVariance(sumForce, sumForceSquared, count);
            int retained = count / walkers;
            double chainSquares = 0.0;
            for (double chainSum : chainSums) {
                double difference = chainSum / retained - force;
                chainSquares += difference * difference;
            }
            double chainSe = Math.sqrt(chainSquares / (walkers - 1) / walkers);
            double covariance = (sumProduct - count * energyPlus * energyMinus)
                    / (count - 1);
            return new ComponentResult(
                    nucleus, axis, axisName(axis), energyPlus, energyMinus, covariance,
                    force, chainSe, Math.sqrt(variance / count), variance,
                    plusWeights.ess(), minusWeights.ess(),
                    Math.min(plusWeights.ess(), minusWeights.ess()), parameters,
                    FermiNetPretrainingQualification.geometryIdentity(plus.molecule()),
                    FermiNetPretrainingQualification.geometryIdentity(minus.molecule()),
                    tails(forces, force, Math.sqrt(variance)), forces);
        }
    }

    private static final class LogWeights {
        private double maximum = Double.NEGATIVE_INFINITY;
        private double scaledSum;
        private double scaledSquareSum;
        private int count;

        private void add(double logWeight) {
            if (!Double.isFinite(logWeight)) {
                throw new IllegalStateException("non-finite correlated-FD log weight");
            }
            if (logWeight > maximum) {
                double scale = count == 0 ? 0.0 : Math.exp(maximum - logWeight);
                scaledSum *= scale;
                scaledSquareSum *= scale * scale;
                maximum = logWeight;
            }
            double value = Math.exp(logWeight - maximum);
            scaledSum += value;
            scaledSquareSum += value * value;
            count++;
        }

        private double normalizedToMean(double logWeight) {
            return Math.exp(logWeight - maximum) * count / scaledSum;
        }

        private double ess() { return scaledSum * scaledSum / scaledSquareSum; }
    }
}
