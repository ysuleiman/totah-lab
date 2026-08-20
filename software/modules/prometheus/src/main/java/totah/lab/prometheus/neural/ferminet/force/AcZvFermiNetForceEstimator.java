package totah.lab.prometheus.neural.ferminet.force;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngine;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Assaraf-Caffarel zero-variance (AC-ZV) nuclear-force estimator for the
 * frozen FermiNet state, behind the canonical pipeline.
 *
 * <p>Implements the Eq.-6-consistent zero-variance part of Qian et al.,
 * J. Chem. Phys. 157, 164104 (2022):
 *
 * <pre>
 * F_A^ZV = F_A,nn + div Q_A . grad log|Psi|
 * Q_A    = Z_A sum_i (r_i - R_A) / |r_i - R_A|
 * </pre>
 *
 * where F_A,nn = Z_A sum_{B!=A} Z_B (R_A - R_B) / |R_A - R_B|^3 is the bare
 * nucleus-nucleus Hellmann-Feynman force and the contraction
 * div Q_A . grad log|Psi| = sum_i sum_alpha dQ_Ac/de_i,alpha * d log|Psi|/de_i,alpha
 * is the zero-variance term. The electron-nucleus bare force cancels
 * analytically against -1/2 laplacian(Q_A) and is never evaluated.
 *
 * <p>Two historical defects of the retired H2 reference implementation
 * ({@code AssarafCaffarelZvForceEstimator}, removed from live source; see
 * {@code AC_ZV_HISTORICAL_IMPLEMENTATION_RETIRED.md} and git history) are
 * deliberately not reproduced here:
 *
 * <ul>
 *   <li>the printed Eq. 11 final expression carries a minus sign before the
 *       contraction that is inconsistent with the paper's own Eq. 6 (and with
 *       the validated ZVZB implementation); a frozen-panel probe shows errors
 *       of +1.36/+1.05/+0.34 hartree/bohr for the printed sign versus
 *       -0.085/-0.050/-0.0003 for the Eq.-6-consistent sign implemented here;
 *   <li>the historical auxiliary omits the nuclear-charge factor Z_A
 *       (invisible for hydrogen); this implementation includes it.
 * </ul>
 *
 * <p>Only validated shared runtime quantities are used: the electron
 * log-gradient from {@link FermiNetStateAccess#spatial}, nuclear positions
 * and charges. No local energy, Laplacian, directional derivative, or
 * parameter derivative is required, and there is no finite-difference
 * fallback.
 *
 * <p>The classification is a descriptive evidence level, not an acceptance
 * decision: no numerical pass/fail thresholds are applied. It is
 * {@link #IMPLEMENTATION_FAILURE} when any sample or component is
 * non-finite, {@link #VARIANCE_REDUCED} when every component has smaller
 * sample variance than correlated FD, {@link #VARIANCE_FAILURE} when no
 * component does, and {@link #NUMERICALLY_OPERATIONAL} otherwise.
 */
public final class AcZvFermiNetForceEstimator implements FermiNetNuclearForceEstimator {

    public static final String NUMERICALLY_OPERATIONAL = "AC_ZV_NUMERICALLY_OPERATIONAL";
    public static final String VARIANCE_REDUCED = "AC_ZV_VARIANCE_REDUCED";
    public static final String VARIANCE_FAILURE = "AC_ZV_VARIANCE_FAILURE";
    public static final String IMPLEMENTATION_FAILURE = "AC_ZV_IMPLEMENTATION_FAILURE";

    public static final String FORMULATION =
            "QIAN_2022_EQ6_CONSISTENT_ZV_NN_PLUS_CONTRACTION";
    public static final String AUXILIARY =
            "QIAN_2022_EQS_9_10_WITH_NUCLEAR_CHARGE";

    private static final String CORRELATED_FD_REFERENCE_FILE =
            "correlated-fd-reference.json";

    @Override
    public NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration,
            FermiNetDerivativeEngine derivativeEngine) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.estimatorType() != NuclearForceEstimatorType.AC_ZV) {
            throw new IllegalArgumentException("AC-ZV estimator configuration mismatch");
        }
        context.verifyDataset();
        var reference = loadCorrelatedFdReference(context);
        FermiNetV1State state = context.state();
        Molecule molecule = state.molecule();
        int nuclei = molecule.nuclei().size();
        int components = 3 * nuclei;
        if (reference.components().size() != components) {
            throw new IOException("correlated-FD reference component count mismatch");
        }
        int samples = context.dataset().sampleCount();
        int walkers = context.dataset().walkerCount();

        double[] nuclearRepulsion = new double[components];
        for (int nucleus = 0; nucleus < nuclei; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                nuclearRepulsion[3 * nucleus + axis] =
                        nuclearRepulsionForce(molecule, nucleus, axis);
            }
        }

        int[] chains = new int[samples];
        double[][] forceSamples = new double[components][samples];
        boolean[][] finite = new boolean[components][samples];
        double[] contractionSums = new double[components];

        FermiNetCorrelatedFdConfigurationFile.forEach(
                context.configurationFile(), walkers,
                (sample, chain, retained, coordinates) -> {
            chains[sample] = chain;
            double[] logGradient;
            try {
                logGradient = FermiNetStateAccess.spatial(state, coordinates)
                        .logCoordinateGradient();
            } catch (RuntimeException exception) {
                logGradient = null;
            }
            for (int component = 0; component < components; component++) {
                int nucleus = component / 3;
                int axis = component % 3;
                if (logGradient == null) {
                    forceSamples[component][sample] = Double.NaN;
                    continue;
                }
                double contraction;
                try {
                    contraction = auxiliaryContraction(
                            molecule, coordinates, logGradient, nucleus, axis);
                } catch (RuntimeException exception) {
                    forceSamples[component][sample] = Double.NaN;
                    continue;
                }
                double force = nuclearRepulsion[component] + contraction;
                if (!Double.isFinite(force)) {
                    forceSamples[component][sample] = Double.NaN;
                    continue;
                }
                forceSamples[component][sample] = force;
                finite[component][sample] = true;
                contractionSums[component] += contraction;
            }
        });

        List<NuclearForceResult.Component> resultComponents = new ArrayList<>(components);
        List<NuclearForceResult.AcZvComponentDiagnostics> diagnostics =
                new ArrayList<>(components);
        boolean anyNonfinite = false;
        int reducedComponents = 0;
        for (int component = 0; component < components; component++) {
            int nucleus = component / 3;
            int axis = component % 3;
            int finiteCount = 0;
            for (int sample = 0; sample < samples; sample++) {
                if (finite[component][sample]) finiteCount++;
            }
            anyNonfinite |= finiteCount < samples;
            var fd = reference.components().get(component);
            if (fd.nucleus() != nucleus || fd.axis() != axis) {
                throw new IOException("correlated-FD reference component ordering mismatch");
            }
            ComponentStatistics statistics = ComponentStatistics.compute(
                    forceSamples[component], chains, walkers, finiteCount);
            double varianceReduction = statistics.variance() > 0.0
                    ? fd.forceVariance() / statistics.variance()
                    : Double.POSITIVE_INFINITY;
            if (Double.isFinite(statistics.variance())
                    && statistics.variance() < fd.forceVariance()) {
                reducedComponents++;
            }
            double difference = statistics.mean() - fd.forceHartreePerBohr();
            double combined = Math.sqrt(statistics.chainStandardError()
                    * statistics.chainStandardError()
                    + fd.forceStandardError() * fd.forceStandardError());
            resultComponents.add(new NuclearForceResult.Component(
                    nucleus, axis, axisName(axis), statistics.mean(),
                    statistics.chainStandardError(), statistics.variance(),
                    finiteCount, samples - finiteCount,
                    tails(forceSamples[component], finiteCount, statistics.mean(),
                            Math.sqrt(statistics.variance())),
                    checksum(forceSamples[component]), forceSamples[component]));
            diagnostics.add(new NuclearForceResult.AcZvComponentDiagnostics(
                    nucleus, axis,
                    nuclearRepulsion[component],
                    finiteCount > 0
                            ? contractionSums[component] / finiteCount : Double.NaN,
                    fd.forceHartreePerBohr(), fd.forceStandardError(),
                    fd.forceVariance(), varianceReduction, difference, combined,
                    combined > 0.0 ? difference / combined : Double.NaN));
        }

        String classification;
        if (anyNonfinite) {
            classification = IMPLEMENTATION_FAILURE;
        } else if (reducedComponents == components) {
            classification = VARIANCE_REDUCED;
        } else if (reducedComponents == 0) {
            classification = VARIANCE_FAILURE;
        } else {
            classification = NUMERICALLY_OPERATIONAL;
        }
        return new NuclearForceResult(
                NuclearForceEstimatorType.AC_ZV, classification,
                context.parameterChecksum(), context.geometryIdentity(),
                context.dataset().sha256(), context.checkpointChecksum(),
                configuration.identity(), samples, walkers,
                context.dataset().retainedPerWalker(), resultComponents,
                new NuclearForceResult.AcZvDiagnostics(
                        FORMULATION, AUXILIARY, diagnostics));
    }

    /**
     * Bare nucleus-nucleus Hellmann-Feynman force component,
     * Z_A sum_{B!=A} Z_B (R_A - R_B)_axis / |R_A - R_B|^3, hartree/bohr.
     */
    public static double nuclearRepulsionForce(
            Molecule molecule, int nucleus, int axis) {
        Objects.requireNonNull(molecule, "molecule");
        var nuclei = molecule.nuclei();
        if (nucleus < 0 || nucleus >= nuclei.size()) {
            throw new IllegalArgumentException("nucleus index out of range");
        }
        var origin = nuclei.get(nucleus).position().inBohr();
        double[] r = {origin.x(), origin.y(), origin.z()};
        double z = nuclei.get(nucleus).charge().atomicNumber();
        double force = 0.0;
        for (int other = 0; other < nuclei.size(); other++) {
            if (other == nucleus) continue;
            var position = nuclei.get(other).position().inBohr();
            double dx = r[0] - position.x();
            double dy = r[1] - position.y();
            double dz = r[2] - position.z();
            double r2 = dx * dx + dy * dy + dz * dz;
            if (!(r2 > 0.0)) {
                throw new IllegalArgumentException("coincident nuclei");
            }
            double r3 = r2 * Math.sqrt(r2);
            double[] d = {dx, dy, dz};
            force += z * nuclei.get(other).charge().atomicNumber()
                    * d[axis] / r3;
        }
        return force;
    }

    /**
     * Auxiliary function component
     * Q_A,axis = Z_A sum_i (r_i - R_A)_axis / |r_i - R_A|, dimensionless.
     */
    public static double auxiliaryQ(
            Molecule molecule, QuantumCoordinates coordinates,
            int nucleus, int axis) {
        Objects.requireNonNull(molecule, "molecule");
        Objects.requireNonNull(coordinates, "coordinates");
        var nuclei = molecule.nuclei();
        if (nucleus < 0 || nucleus >= nuclei.size()) {
            throw new IllegalArgumentException("nucleus index out of range");
        }
        var origin = nuclei.get(nucleus).position().inBohr();
        double[] r = {origin.x(), origin.y(), origin.z()};
        double q = 0.0;
        for (var electron : coordinates.particles()) {
            double[] displacement = displacement(electron, r);
            double radius = radius(displacement);
            q += displacement[axis] / radius;
        }
        return nuclei.get(nucleus).charge().atomicNumber() * q;
    }

    /**
     * Zero-variance contraction component
     * div Q_A,axis . grad log|Psi|
     * = Z_A sum_i sum_alpha d((r_i-R_A)_axis/|r_i-R_A|)/de_i,alpha
     *     * d log|Psi|/de_i,alpha,
     * with d(disp_c/r)/de_a = delta_ca/r - disp_c disp_a/r^3.
     * The log gradient is the validated electron log-gradient in
     * electron-major (x, y, z) order, hartree/bohr.
     */
    public static double auxiliaryContraction(
            Molecule molecule, QuantumCoordinates coordinates,
            double[] logGradient, int nucleus, int axis) {
        Objects.requireNonNull(molecule, "molecule");
        Objects.requireNonNull(coordinates, "coordinates");
        Objects.requireNonNull(logGradient, "logGradient");
        var nuclei = molecule.nuclei();
        if (nucleus < 0 || nucleus >= nuclei.size()) {
            throw new IllegalArgumentException("nucleus index out of range");
        }
        if (logGradient.length != 3 * coordinates.particles().size()) {
            throw new IllegalArgumentException("log-gradient dimension mismatch");
        }
        var origin = nuclei.get(nucleus).position().inBohr();
        double[] r = {origin.x(), origin.y(), origin.z()};
        double contraction = 0.0;
        for (int i = 0; i < coordinates.particles().size(); i++) {
            double[] displacement =
                    displacement(coordinates.particles().get(i), r);
            double radius = radius(displacement);
            double radius3 = radius * radius * radius;
            for (int alpha = 0; alpha < 3; alpha++) {
                double derivative = (alpha == axis ? 1.0 / radius : 0.0)
                        - displacement[axis] * displacement[alpha] / radius3;
                contraction += derivative * logGradient[3 * i + alpha];
            }
        }
        return nuclei.get(nucleus).charge().atomicNumber() * contraction;
    }

    private static double[] displacement(
            QuantumCoordinates.ParticleCoordinate electron, double[] nucleus) {
        return new double[] {electron.xBohr() - nucleus[0],
                electron.yBohr() - nucleus[1], electron.zBohr() - nucleus[2]};
    }

    private static double radius(double[] displacement) {
        double r2 = displacement[0] * displacement[0]
                + displacement[1] * displacement[1]
                + displacement[2] * displacement[2];
        if (!(r2 > 0.0) || !Double.isFinite(r2)) {
            throw new IllegalArgumentException(
                    "electron-nucleus coalescence is undefined for raw AC samples");
        }
        return Math.sqrt(r2);
    }

    private static FermiNetCorrelatedFiniteDifferenceForceReference.Result
            loadCorrelatedFdReference(FermiNetForceEvaluationContext context)
            throws IOException {
        Path referenceFile = context.configurationFile().getParent()
                .resolve(CORRELATED_FD_REFERENCE_FILE);
        if (!Files.exists(referenceFile)) {
            throw new IOException(
                    "correlated-FD comparison reference missing: " + referenceFile);
        }
        FermiNetCorrelatedFiniteDifferenceForceReference.Result reference;
        try {
            reference = new ObjectMapper().readValue(referenceFile.toFile(),
                    FermiNetCorrelatedFiniteDifferenceForceReference.Result.class);
        } catch (RuntimeException exception) {
            throw new IOException(
                    "correlated-FD comparison reference unreadable", exception);
        }
        if (!context.parameterChecksum().equals(reference.parameterChecksum())
                || !context.dataset().equals(reference.dataset())) {
            throw new IOException(
                    "correlated-FD comparison reference provenance mismatch");
        }
        return reference;
    }

    private static NuclearForceResult.TailDiagnostics tails(
            double[] values, int finiteCount, double mean, double sd) {
        if (finiteCount == 0) {
            return new NuclearForceResult.TailDiagnostics(Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    0, 0);
        }
        double[] finiteValues = new double[finiteCount];
        int index = 0;
        for (double value : values) {
            if (Double.isFinite(value)) finiteValues[index++] = value;
        }
        double[] sorted = finiteValues.clone();
        Arrays.sort(sorted);
        long beyondFive = 0, beyondTen = 0;
        for (double value : finiteValues) {
            double distance = Math.abs(value - mean);
            if (distance > 5.0 * sd) beyondFive++;
            if (distance > 10.0 * sd) beyondTen++;
        }
        return new NuclearForceResult.TailDiagnostics(sorted[0],
                quantile(sorted, 0.001), quantile(sorted, 0.01),
                quantile(sorted, 0.5), quantile(sorted, 0.99),
                quantile(sorted, 0.999), sorted[sorted.length - 1],
                beyondFive, beyondTen);
    }

    private static double quantile(double[] sorted, double probability) {
        double position = (sorted.length - 1) * probability;
        int lower = (int) Math.floor(position), upper = (int) Math.ceil(position);
        if (lower == upper) return sorted[lower];
        return sorted[lower]
                + (sorted[upper] - sorted[lower]) * (position - lower);
    }

    private static String axisName(int axis) {
        return switch (axis) {
            case 0 -> "x"; case 1 -> "y"; case 2 -> "z";
            default -> throw new IllegalArgumentException("invalid Cartesian axis");
        };
    }

    private static String checksum(double[] values) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        for (double value : values) {
            long bits = Double.doubleToRawLongBits(value);
            for (int shift = 56; shift >= 0; shift -= 8) {
                digest.update((byte) (bits >>> shift));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Mean, chain-aware standard error, and sample variance over finite samples. */
    private record ComponentStatistics(
            double mean, double chainStandardError, double variance) {
        private static ComponentStatistics compute(
                double[] forceSamples, int[] chains, int walkers, int finiteCount) {
            if (finiteCount == 0) {
                return new ComponentStatistics(
                        Double.NaN, Double.NaN, Double.NaN);
            }
            double sum = 0.0, sumSquares = 0.0;
            double[] chainSums = new double[walkers];
            int[] chainCounts = new int[walkers];
            for (int sample = 0; sample < forceSamples.length; sample++) {
                double value = forceSamples[sample];
                if (!Double.isFinite(value)) continue;
                sum += value;
                sumSquares += value * value;
                chainSums[chains[sample]] += value;
                chainCounts[chains[sample]]++;
            }
            double mean = sum / finiteCount;
            double variance = finiteCount > 1
                    ? Math.max(0.0, (sumSquares - sum * sum / finiteCount)
                            / (finiteCount - 1))
                    : Double.NaN;
            double chainSquares = 0.0;
            for (int chain = 0; chain < walkers; chain++) {
                if (chainCounts[chain] == 0) {
                    return new ComponentStatistics(mean, Double.NaN, variance);
                }
                double difference = chainSums[chain] / chainCounts[chain] - mean;
                chainSquares += difference * difference;
            }
            double chainSe = walkers > 1
                    ? Math.sqrt(chainSquares / (walkers - 1) / walkers)
                    : Double.NaN;
            return new ComponentStatistics(mean, chainSe, variance);
        }
    }
}
