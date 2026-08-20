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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetRuntimeSampling;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngine;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.force.GeneralMolecularSpaceWarp;

/**
 * Sorella-Capriotti space-warp coordinate-transform (SWCT) nuclear-force
 * estimator for the frozen FermiNet state, behind the canonical pipeline.
 *
 * <p>Per nucleus A and Cartesian axis the estimator evaluates the locked
 * expression
 *
 * <pre>
 * F_A = -<D E_L> - 2 [ <E_L D log(J^1/2 |Psi|)> - <E_L> <D log(J^1/2 |Psi|)> ]
 * </pre>
 *
 * with D the total directional derivative along the warped displacement
 * (nucleus moves, electrons follow with the normalized r^-4 Filippi-Umrigar
 * weights from {@link GeneralMolecularSpaceWarp}). All terms are explicit:
 *
 * <ul>
 *   <li>total directional derivative of the local energy,
 *       D E_L = -1/2 D(laplacian/Psi) + D V;
 *   <li>Coulomb-potential directional derivative D V (bare nuclear derivative
 *       plus warp-weighted electron derivatives), evaluated analytically;
 *   <li>wavefunction directional term D log|Psi| from the validated
 *       directional-derivative runtime API;
 *   <li>Jacobian/divergence term 1/2 sum_i div_i(w_i^A);
 *   <li>covariance (Pulay-like) term formed against the dataset mean local
 *       energy.
 * </ul>
 *
 * <p>There is no finite-difference fallback: any non-finite or singular
 * sample is recorded as non-finite evidence, never recomputed numerically.
 *
 * <p>The correlated-FD reference is loaded only as diagnostic comparison
 * evidence. Its large statistical uncertainty means an indistinguishable
 * difference of means is not evidence that SWCT is unbiased.
 *
 * <p>The classification is a descriptive evidence level, not an acceptance
 * decision: no numerical pass/fail thresholds are applied. It is
 * {@link #IMPLEMENTATION_FAILURE} when any sample or component is
 * non-finite, {@link #VARIANCE_REDUCED} when every component has smaller
 * sample variance than correlated FD, {@link #VARIANCE_FAILURE} when no
 * component does, and {@link #NUMERICALLY_OPERATIONAL} otherwise.
 */
public final class SwctFermiNetForceEstimator implements FermiNetNuclearForceEstimator {

    public static final String NUMERICALLY_OPERATIONAL = "SWCT_NUMERICALLY_OPERATIONAL";
    public static final String VARIANCE_REDUCED = "SWCT_VARIANCE_REDUCED";
    public static final String VARIANCE_FAILURE = "SWCT_VARIANCE_FAILURE";
    public static final String IMPLEMENTATION_FAILURE = "SWCT_IMPLEMENTATION_FAILURE";

    private static final String CORRELATED_FD_REFERENCE_FILE =
            "correlated-fd-reference.json";

