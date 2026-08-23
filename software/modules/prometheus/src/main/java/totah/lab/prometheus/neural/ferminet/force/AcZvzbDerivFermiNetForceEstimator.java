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
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetPhysicalSingularityException;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Assaraf-Caffarel 2003 v=0 split-auxiliary ZVZB nuclear-force estimator
 * evaluated for the frozen-parameter FermiNet ansatz, behind the canonical
 * pipeline.
 *
 * <p>Per sample, in the force convention F = -dE/dR:
 *
 * <pre>
 * F_A,c = F_A,c,nn + G_A,c + 2 (E_v - E_L) d log|Psi| / dR_A,c
 * </pre>
 *
 * The zero-variance part uses the nuclear-charge-generalized minimal
 * auxiliary Q_A = Z_A sum_i (r_i - R_A)/|r_i - R_A| exactly as in
 * {@link AcZvFermiNetForceEstimator} (F_nn + G, with the electron-nucleus
 * bare force cancelled analytically). The zero-bias density-response part
 * uses the actual frozen-FermiNet nuclear log derivative from
 * {@link FermiNetStateAccess#nuclear} - a partial derivative at fixed
 * electrons and fixed parameters - in place of the minimal-Q response used
 * by {@link AcZvzbFermiNetForceEstimator}. The sign convention was derived
 * independently from AC 2003 (the paper's observable estimates +dE/dR;
 * negation to the force convention flips the printed ZB sign) and is
 * consistent with the validated Qian-Eq.-6 convention used by AC-ZVZB.
 *
 * <p>No specialization of the AC zero-bias theorem to the frozen-parameter
 * ansatz is claimed: bias properties are reported as descriptive evidence
 * only. The ZB term with the derivative response and, for direct
 * comparison, the ZB term with the minimal-Q response are both reported
 * with full tail statistics. E_v is the plain dataset mean local energy;
 * the dataset is drawn from the frozen |Psi|^2 distribution, so no
 * importance weighting is applied. There is no finite-difference fallback.
 *
 * <p>The classification is a descriptive evidence level, not an acceptance
 * decision: no numerical pass/fail thresholds are applied. It is
 * {@link #IMPLEMENTATION_FAILURE} when any sample or component is
 * non-finite, {@link #VARIANCE_REDUCED} when every component has smaller
 * sample variance than correlated FD, {@link #VARIANCE_FAILURE} when no
 * component does, and {@link #NUMERICALLY_OPERATIONAL} otherwise.
 */
public final class AcZvzbDerivFermiNetForceEstimator
        implements FermiNetNuclearForceEstimator {

    public static final String NUMERICALLY_OPERATIONAL =
            "AC_ZVZB_DERIV_NUMERICALLY_OPERATIONAL";
    public static final String VARIANCE_REDUCED = "AC_ZVZB_DERIV_VARIANCE_REDUCED";
    public static final String VARIANCE_FAILURE = "AC_ZVZB_DERIV_VARIANCE_FAILURE";
    public static final String IMPLEMENTATION_FAILURE =
            "AC_ZVZB_DERIV_IMPLEMENTATION_FAILURE";

    public static final String FORMULATION =
            "AC_2003_SPLIT_AUXILIARY_V0_FROZEN_PARAMETER_FERMINET";
    public static final String AUXILIARY =
            "ZV_MINIMAL_Q_WITH_NUCLEAR_CHARGE__ZB_NUCLEAR_LOG_DERIVATIVE";
    static final String PATHAK_WAGNER_PROVENANCE =
            "Pathak-Wagner-2020-Eqs-3-4__response-term-only";

    private static final String CORRELATED_FD_REFERENCE_FILE =
            "correlated-fd-reference.json";

    @Override
    public NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration,
            FermiNetDerivativeEngine derivativeEngine) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.estimatorType()
                != NuclearForceEstimatorType.AC_ZVZB_DERIV) {
            throw new IllegalArgumentException(
                    "AC-ZVZB-DERIV estimator configuration mismatch");
        }
        context.verifyDataset();
        var reference = loadCorrelatedFdReference(context);
        EstimatorComparison swct = loadEstimatorComparison(context,
                NuclearForceEstimatorType.SWCT);
        EstimatorComparison acZv = loadEstimatorComparison(context,
                NuclearForceEstimatorType.AC_ZV);
        EstimatorComparison acZvzb = loadEstimatorComparison(context,
                NuclearForceEstimatorType.AC_ZVZB);
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

        // Pass one: validated local energy, electron log-gradient, and frozen
        // nuclear log derivative per sample.
        int[] chains = new int[samples];
        double[] localEnergies = new double[samples];
        double[][] electronLogGradients = new double[samples][];
        double[][] nuclearLogGradients = new double[samples][];
        QuantumCoordinates[] configurations = new QuantumCoordinates[samples];
        FermiNetCorrelatedFdConfigurationFile.forEach(
                context.configurationFile(), walkers,
                (sample, chain, retained, coordinates) -> {
            chains[sample] = chain;
            configurations[sample] = coordinates;
            try {
                double localEnergy = FermiNetRuntimeSampling.localEnergyWithLog(
                        state, coordinates).localEnergy().totalHartree();
                double[] electronLogGradient = FermiNetStateAccess.spatial(
                        state, coordinates).logCoordinateGradient();
                double[] nuclearLogGradient = FermiNetStateAccess.nuclear(
                        state, coordinates).logNuclearGradient();
                if (!Double.isFinite(localEnergy)) {
                    throw new IllegalStateException("non-finite local energy");
                }
                localEnergies[sample] = localEnergy;
                electronLogGradients[sample] = electronLogGradient;
                nuclearLogGradients[sample] = nuclearLogGradient;
            } catch (FermiNetPhysicalSingularityException exception) {
                localEnergies[sample] = Double.NaN;
                electronLogGradients[sample] = null;
                nuclearLogGradients[sample] = null;
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

        // Pass two: F = nn + G + 2(E_v - E_L) d log|Psi|/dR per component.
        double[][] forceSamples = new double[components][samples];
        double[][] zbDerivSamples = new double[components][samples];
        double[][] zbMinimalQSamples = new double[components][samples];
        boolean[][] finite = new boolean[components][samples];
        double[] contractionSums = new double[components];
        double[] responseSums = new double[components];
        double[][] finitePartSamples = configuration.pathakWagner() == null
                ? null : new double[components][samples];
        for (int sample = 0; sample < samples; sample++) {
            if (electronLogGradients[sample] == null) continue;
            for (int component = 0; component < components; component++) {
                int nucleus = component / 3;
                int axis = component % 3;
                double q, contraction;
                try {
                    q = AcZvFermiNetForceEstimator.auxiliaryQ(
                            molecule, configurations[sample], nucleus, axis);
                    contraction = AcZvFermiNetForceEstimator.auxiliaryContraction(
                            molecule, configurations[sample],
                            electronLogGradients[sample], nucleus, axis);
                } catch (FermiNetPhysicalSingularityException exception) {
                    markNonfinite(forceSamples, zbDerivSamples,
                            zbMinimalQSamples, finite, component, sample);
                    continue;
                }
                double centered = meanEnergy - localEnergies[sample];
                double zbDeriv =
                        2.0 * centered * nuclearLogGradients[sample][component];
                double zbMinimalQ = 2.0 * centered * q;
                double force = nuclearRepulsion[component] + contraction + zbDeriv;
                if (!Double.isFinite(force) || !Double.isFinite(zbMinimalQ)) {
                    markNonfinite(forceSamples, zbDerivSamples,
                            zbMinimalQSamples, finite, component, sample);
                    continue;
                }
                forceSamples[component][sample] = force;
                if (finitePartSamples != null) {
                    finitePartSamples[component][sample] =
                            nuclearRepulsion[component] + contraction;
                }
                zbDerivSamples[component][sample] = zbDeriv;
                zbMinimalQSamples[component][sample] = zbMinimalQ;
                finite[component][sample] = true;
                contractionSums[component] += contraction;
                responseSums[component] += nuclearLogGradients[sample][component];
            }
        }

        List<NuclearForceResult.Component> resultComponents = new ArrayList<>(components);
        List<NuclearForceResult.AcZvzbDerivComponentDiagnostics> diagnostics =
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
                    || acZv.axis(component) != axis
                    || acZvzb.nucleus(component) != nucleus
                    || acZvzb.axis(component) != axis) {
                throw new IOException("comparison component ordering mismatch");
            }
            ComponentStatistics statistics = ComponentStatistics.compute(
                    forceSamples[component], chains, walkers, finiteCount);
            ComponentStatistics zbDerivStats = ComponentStatistics.compute(
                    zbDerivSamples[component], chains, walkers, finiteCount);
            ComponentStatistics zbMinimalQStats = ComponentStatistics.compute(
                    zbMinimalQSamples[component], chains, walkers, finiteCount);
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
            diagnostics.add(new NuclearForceResult.AcZvzbDerivComponentDiagnostics(
                    nucleus, axis,
                    nuclearRepulsion[component],
                    mean(contractionSums[component], finiteCount),
                    mean(responseSums[component], finiteCount),
                    zbDerivStats.mean(), zbDerivStats.chainStandardError(),
                    zbDerivStats.variance(),
                    extremes(zbDerivSamples[component], zbDerivStats.mean(),
                            Math.sqrt(zbDerivStats.variance()), finiteCount),
                    zbMinimalQStats.mean(), zbMinimalQStats.chainStandardError(),
                    zbMinimalQStats.variance(),
                    extremes(zbMinimalQSamples[component],
                            zbMinimalQStats.mean(),
                            Math.sqrt(zbMinimalQStats.variance()), finiteCount),
                    fd.forceHartreePerBohr(), fd.forceStandardError(),
                    fd.forceVariance(), reduction(fd.forceVariance(), variance),
                    swct.mean(component), swct.chainStandardError(component),
                    swct.variance(component),
                    reduction(swct.variance(component), variance),
                    acZv.mean(component), acZv.chainStandardError(component),
                    acZv.variance(component),
                    reduction(acZv.variance(component), variance),
                    acZvzb.mean(component), acZvzb.chainStandardError(component),
                    acZvzb.variance(component),
                    reduction(acZvzb.variance(component), variance)));
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
        NuclearForceResult.PathakWagnerDiagnostics pathakWagner =
                configuration.pathakWagner() == null ? null
                        : pathakWagnerDiagnostics(configuration.pathakWagner(),
                                electronLogGradients, finitePartSamples,
                                zbDerivSamples, finite, chains, walkers,
                                components, samples);
        return new NuclearForceResult(
                NuclearForceEstimatorType.AC_ZVZB_DERIV, classification,
                context.parameterChecksum(), context.geometryIdentity(),
                context.dataset().sha256(), context.checkpointChecksum(),
                configuration.identity(), samples, walkers,
                context.dataset().retainedPerWalker(), resultComponents,
                new NuclearForceResult.AcZvzbDerivDiagnostics(
                        FORMULATION, AUXILIARY, meanEnergy, diagnostics,
                        pathakWagner));
    }

    private static NuclearForceResult.PathakWagnerDiagnostics
            pathakWagnerDiagnostics(
                    NuclearForceConfiguration.PathakWagnerConfiguration configuration,
                    double[][] electronLogGradients,
                    double[][] finitePartSamples,
                    double[][] zbSamples,
                    boolean[][] finite,
                    int[] chains,
                    int walkers,
                    int components,
                    int samples) {
        double[] epsilon = configuration.epsilonBohr();
        double[] nodalDistance = new double[samples];
        for (int sample = 0; sample < samples; sample++) {
            nodalDistance[sample] = electronLogGradients[sample] == null
                    ? Double.NaN : nodalDistance(electronLogGradients[sample]);
        }
        double[] sortedDistance = Arrays.stream(nodalDistance)
                .filter(Double::isFinite).sorted().toArray();
        if (sortedDistance.length != samples) {
            throw new IllegalStateException("non-finite Pathak-Wagner nodal distance");
        }
        var distanceDiagnostics = new NuclearForceResult.NodalDistanceDiagnostics(
                sortedDistance[0], quantile(sortedDistance, 0.001),
                quantile(sortedDistance, 0.01), quantile(sortedDistance, 0.05),
                quantile(sortedDistance, 0.5), checksum(nodalDistance), nodalDistance);

        double[][][] panelSamples = new double[epsilon.length][components][samples];
        List<NuclearForceResult.PathakWagnerEpsilonDiagnostics> panel =
                new ArrayList<>(epsilon.length);
        for (int e = 0; e < epsilon.length; e++) {
            long affected = 0;
            long factorLessThanOne = 0;
            long factorGreaterThanOne = 0;
            double factorSum = 0.0;
            double[] factors = new double[samples];
            for (int sample = 0; sample < samples; sample++) {
                double factor = pathakWagnerFactor(nodalDistance[sample], epsilon[e]);
                factors[sample] = factor;
                factorSum += factor;
                if (factor != 1.0) affected++;
                if (factor < 1.0) factorLessThanOne++;
                if (factor > 1.0) factorGreaterThanOne++;
                for (int component = 0; component < components; component++) {
                    panelSamples[e][component][sample] = finite[component][sample]
                            ? finitePartSamples[component][sample]
                                    + factor * zbSamples[component][sample]
                            : Double.NaN;
                }
            }
            List<NuclearForceResult.PathakWagnerComponentDiagnostics> estimates =
                    new ArrayList<>(components);
            for (int component = 0; component < components; component++) {
                int finiteCount = 0;
                for (double value : panelSamples[e][component]) {
                    if (Double.isFinite(value)) finiteCount++;
                }
                ComponentStatistics statistics = ComponentStatistics.compute(
                        panelSamples[e][component], chains, walkers, finiteCount);
                estimates.add(new NuclearForceResult.PathakWagnerComponentDiagnostics(
                        component / 3, component % 3, statistics.mean(),
                        statistics.chainStandardError(), statistics.variance(),
                        finiteCount, samples - finiteCount,
                        tails(panelSamples[e][component], finiteCount,
                                statistics.mean(), Math.sqrt(statistics.variance())),
                        checksum(panelSamples[e][component]),
                        panelSamples[e][component]));
            }
            panel.add(new NuclearForceResult.PathakWagnerEpsilonDiagnostics(
                    epsilon[e], affected, factorLessThanOne,
                    factorGreaterThanOne,
                    factorSum / samples, estimates));
        }

        List<NuclearForceResult.PathakWagnerExtrapolation> extrapolations =
                new ArrayList<>(components);
        for (int component = 0; component < components; component++) {
            extrapolations.add(extrapolation(
                    component, epsilon, panelSamples, chains, walkers));
        }
        return new NuclearForceResult.PathakWagnerDiagnostics(
                PATHAK_WAGNER_PROVENANCE, distanceDiagnostics, panel,
                extrapolations);
    }

    static double nodalDistance(double[] electronLogGradient) {
        Objects.requireNonNull(electronLogGradient, "electronLogGradient");
        double squaredNorm = 0.0;
        for (double value : electronLogGradient) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("non-finite electron log gradient");
            }
            squaredNorm += value * value;
        }
        return squaredNorm == 0.0
                ? Double.POSITIVE_INFINITY : 1.0 / Math.sqrt(squaredNorm);
    }

    static double pathakWagnerFactor(double nodalDistance, double epsilon) {
        if (!(nodalDistance >= 0.0) || !(epsilon > 0.0)
                || !Double.isFinite(epsilon)) {
            throw new IllegalArgumentException("invalid Pathak-Wagner input");
        }
        double s = nodalDistance / epsilon;
        if (s >= 1.0) return 1.0;
        double s2 = s * s;
        double s4 = s2 * s2;
        return 7.0 * s4 * s2 - 15.0 * s4 + 9.0 * s2;
    }

    private static NuclearForceResult.PathakWagnerExtrapolation extrapolation(
            int component, double[] epsilon, double[][][] samples,
            int[] chains, int walkers) {
        double[] x = new double[epsilon.length];
        double[] y = new double[epsilon.length];
        for (int i = 0; i < epsilon.length; i++) {
            x[i] = epsilon[i] * epsilon[i] * epsilon[i];
            y[i] = ComponentStatistics.compute(samples[i][component], chains,
                    walkers, samples[i][component].length).mean();
        }
        LinearFit fit = linearFit(x, y, 0, x.length);
        double[] coefficients = interceptCoefficients(x, 0, x.length);
        double[] interceptSamples = new double[samples[0][component].length];
        for (int sample = 0; sample < interceptSamples.length; sample++) {
            for (int i = 0; i < coefficients.length; i++) {
                interceptSamples[sample] += coefficients[i]
                        * samples[i][component][sample];
            }
        }
        ComponentStatistics interceptStatistics = ComponentStatistics.compute(
                interceptSamples, chains, walkers, interceptSamples.length);
        double[] residuals = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            residuals[i] = y[i] - (fit.intercept() + fit.slope() * x[i]);
        }
        double dropLargest = linearFit(x, y, 1, x.length).intercept();
        double dropSmallest = linearFit(x, y, 0, x.length - 1).intercept();
        return new NuclearForceResult.PathakWagnerExtrapolation(
                component / 3, component % 3, fit.intercept(),
                interceptStatistics.chainStandardError(), fit.slope(), residuals,
                dropLargest, dropSmallest, false);
    }

    private static double[] interceptCoefficients(double[] x, int start, int end) {
        int n = end - start;
        double sx = 0.0, sxx = 0.0;
        for (int i = start; i < end; i++) {
            sx += x[i];
            sxx += x[i] * x[i];
        }
        double denominator = n * sxx - sx * sx;
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = (sxx - sx * x[start + i]) / denominator;
        }
        return result;
    }

    private static LinearFit linearFit(double[] x, double[] y, int start, int end) {
        int n = end - start;
        if (n < 2) throw new IllegalArgumentException("at least two fit points required");
        double sx = 0.0, sy = 0.0, sxx = 0.0, sxy = 0.0;
        for (int i = start; i < end; i++) {
            sx += x[i];
            sy += y[i];
            sxx += x[i] * x[i];
            sxy += x[i] * y[i];
        }
        double denominator = n * sxx - sx * sx;
        return new LinearFit((sxx * sy - sx * sxy) / denominator,
                (n * sxy - sx * sy) / denominator);
    }

    private record LinearFit(double intercept, double slope) {}

    private static void markNonfinite(
            double[][] forceSamples, double[][] zbDerivSamples,
            double[][] zbMinimalQSamples, boolean[][] finite,
            int component, int sample) {
        forceSamples[component][sample] = Double.NaN;
        zbDerivSamples[component][sample] = Double.NaN;
        zbMinimalQSamples[component][sample] = Double.NaN;
        finite[component][sample] = false;
    }

    private static double mean(double sum, int count) {
        return count > 0 ? sum / count : Double.NaN;
    }

    private static double reduction(double referenceVariance, double variance) {
        return variance > 0.0 ? referenceVariance / variance
                : Double.POSITIVE_INFINITY;
    }

    private static NuclearForceResult.ZbTermExtremes extremes(
            double[] values, double mean, double sd, int finiteCount) {
        if (finiteCount == 0) {
            return new NuclearForceResult.ZbTermExtremes(
                    Double.NaN, Double.NaN, 0, 0);
        }
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        long beyondFive = 0, beyondTen = 0;
        for (double value : values) {
            if (!Double.isFinite(value)) continue;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            double distance = Math.abs(value - mean);
            if (distance > 5.0 * sd) beyondFive++;
            if (distance > 10.0 * sd) beyondTen++;
        }
        return new NuclearForceResult.ZbTermExtremes(
                minimum, maximum, beyondFive, beyondTen);
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
            int expectedChainCount = chainCounts[0];
            for (int chain = 0; chain < walkers; chain++) {
                if (chainCounts[chain] == 0
                        || chainCounts[chain] != expectedChainCount) {
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
