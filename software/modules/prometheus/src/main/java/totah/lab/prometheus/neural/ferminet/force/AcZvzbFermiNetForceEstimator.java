package totah.lab.prometheus.neural.ferminet.force;

import com.fasterxml.jackson.databind.JsonNode;
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
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetRuntimeSampling;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngine;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Assaraf-Caffarel zero-variance zero-bias (AC-ZVZB) nuclear-force estimator
 * for the frozen FermiNet state, behind the canonical pipeline.
 *
 * <p>Implements the general-molecule form of Qian et al., J. Chem. Phys.
 * 157, 164104 (2022) Eq. 6, audited against the surviving H2 reference
 * implementation and against Assaraf-Caffarel 2003 Eq. 19:
 *
 * <pre>
 * F_A,c^ZVZB = F_A,c,nn + G_A,c + 2 (E_v - E_L) Q_A,c
 * Q_A,c      = Z_A sum_i (r_i - R_A)_c / |r_i - R_A|
 * </pre>
 *
 * with F_A,c,nn the bare nucleus-nucleus Hellmann-Feynman force and G_A,c the
 * zero-variance contraction div Q_A,c . grad log|Psi|. The electron-nucleus
 * bare force cancels analytically against -1/2 laplacian(Q_A) and is never
 * evaluated. The ZB term is the density-response (Pulay-like) bias
 * correction; E_v is the plain mean local energy over the dataset, which is
 * already drawn from the frozen |Psi|^2 distribution, so no additional
 * importance weighting is applied.
 *
 * <p>All molecular primitives ({@code nuclearRepulsionForce},
 * {@code auxiliaryQ}, {@code auxiliaryContraction}) are reused from
 * {@link AcZvFermiNetForceEstimator}, which carries the tested
 * nuclear-charge-generalized auxiliary. Local energies come from the
 * validated {@link FermiNetRuntimeSampling#localEnergyWithLog} path; the
 * electron log-gradient from {@link FermiNetStateAccess#spatial}. There is
 * no finite-difference fallback.
 *
 * <p>The classification is a descriptive evidence level, not an acceptance
 * decision: no numerical pass/fail thresholds are applied. It is
 * {@link #IMPLEMENTATION_FAILURE} when any sample or component is
 * non-finite, {@link #VARIANCE_REDUCED} when every component has smaller
 * sample variance than correlated FD, {@link #VARIANCE_FAILURE} when no
 * component does, and {@link #NUMERICALLY_OPERATIONAL} otherwise.
 */
public final class AcZvzbFermiNetForceEstimator implements FermiNetNuclearForceEstimator {

    public static final String NUMERICALLY_OPERATIONAL =
            "AC_ZVZB_NUMERICALLY_OPERATIONAL";
    public static final String VARIANCE_REDUCED = "AC_ZVZB_VARIANCE_REDUCED";
    public static final String VARIANCE_FAILURE = "AC_ZVZB_VARIANCE_FAILURE";
    public static final String IMPLEMENTATION_FAILURE =
            "AC_ZVZB_IMPLEMENTATION_FAILURE";

    public static final String FORMULATION =
            "QIAN_2022_EQ6_ZVZB_NN_PLUS_CONTRACTION_PLUS_ZB";
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
        if (configuration.estimatorType() != NuclearForceEstimatorType.AC_ZVZB) {
            throw new IllegalArgumentException("AC-ZVZB estimator configuration mismatch");
        }
        context.verifyDataset();
        var reference = loadCorrelatedFdReference(context);
        EstimatorComparison swct = loadEstimatorComparison(context,
                NuclearForceEstimatorType.SWCT);
        EstimatorComparison acZv = loadEstimatorComparison(context,
                NuclearForceEstimatorType.AC_ZV);
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
                        AcZvFermiNetForceEstimator.nuclearRepulsionForce(
                                molecule, nucleus, axis);
            }
        }

        // Pass one: validated local energy and electron log-gradient per sample.
        int[] chains = new int[samples];
        double[] localEnergies = new double[samples];
        double[][] logGradients = new double[samples][];
        QuantumCoordinates[] configurations = new QuantumCoordinates[samples];
        FermiNetCorrelatedFdConfigurationFile.forEach(
                context.configurationFile(), walkers,
                (sample, chain, retained, coordinates) -> {
            chains[sample] = chain;
            configurations[sample] = coordinates;
            try {
                double localEnergy = FermiNetRuntimeSampling.localEnergyWithLog(
                        state, coordinates).localEnergy().totalHartree();
                double[] logGradient = FermiNetStateAccess.spatial(
                        state, coordinates).logCoordinateGradient();
                if (!Double.isFinite(localEnergy)) {
                    throw new IllegalStateException("non-finite local energy");
                }
                localEnergies[sample] = localEnergy;
                logGradients[sample] = logGradient;
            } catch (RuntimeException exception) {
                localEnergies[sample] = Double.NaN;
                logGradients[sample] = null;
            }
        });
        double energySum = 0.0;
        int energyCount = 0;
        for (double localEnergy : localEnergies) {
            if (Double.isFinite(localEnergy)) {
                energySum += localEnergy;
                energyCount++;
            }
        }
        double meanEnergy = energyCount > 0
                ? energySum / energyCount : Double.NaN;

        // Pass two: F = nn + G + 2 (E_v - E_L) Q per component per sample.
        double[][] forceSamples = new double[components][samples];
        boolean[][] finite = new boolean[components][samples];
        double[] contractionSums = new double[components];
        double[] auxiliarySums = new double[components];
        double[] zeroBiasSums = new double[components];
        for (int sample = 0; sample < samples; sample++) {
            if (logGradients[sample] == null) continue;
            for (int component = 0; component < components; component++) {
                int nucleus = component / 3;
                int axis = component % 3;
                double q, contraction;
                try {
                    q = AcZvFermiNetForceEstimator.auxiliaryQ(
                            molecule, configurations[sample], nucleus, axis);
                    contraction = AcZvFermiNetForceEstimator.auxiliaryContraction(
                            molecule, configurations[sample],
                            logGradients[sample], nucleus, axis);
                } catch (RuntimeException exception) {
                    forceSamples[component][sample] = Double.NaN;
                    continue;
                }
                double zeroBias =
                        2.0 * (meanEnergy - localEnergies[sample]) * q;
                double force = nuclearRepulsion[component] + contraction + zeroBias;
                if (!Double.isFinite(force)) {
                    forceSamples[component][sample] = Double.NaN;
                    continue;
                }
                forceSamples[component][sample] = force;
                finite[component][sample] = true;
                contractionSums[component] += contraction;
                auxiliarySums[component] += q;
                zeroBiasSums[component] += zeroBias;
            }
        }

        List<NuclearForceResult.Component> resultComponents = new ArrayList<>(components);
        List<NuclearForceResult.AcZvzbComponentDiagnostics> diagnostics =
                new ArrayList<>(components);
        boolean anyNonfinite = energyCount < samples;
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
            if (fd.nucleus() != nucleus || fd.axis() != axis
                    || swct.nucleus(component) != nucleus
                    || swct.axis(component) != axis
                    || acZv.nucleus(component) != nucleus
                    || acZv.axis(component) != axis) {
                throw new IOException("comparison component ordering mismatch");
            }
            ComponentStatistics statistics = ComponentStatistics.compute(
                    forceSamples[component], chains, walkers, finiteCount);
            double variance = statistics.variance();
            if (Double.isFinite(variance) && variance < fd.forceVariance()) {
                reducedComponents++;
            }
            resultComponents.add(new NuclearForceResult.Component(
                    nucleus, axis, axisName(axis), statistics.mean(),
                    statistics.chainStandardError(), variance,
                    finiteCount, samples - finiteCount,
                    tails(forceSamples[component], finiteCount, statistics.mean(),
                            Math.sqrt(variance)),
                    checksum(forceSamples[component]), forceSamples[component]));
            diagnostics.add(new NuclearForceResult.AcZvzbComponentDiagnostics(
                    nucleus, axis,
                    nuclearRepulsion[component],
                    mean(contractionSums[component], finiteCount),
                    mean(auxiliarySums[component], finiteCount),
                    mean(zeroBiasSums[component], finiteCount),
                    fd.forceHartreePerBohr(), fd.forceStandardError(),
                    fd.forceVariance(), reduction(fd.forceVariance(), variance),
                    swct.mean(component), swct.chainStandardError(component),
                    swct.variance(component),
                    reduction(swct.variance(component), variance),
                    acZv.mean(component), acZv.chainStandardError(component),
                    acZv.variance(component),
                    reduction(acZv.variance(component), variance)));
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
                NuclearForceEstimatorType.AC_ZVZB, classification,
                context.parameterChecksum(), context.geometryIdentity(),
                context.dataset().sha256(), context.checkpointChecksum(),
                configuration.identity(), samples, walkers,
                context.dataset().retainedPerWalker(), resultComponents,
                new NuclearForceResult.AcZvzbDiagnostics(
                        FORMULATION, AUXILIARY, meanEnergy, diagnostics));
    }

    private static double mean(double sum, int count) {
        return count > 0 ? sum / count : Double.NaN;
    }

    private static double reduction(double referenceVariance, double variance) {
        return variance > 0.0 ? referenceVariance / variance
                : Double.POSITIVE_INFINITY;
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

    /**
     * Loads per-component mean/chain-SE/variance evidence from a previously
     * qualified estimator artifact of the same dataset, failing closed on any
     * identity mismatch. Only the common component statistics are read.
     */
    private static EstimatorComparison loadEstimatorComparison(
            FermiNetForceEvaluationContext context,
            NuclearForceEstimatorType type) throws IOException {
        Path artifact = context.configurationFile().getParent().getParent()
                .resolve(context.dataset().sha256()).resolve(type.name())
                .resolve("nuclear-force-result.json");
        if (!Files.exists(artifact)) {
            throw new IOException("estimator comparison artifact missing: "
                    + artifact);
        }
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(artifact.toFile());
        } catch (RuntimeException exception) {
            throw new IOException(
                    "estimator comparison artifact unreadable", exception);
        }
        JsonNode result = root.get("result");
        if (result == null
                || !type.name().equals(result.path("estimatorType").asText())
                || !context.parameterChecksum().equals(
                        result.path("parameterChecksum").asText())
                || !context.dataset().sha256().equals(
                        result.path("datasetChecksum").asText())) {
            throw new IOException(
                    "estimator comparison artifact provenance mismatch: " + artifact);
        }
        JsonNode components = result.path("components");
        int count = components.size();
        int[] nuclei = new int[count];
        int[] axes = new int[count];
        double[] means = new double[count];
        double[] standardErrors = new double[count];
        double[] variances = new double[count];
        for (int i = 0; i < count; i++) {
            JsonNode component = components.get(i);
            nuclei[i] = component.path("nucleus").asInt(-1);
            axes[i] = component.path("axis").asInt(-1);
            means[i] = component.path("meanHartreePerBohr").asDouble(Double.NaN);
            standardErrors[i] =
                    component.path("chainStandardError").asDouble(Double.NaN);
            variances[i] = component.path("variance").asDouble(Double.NaN);
        }
        return new EstimatorComparison(
                nuclei, axes, means, standardErrors, variances);
    }

    private record EstimatorComparison(
            int[] nuclei, int[] axes, double[] means,
            double[] chainStandardErrors, double[] variances) {
        int nucleus(int component) { return nuclei[component]; }
        int axis(int component) { return axes[component]; }
        double mean(int component) { return means[component]; }
        double chainStandardError(int component) {
            return chainStandardErrors[component];
        }
        double variance(int component) { return variances[component]; }
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