    @Override
    public NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration,
            FermiNetDerivativeEngine derivativeEngine) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.estimatorType() != NuclearForceEstimatorType.SWCT) {
            throw new IllegalArgumentException("SWCT estimator configuration mismatch");
        }
        context.verifyDataset();
        var reference = loadCorrelatedFdReference(context);
        FermiNetV1State state = context.state();
        Molecule molecule = state.molecule();
        int components = 3 * molecule.nuclei().size();
        if (reference.components().size() != components) {
            throw new IOException("correlated-FD reference component count mismatch");
        }
        int samples = context.dataset().sampleCount();
        int walkers = context.dataset().walkerCount();

        double[] localEnergies = new double[samples];
        int[] chains = new int[samples];
        double[][] kineticDirectional = new double[components][samples];
        double[][] coulombDirectional = new double[components][samples];
        double[][] wavefunctionLog = new double[components][samples];
        double[][] jacobianHalfDivergence = new double[components][samples];
        boolean[][] finite = new boolean[components][samples];

        SampleInput[] inputs = new SampleInput[samples];
        FermiNetCorrelatedFdConfigurationFile.forEach(
                context.configurationFile(), walkers,
                (sample, chain, retained, coordinates) ->
                        inputs[sample] = new SampleInput(
                                sample, chain, retained, coordinates));
        List<FermiNetStateAccess.NuclearDirection> nuclearDirections =
                nuclearDirections(molecule.nuclei().size());
        int parallelism = derivativeEngine.sampleParallelism();
        if (parallelism == 1) {
            for (SampleInput input : inputs) {
                evaluateSample(state, molecule, derivativeEngine,
                        nuclearDirections, input, chains, localEnergies,
                        kineticDirectional, coulombDirectional,
                        wavefunctionLog, jacobianHalfDivergence, finite);
            }
        } else {
            ExecutorService executor = Executors.newFixedThreadPool(parallelism);
            try {
                List<Future<?>> futures = new ArrayList<>(inputs.length);
                for (SampleInput input : inputs) {
                    futures.add(executor.submit(() -> evaluateSample(
                            state, molecule, derivativeEngine,
                            nuclearDirections, input, chains, localEnergies,
                            kineticDirectional, coulombDirectional,
                            wavefunctionLog, jacobianHalfDivergence, finite)));
                }
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("SWCT sample evaluation interrupted",
                                exception);
                    } catch (ExecutionException exception) {
                        throw new IOException("SWCT sample evaluation failed",
                                exception.getCause());
                    }
                }
            } finally {
                executor.shutdownNow();
            }
        }

        double energySum = 0.0;
        int energyCount = 0;
        for (double localEnergy : localEnergies) {
            if (Double.isFinite(localEnergy)) {
                energySum += localEnergy;
                energyCount++;
            }
        }
        double meanLocalEnergy = energyCount > 0
                ? energySum / energyCount : Double.NaN;

        List<NuclearForceResult.Component> resultComponents = new ArrayList<>(components);
        List<NuclearForceResult.SwctComponentDiagnostics> diagnostics =
                new ArrayList<>(components);
        boolean anyNonfinite = energyCount < samples;
        int reducedComponents = 0;
        for (int component = 0; component < components; component++) {
            int nucleus = component / 3;
            int axis = component % 3;
            double[] forceSamples = new double[samples];
            double directSum = 0.0, kineticSum = 0.0, coulombSum = 0.0;
            double wavefunctionSum = 0.0, jacobianSum = 0.0, logSum = 0.0;
            int finiteCount = 0;
            for (int sample = 0; sample < samples; sample++) {
                if (!finite[component][sample]) {
                    forceSamples[sample] = Double.NaN;
                    continue;
                }
                double centered = localEnergies[sample] - meanLocalEnergy;
                double directionalLocal = kineticDirectional[component][sample]
                        + coulombDirectional[component][sample];
                double logJacobianState = wavefunctionLog[component][sample]
                        + jacobianHalfDivergence[component][sample];
                double direct = -directionalLocal;
                double wavefunctionTerm =
                        -2.0 * centered * wavefunctionLog[component][sample];
                double jacobianTerm =
                        -2.0 * centered * jacobianHalfDivergence[component][sample];
                double force = forceSample(localEnergies[sample],
                        meanLocalEnergy, directionalLocal, logJacobianState);
                if (!Double.isFinite(force)) {
                    forceSamples[sample] = Double.NaN;
                    continue;
                }
                forceSamples[sample] = force;
                directSum += direct;
                kineticSum += kineticDirectional[component][sample];
                coulombSum += coulombDirectional[component][sample];
                wavefunctionSum += wavefunctionTerm;
                jacobianSum += jacobianTerm;
                logSum += wavefunctionLog[component][sample]
                        + jacobianHalfDivergence[component][sample];
                finiteCount++;
            }
            anyNonfinite |= finiteCount < samples;
            var fd = reference.components().get(component);
            if (fd.nucleus() != nucleus || fd.axis() != axis) {
                throw new IOException("correlated-FD reference component ordering mismatch");
            }
            ComponentStatistics statistics = ComponentStatistics.compute(
                    forceSamples, chains, walkers, finiteCount);
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
                    tails(forceSamples, finiteCount, statistics.mean(),
                            Math.sqrt(statistics.variance())),
                    checksum(forceSamples), forceSamples));
            diagnostics.add(new NuclearForceResult.SwctComponentDiagnostics(
                    nucleus, axis,
                    finiteCount > 0 ? directSum / finiteCount : Double.NaN,
                    finiteCount > 0 ? kineticSum / finiteCount : Double.NaN,
                    finiteCount > 0 ? coulombSum / finiteCount : Double.NaN,
                    finiteCount > 0
                            ? (wavefunctionSum + jacobianSum) / finiteCount
                            : Double.NaN,
                    finiteCount > 0 ? wavefunctionSum / finiteCount : Double.NaN,
                    finiteCount > 0 ? jacobianSum / finiteCount : Double.NaN,
                    finiteCount > 0 ? logSum / finiteCount : Double.NaN,
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
                NuclearForceEstimatorType.SWCT, classification,
                context.parameterChecksum(), context.geometryIdentity(),
                context.dataset().sha256(), context.checkpointChecksum(),
                configuration.identity(), samples, walkers,
                context.dataset().retainedPerWalker(), resultComponents,
                new NuclearForceResult.SwctDiagnostics(
                        meanLocalEnergy, diagnostics));
    }

    private static void evaluateComponent(
            Molecule molecule,
            QuantumCoordinates coordinates,
            GeneralMolecularSpaceWarp.Weight[][] weights,
            FermiNetStateAccess.DirectionalSnapshot directional,
            int nucleus, int axis,
            double[][] kineticDirectional, double[][] coulombDirectional,
            double[][] wavefunctionLog, double[][] jacobianHalfDivergence,
            boolean[][] finite, int component, int sample) {
        double kinetic = -0.5 * directional.directionalLaplacianOverWavefunction();
        double coulomb = coulombDirectionalDerivative(molecule, coordinates,
                nucleus, axis, electronWeights(weights, nucleus));
        double halfDivergence = halfWarpDivergence(weights, nucleus, axis);
        double logDerivative = directional.directionalLogAbsoluteWavefunction();
        if (!Double.isFinite(kinetic) || !Double.isFinite(coulomb)
                || !Double.isFinite(halfDivergence)
                || !Double.isFinite(logDerivative)) {
            markNonfinite(kineticDirectional, coulombDirectional,
                    wavefunctionLog, jacobianHalfDivergence, finite,
                    component, sample);
            return;
        }
        kineticDirectional[component][sample] = kinetic;
        coulombDirectional[component][sample] = coulomb;
        wavefunctionLog[component][sample] = logDerivative;
        jacobianHalfDivergence[component][sample] = halfDivergence;
        finite[component][sample] = true;
    }

    private static void evaluateSample(
            FermiNetV1State state,
            Molecule molecule,
            FermiNetDerivativeEngine derivativeEngine,
            List<FermiNetStateAccess.NuclearDirection> nuclearDirections,
            SampleInput input,
            int[] chains,
            double[] localEnergies,
            double[][] kineticDirectional,
            double[][] coulombDirectional,
            double[][] wavefunctionLog,
            double[][] jacobianHalfDivergence,
            boolean[][] finite) {
        int sample = input.sample();
        QuantumCoordinates coordinates = input.coordinates();
        chains[sample] = input.chain();
        int components = 3 * molecule.nuclei().size();
        GeneralMolecularSpaceWarp.Weight[][] weights;
        FermiNetStateAccess.DirectionalBatchSnapshot derivativeBatch;
        try {
            weights = warpWeights(molecule, coordinates);
            derivativeBatch = derivativeEngine.directionalBatch(
                    state, coordinates, nuclearDirections,
                    electronDirections(weights));
        } catch (RuntimeException exception) {
            localEnergies[sample] = Double.NaN;
            for (int component = 0; component < components; component++) {
                markNonfinite(kineticDirectional, coulombDirectional,
                        wavefunctionLog, jacobianHalfDivergence, finite,
                        component, sample);
            }
            return;
        }
        double localEnergy;
        try {
            localEnergy = FermiNetRuntimeSampling.localEnergyWithLog(
                    state, coordinates, derivativeBatch.spatial())
                    .localEnergy().totalHartree();
        } catch (RuntimeException exception) {
            localEnergy = Double.NaN;
        }
        localEnergies[sample] = localEnergy;
        for (int nucleus = 0; nucleus < molecule.nuclei().size(); nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                int component = 3 * nucleus + axis;
                if (!Double.isFinite(localEnergy)) {
                    markNonfinite(kineticDirectional, coulombDirectional,
                            wavefunctionLog, jacobianHalfDivergence, finite,
                            component, sample);
                    continue;
                }
                try {
                    evaluateComponent(molecule, coordinates, weights,
                            derivativeBatch.directions().get(component),
                            nucleus, axis, kineticDirectional,
                            coulombDirectional, wavefunctionLog,
                            jacobianHalfDivergence, finite,
                            component, sample);
                } catch (RuntimeException exception) {
                    markNonfinite(kineticDirectional, coulombDirectional,
                            wavefunctionLog, jacobianHalfDivergence, finite,
                            component, sample);
                }
            }
        }
    }

    private static List<FermiNetStateAccess.NuclearDirection> nuclearDirections(
            int nuclei) {
        int components = 3 * nuclei;
        List<FermiNetStateAccess.NuclearDirection> result =
                new ArrayList<>(components);
        for (int component = 0; component < components; component++) {
            double[] direction = new double[components];
            direction[component] = 1.0;
            result.add(new FermiNetStateAccess.NuclearDirection(direction));
        }
        return List.copyOf(result);
    }

    private static List<FermiNetStateAccess.ElectronDirection> electronDirections(
            GeneralMolecularSpaceWarp.Weight[][] weights) {
        int components = 3 * weights[0].length;
        List<FermiNetStateAccess.ElectronDirection> result =
                new ArrayList<>(components);
        for (int component = 0; component < components; component++) {
            int nucleus = component / 3;
            int axis = component % 3;
            result.add(new FermiNetStateAccess.ElectronDirection(
                    electronDirection(weights, nucleus, axis)));
        }
        return List.copyOf(result);
    }

    private record SampleInput(
            int sample,
            int chain,
            int retained,
            QuantumCoordinates coordinates) {}

    private static void markNonfinite(
            double[][] kineticDirectional, double[][] coulombDirectional,
            double[][] wavefunctionLog, double[][] jacobianHalfDivergence,
            boolean[][] finite, int component, int sample) {
        kineticDirectional[component][sample] = Double.NaN;
        coulombDirectional[component][sample] = Double.NaN;
        wavefunctionLog[component][sample] = Double.NaN;
        jacobianHalfDivergence[component][sample] = Double.NaN;
        finite[component][sample] = false;
    }

    /** Locked normalized r^-4 space-warp weights for every electron/nucleus pair. */
    static GeneralMolecularSpaceWarp.Weight[][] warpWeights(
            Molecule molecule, QuantumCoordinates coordinates) {
        int electrons = coordinates.particles().size();
        int nuclei = molecule.nuclei().size();
        GeneralMolecularSpaceWarp.Weight[][] weights =
                new GeneralMolecularSpaceWarp.Weight[electrons][nuclei];
        for (int electron = 0; electron < electrons; electron++) {
            for (int nucleus = 0; nucleus < nuclei; nucleus++) {
                weights[electron][nucleus] = GeneralMolecularSpaceWarp.weight(
                        molecule, coordinates.particles().get(electron), nucleus);
            }
        }
        return weights;
    }

    /** Electron warp velocities for displacing one nucleus along one axis. */
    static double[] electronDirection(
            GeneralMolecularSpaceWarp.Weight[][] weights, int nucleus, int axis) {
        double[] direction = new double[3 * weights.length];
        for (int electron = 0; electron < weights.length; electron++) {
            direction[3 * electron + axis] = weights[electron][nucleus].value();
        }
        return direction;
    }

    /** Half the warp divergence, 1/2 sum_i div_i(w_i^nucleus), for one axis. */
    static double halfWarpDivergence(
            GeneralMolecularSpaceWarp.Weight[][] weights, int nucleus, int axis) {
        double divergence = 0.0;
        for (GeneralMolecularSpaceWarp.Weight[] electron : weights) {
            divergence += electron[nucleus].derivative(axis);
        }
        return 0.5 * divergence;
    }

    private static double[] electronWeights(
            GeneralMolecularSpaceWarp.Weight[][] weights, int nucleus) {
        double[] values = new double[weights.length];
        for (int electron = 0; electron < weights.length; electron++) {
            values[electron] = weights[electron][nucleus].value();
        }
        return values;
    }

    /** Born-Oppenheimer Coulomb potential (electron-nuclear + ee + nn), hartree. */
    static double coulombPotential(
            Molecule molecule, QuantumCoordinates coordinates) {
        double electronNuclear = 0.0, electronElectron = 0.0, nuclearNuclear = 0.0;
        var particles = coordinates.particles();
        for (var electron : particles) {
            for (var center : molecule.nuclei()) {
                var position = center.position().inBohr();
                electronNuclear -= center.charge().atomicNumber() / distance(
                        electron.xBohr(), electron.yBohr(), electron.zBohr(),
                        position.x(), position.y(), position.z());
            }
        }
        for (int i = 0; i < particles.size(); i++) {
            for (int j = i + 1; j < particles.size(); j++) {
                var a = particles.get(i);
                var b = particles.get(j);
                electronElectron += 1.0 / distance(
                        a.xBohr(), a.yBohr(), a.zBohr(),
                        b.xBohr(), b.yBohr(), b.zBohr());
            }
        }
        var nuclei = molecule.nuclei();
        for (int i = 0; i < nuclei.size(); i++) {
            for (int j = i + 1; j < nuclei.size(); j++) {
                var a = nuclei.get(i).position().inBohr();
                var b = nuclei.get(j).position().inBohr();
                nuclearNuclear += nuclei.get(i).charge().atomicNumber()
                        * nuclei.get(j).charge().atomicNumber()
                        / distance(a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
            }
        }
        return electronNuclear + electronElectron + nuclearNuclear;
    }

    /**
     * Total Coulomb-potential directional derivative: the bare derivative
     * with respect to the displaced nuclear coordinate plus the
     * warp-weighted electron derivatives, hartree/bohr.
     */
    static double coulombDirectionalDerivative(
            Molecule molecule, QuantumCoordinates coordinates,
            int nucleus, int axis, double[] electronWeights) {
        var nuclei = molecule.nuclei();
        var displaced = nuclei.get(nucleus);
        var origin = displaced.position().inBohr();
        double[] r = {origin.x(), origin.y(), origin.z()};
        double z = displaced.charge().atomicNumber();
        double bare = 0.0;
        var particles = coordinates.particles();
        for (int i = 0; i < particles.size(); i++) {
            var electron = particles.get(i);
            double[] e = {electron.xBohr(), electron.yBohr(), electron.zBohr()};
            double r3 = cubeDistance(e[0] - r[0], e[1] - r[1], e[2] - r[2]);
            bare += z * (r[axis] - e[axis]) / r3;
        }
        for (int other = 0; other < nuclei.size(); other++) {
            if (other == nucleus) continue;
            var position = nuclei.get(other).position().inBohr();
            double[] q = {position.x(), position.y(), position.z()};
            double r3 = cubeDistance(q[0] - r[0], q[1] - r[1], q[2] - r[2]);
            bare -= z * nuclei.get(other).charge().atomicNumber()
                    * (r[axis] - q[axis]) / r3;
        }
        double warped = 0.0;
        for (int i = 0; i < particles.size(); i++) {
            var electron = particles.get(i);
            double[] e = {electron.xBohr(), electron.yBohr(), electron.zBohr()};
            double derivative = 0.0;
            for (var center : nuclei) {
                var position = center.position().inBohr();
                double r3 = cubeDistance(e[0] - position.x(),
                        e[1] - position.y(), e[2] - position.z());
                derivative += center.charge().atomicNumber()
                        * (e[axis] - coordinate(position, axis)) / r3;
            }
            for (int j = 0; j < particles.size(); j++) {
                if (j == i) continue;
                var other = particles.get(j);
                double r3 = cubeDistance(e[0] - other.xBohr(),
                        e[1] - other.yBohr(), e[2] - other.zBohr());
                derivative -= (e[axis] - coordinate(other, axis)) / r3;
            }
            warped += electronWeights[i] * derivative;
        }
        return bare + warped;
    }

    private static double coordinate(
            totah.lab.prometheus.molecular.CartesianPosition position, int axis) {
        return switch (axis) {
            case 0 -> position.x();
            case 1 -> position.y();
            case 2 -> position.z();
            default -> throw new IllegalArgumentException("invalid Cartesian axis");
        };
    }

    private static double coordinate(
            QuantumCoordinates.ParticleCoordinate electron, int axis) {
        return switch (axis) {
            case 0 -> electron.xBohr();
            case 1 -> electron.yBohr();
            case 2 -> electron.zBohr();
            default -> throw new IllegalArgumentException("invalid Cartesian axis");
        };
    }

    private static double cubeDistance(double x, double y, double z) {
        double r2 = x * x + y * y + z * z;
        return r2 * Math.sqrt(r2);
    }

    private static double distance(
            double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(r > 1e-12)) {
            throw new IllegalArgumentException(
                    "Coulomb singularity at coincident charged particles");
        }
        return r;
    }

    /** Single-sample SWCT force: -D E_L - 2 (E_L - <E_L>) D log(J^1/2 |Psi|). */
    static double forceSample(
            double localEnergy, double meanLocalEnergy,
            double directionalLocalEnergy,
            double directionalLogJacobianState) {
        return -directionalLocalEnergy
                - 2.0 * (localEnergy - meanLocalEnergy)
                        * directionalLogJacobianState;
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
