package totah.lab.prometheus.neural.ferminet.drivers;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Canonical one-step H2O FermiNet sample-space SR driver.
 *
 * <p>Loads the frozen HF-pretrained state, measures an independent baseline,
 * performs exactly one matrix-free SR update, measures the updated state, writes
 * diagnostics, and stops. It never overwrites the pretraining artifacts.
 */
public final class FermiNetH2oSrDriver {

    private static final String EXPECTED_PARAMETER_CHECKSUM =
            "43a41da438fdcdccf1f6496db6af7848fda1f572bc657ea8037b399b6c12c16b";
    private static final String EXPECTED_GEOMETRY_IDENTITY =
            "2b5c454215a84de2cfacd6ce7cec2cf018b5b7ee6ab95267332f0fdc26421234";
    private static final String EXPECTED_HF_ARTIFACT_SHA256 =
            "2c2ae3854bf743124c8d3b9847095080f6c6f990f94cf73d030f64ee56912d89";
    private static final String EXPECTED_BRANCH_PARENT_PARAMETER_CHECKSUM =
            "dfa88d8f0714ea9f9cf45fd3f735a0b198f1f5eef42e6b0a96f2dc7e40341d20";
    private static final String HF_ARTIFACT_RESOURCE =
            "/totah/lab/prometheus/neural/h2o-uhf-ccpvdz.json";
    private static final int CANONICAL_WALKERS = 64;

    private static final int VMC_PARALLELISM = Math.max(1,
            Math.min(12, Runtime.getRuntime().availableProcessors()));
    private static final double MIN_ACCEPTANCE = 0.20;
    private static final double MAX_ACCEPTANCE = 0.90;

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private FermiNetH2oSrDriver() {}

    public static void main(String[] args) throws Exception {
        long driverStartedNanos = System.nanoTime();
        Arguments arguments = Arguments.parse(args);
        FermiNetH2oGeometryManifest.Entry geometry = arguments.geometryKey() == null
                ? FermiNetH2oGeometryManifest.require(
                        FermiNetH2oGeometryManifest.CANONICAL_KEY)
                : FermiNetH2oGeometryManifest.require(arguments.geometryKey());
        Molecule molecule = geometry.molecule();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.locked();
        FermiNetParameterLayout layout = new FermiNetParameterLayout(configuration, molecule);

        FermiNetOptimizationCheckpoint resumeCheckpoint = arguments.resumeCheckpoint() == null
                ? null : FermiNetOptimizationCheckpoint.read(arguments.resumeCheckpoint());
        FermiNetOptimizationCheckpoint branchParent = arguments.branchFromCheckpoint() == null
                ? null : FermiNetOptimizationCheckpoint.read(
                        arguments.branchFromCheckpoint());
        double[] parameterValues = resumeCheckpoint != null
                ? resumeCheckpoint.parameters()
                : branchParent != null
                        ? branchParent.parameters()
                        : readParameters(arguments.parameterFile(), layout.parameterCount());
        if (parameterValues.length != layout.parameterCount()) {
            throw new IllegalArgumentException("checkpoint parameter count mismatch");
        }

        FermiNetV1State initialState = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.fromArray(layout, parameterValues));

        BranchProvenance branch = branchParent == null ? null
                : verifyAndCreateBranchProvenance(
                        arguments.branchFromCheckpoint(), arguments.baselineSeed(),
                        geometry, initialState, branchParent);
        List<QuantumCoordinates> availableWalkers = resumeCheckpoint != null
                ? resumeCheckpoint.walkers()
                : branchParent != null
                        ? freshBranchWalkers(initialState, branch.walkerInitializationSeed())
                        : readWalkers(arguments.walkerFile(), molecule);
        if (branchParent != null) {
            verifyIdentity(FermiNetPretrainingQualification.parameterChecksum(initialState),
                    branch.parentParameterSha256(), "branch parent parameter checksum");
        } else if (resumeCheckpoint == null) {
            verifyProvenance(initialState, availableWalkers, hfArtifactSha256());
        } else {
            verifyResumeProvenance(initialState, resumeCheckpoint, hfArtifactSha256());
        }
        Files.createDirectories(arguments.outputDirectory());
        int requiredWalkers = arguments.sampleCount() / arguments.retainedPerWalker();
        if (availableWalkers.size() < requiredWalkers) {
            throw new IllegalArgumentException("walker artifact has " + availableWalkers.size()
                    + " walkers; configuration requires " + requiredWalkers);
        }
        List<QuantumCoordinates> pretrainedWalkers = List.copyOf(
                availableWalkers.subList(0, requiredWalkers));

        verifySamplingParity(initialState, pretrainedWalkers);

        System.out.printf(Locale.ROOT, """
                Prometheus canonical FermiNet H2O one-step SR
                -----------------------------------------
                preset                 : %s
                parameter input        : %s
                walker input           : %s
                output                 : %s
                resume checkpoint      : %s
                branch parent          : %s
                geometry key           : %s
                geometry identity      : %s
                iterations             : %d
                parameters             : %d
                walkers                : %d
                SR samples             : %d
                VMC implementation     : deterministic parallel
                VMC parallelism        : %d

                baseline VMC:
                  warmup sweeps        : %d
                  retained/walker      : %d
                  sweeps/retained      : %d
                  step size (bohr)     : %.8g
                  seed                 : %d

                SR:
                  learning rate        : %.8g
                  damping              : %.8g
                  max update norm      : %.8g
                  solver               : structured Jacobian-free sample-space Cholesky SR
                  observation parallel.: %d

                """,
                arguments.preset(),
                arguments.parameterFile(),
                arguments.walkerFile(),
                arguments.outputDirectory(),
                arguments.resumeCheckpoint(),
                arguments.branchFromCheckpoint(),
                geometry.key(),
                geometry.geometryIdentity(),
                arguments.iterations(),
                initialState.parameterCount(),
                pretrainedWalkers.size(),
                arguments.sampleCount(),
                VMC_PARALLELISM,
                arguments.warmupSweeps(),
                arguments.retainedPerWalker(),
                arguments.sweepsBetweenRetained(),
                arguments.stepSizeBohr(),
                branch == null ? arguments.baselineSeed() : branch.samplingSeed(),
                arguments.learningRate(),
                arguments.damping(),
                arguments.maxUpdateNorm(),
                arguments.observationParallelism());

        Instant started = Instant.now();

        FermiNetVariationalOptimizer.SamplingConfiguration samplingConfiguration =
                new FermiNetVariationalOptimizer.SamplingConfiguration(
                pretrainedWalkers.size(),
                arguments.warmupSweeps(),
                arguments.retainedPerWalker(),
                arguments.sweepsBetweenRetained(),
                arguments.stepSizeBohr(),
                branch == null ? arguments.baselineSeed() : branch.samplingSeed());
        FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration =
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        arguments.learningRate(),
                        arguments.damping(),
                        arguments.maxUpdateNorm(),
                        arguments.observationParallelism());

        if (arguments.iterations() > 1 || resumeCheckpoint != null || branch != null) {
            runPersistentTrajectory(arguments, initialState, pretrainedWalkers,
                    samplingConfiguration, srConfiguration, resumeCheckpoint, branch,
                    geometry.geometryIdentity(), driverStartedNanos);
            return;
        }

        System.out.println("Starting exactly ONE SR update...");

        FermiNetVariationalOptimizer.OptimizationIterationResult iteration;
        FermiNetOptimizationCheckpoint continuationCheckpoint;
        try (FermiNetVariationalOptimizer optimizer =
                     new FermiNetVariationalOptimizer(VMC_PARALLELISM)) {
            var checkpointed = optimizer.optimizeCheckpointed(
                    initialState, pretrainedWalkers, samplingConfiguration,
                    FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                            srConfiguration),
                    EXPECTED_PARAMETER_CHECKSUM, EXPECTED_GEOMETRY_IDENTITY, 1).get(0);
            iteration = checkpointed.result();
            continuationCheckpoint = checkpointed.checkpoint();
        }
        FermiNetRuntimeSampling.Result baseline = iteration.vmcResult();
        FermiNetMatrixFreeSrOptimizer.Result sr = iteration.exactSrResult();
        long baselineVmcNanos = iteration.vmcNanos();
        long srIterationNanos = iteration.updateNanos();
        if (baseline.samples().size() != arguments.sampleCount()) {
            throw new IllegalStateException("expected " + arguments.sampleCount()
                    + " SR samples but obtained " + baseline.samples().size());
        }

        EnergyStatistics baselineEnergy = energyStatistics(baseline.localEnergies());
        requireOperationalAcceptance(baseline.acceptance(), "baseline");

        System.out.printf(Locale.ROOT, """
                Baseline complete
                -----------------
                acceptance : %.6f
                energy     : %+.10f +/- %.10f Ha
                stddev     : %.10f Ha
                samples    : %d

                """,
                baseline.acceptance(),
                baselineEnergy.mean(),
                baselineEnergy.standardError(),
                baselineEnergy.standardDeviation(),
                baselineEnergy.count());

        FermiNetV1State updatedState = sr.state();
        verifyFiniteParameters(updatedState);
        verifySamplingParity(updatedState, baseline.samples());

        System.out.printf(Locale.ROOT, """
                SR solve complete
                -----------------
                SR initial energy      : %+.10f Ha
                gradient norm          : %.10e
                Cholesky solves        : %d
                relative true residual : %.10e
                sample evaluations     : %d
                raw update norm        : %.10e
                applied update norm    : %.10e
                update rescaled        : %s

                """,
                sr.initialEnergyHartree(),
                sr.gradientNorm(),
                sr.solverIterations(),
                sr.relativeTrueResidual(),
                sr.sampleEvaluations(),
                sr.rawUpdateNorm(),
                sr.appliedUpdateNorm(),
                sr.updateRescaled());

        /* 4. Independent post-SR VMC, from the latest retained walkers. */
        FermiNetRuntimeSampling.Request postConfiguration = new FermiNetRuntimeSampling.Request(
                baseline.samples().size(),
                arguments.warmupSweeps(),
                1,
                arguments.sweepsBetweenRetained(),
                arguments.stepSizeBohr(),
                arguments.postSrSeed());

        long postStartedNanos = System.nanoTime();
        FermiNetRuntimeSampling.Result post = sampleCanonicalVmc(
                updatedState,
                postConfiguration,
                baseline.samples(),
                VMC_PARALLELISM);
        long postSrVmcNanos = System.nanoTime() - postStartedNanos;

        EnergyStatistics postEnergy = energyStatistics(post.localEnergies());
        requireOperationalAcceptance(post.acceptance(), "post-SR");

        double deltaEnergy = postEnergy.mean() - baselineEnergy.mean();
        String direction = deltaEnergy < 0.0 ? "DOWN" : deltaEnergy > 0.0 ? "UP" : "UNCHANGED";
        Instant finished = Instant.now();

        /* 5. Persist pilot evidence; never touch the frozen pretraining artifacts. */
        long persistenceStartedNanos = System.nanoTime();
        writeParameters(arguments.outputDirectory().resolve("post-sr-parameters.hex"),
                FermiNetStateAccess.parameterSnapshot(updatedState));
        writeWalkers(arguments.outputDirectory().resolve("baseline-retained-walkers.csv"),
                baseline.samples());
        writeWalkers(arguments.outputDirectory().resolve("post-sr-retained-walkers.csv"),
                post.samples());
        writeEnergySamples(arguments.outputDirectory().resolve("baseline-local-energy-samples.csv"),
                baseline.localEnergies());
        writeEnergySamples(arguments.outputDirectory().resolve("post-sr-local-energy-samples.csv"),
                post.localEnergies());
        continuationCheckpoint.write(arguments.outputDirectory().resolve(
                "continuation-checkpoint.bin"));
        writeSummary(arguments, baseline, baselineEnergy, sr, post, postEnergy,
                deltaEnergy, direction, started, finished);
        long persistenceNanos = System.nanoTime() - persistenceStartedNanos;
        long totalDriverNanos = System.nanoTime() - driverStartedNanos;

        System.out.printf(Locale.ROOT, """
                FERMINET_H2O_SR_DRIVER_TIMING
                  baseline_vmc_ms=%.3f
                  sr_iteration_ms=%.3f
                  post_sr_vmc_ms=%.3f
                  persistence_ms=%.3f
                  total_driver_ms=%.3f

                """,
                milliseconds(baselineVmcNanos),
                milliseconds(srIterationNanos),
                milliseconds(postSrVmcNanos),
                milliseconds(persistenceNanos),
                milliseconds(totalDriverNanos));

        System.out.printf(Locale.ROOT, """
                H2O canonical one-step SR run complete.
                --------------------------------
                baseline acceptance     : %.6f
                baseline energy         : %+.10f +/- %.10f Ha

                post-SR acceptance      : %.6f
                post-SR energy          : %+.10f +/- %.10f Ha

                delta E (after-before)  : %+.10f Ha
                observed direction      : %s

                SR steps executed       : 1
                additional SR steps     : 0
                scientific acceptance   : NOT YET DETERMINED
                forces evaluated        : false

                STOP: inspect this result before any second SR update.
                """,
                baseline.acceptance(),
                baselineEnergy.mean(),
                baselineEnergy.standardError(),
                post.acceptance(),
                postEnergy.mean(),
                postEnergy.standardError(),
                deltaEnergy,
                direction);
    }

    private static void runPersistentTrajectory(
            Arguments arguments,
            FermiNetV1State initialState,
            List<QuantumCoordinates> initialWalkers,
            FermiNetVariationalOptimizer.SamplingConfiguration sampling,
            FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration,
            FermiNetOptimizationCheckpoint resumeCheckpoint,
            BranchProvenance branch,
            String geometryIdentity,
            long driverStartedNanos) throws IOException {

        System.out.printf(Locale.ROOT,
                "Starting one persistent EXACT_SR trajectory of %d iterations...%n",
                arguments.iterations());
        var optimization = FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                srConfiguration);
        List<FermiNetVariationalOptimizer.CheckpointedIteration> results;
        try (FermiNetVariationalOptimizer optimizer =
                     new FermiNetVariationalOptimizer(VMC_PARALLELISM)) {
            results = resumeCheckpoint == null
                    ? optimizer.optimizeCheckpointed(
                            initialState, initialWalkers, sampling, optimization,
                            branch == null ? EXPECTED_PARAMETER_CHECKSUM
                                    : branch.rootParameterSha256(),
                            geometryIdentity, arguments.iterations())
                    : optimizer.resume(initialState, resumeCheckpoint, sampling,
                            optimization, geometryIdentity,
                            arguments.iterations());
        }

        String expectedInputChecksum = resumeCheckpoint == null
                ? branch == null ? EXPECTED_PARAMETER_CHECKSUM
                        : branch.parentParameterSha256()
                : resumeCheckpoint.parameterChecksum();
        for (var checkpointed : results) {
            var iteration = checkpointed.result();
            if (iteration.optimizerType() != FermiNetOptimizerType.EXACT_SR) {
                throw new IllegalStateException("canonical trajectory did not use EXACT_SR");
            }
            String inputChecksum = FermiNetPretrainingQualification.parameterChecksum(
                    iteration.inputState());
            String outputChecksum = FermiNetPretrainingQualification.parameterChecksum(
                    iteration.updatedState());
            if (!expectedInputChecksum.equals(inputChecksum)) {
                throw new IllegalStateException("SR state-continuity checksum mismatch at iteration "
                        + iteration.iteration());
            }
            verifyFiniteParameters(iteration.updatedState());
            requireOperationalAcceptance(
                    iteration.vmcResult().acceptance(),
                    "iteration " + iteration.iteration());
            if (iteration.nextWalkers().size() != CANONICAL_WALKERS) {
                throw new IllegalStateException("persistent walker-count mismatch at iteration "
                        + iteration.iteration());
            }
            persistIteration(arguments.outputDirectory(), iteration,
                    inputChecksum, outputChecksum, checkpointed.checkpoint(), branch);
            printIteration(iteration, inputChecksum, outputChecksum);
            expectedInputChecksum = outputChecksum;
        }

        if (branch != null) {
            persistBranchProvenance(arguments.outputDirectory(), branch);
            evaluateFinalBranchEnergy(arguments.outputDirectory(), sampling,
                    results.get(results.size() - 1));
        }

        System.out.printf(Locale.ROOT, """
                FERMINET_H2O_SR_TRAJECTORY_TIMING
                  iterations=%d
                  total_driver_ms=%.3f

                Persistent EXACT_SR trajectory complete; no independent post-SR VMC was run.
                """,
                results.size(), milliseconds(System.nanoTime() - driverStartedNanos));
    }

    private static void persistIteration(
            Path outputRoot,
            FermiNetVariationalOptimizer.OptimizationIterationResult iteration,
            String inputChecksum,
            String outputChecksum,
            FermiNetOptimizationCheckpoint checkpoint,
            BranchProvenance branch) throws IOException {
        Path directory = outputRoot.resolve(String.format(
                Locale.ROOT, "iteration-%03d", iteration.iteration()));
        Files.createDirectories(directory);
        if (branch == null) {
            writeParameters(directory.resolve("input-parameters.hex"),
                    FermiNetStateAccess.parameterSnapshot(iteration.inputState()));
            writeParameters(directory.resolve("output-parameters.hex"),
                    FermiNetStateAccess.parameterSnapshot(iteration.updatedState()));
        }
        writeWalkers(directory.resolve("next-walkers.csv"), iteration.nextWalkers());
        writeEnergySamples(directory.resolve("local-energy-samples.csv"),
                iteration.vmcResult().localEnergies());
        checkpoint.write(directory.resolve("continuation-checkpoint.bin"));

        var energy = iteration.energyStatistics();
        var sr = iteration.exactSrResult();
        var timing = sr.timing();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schema", "prometheus-ferminet-h2o-exact-sr-iteration-v1");
        summary.put("iteration", iteration.iteration());
        summary.put("seed", iteration.seed());
        summary.put("optimizer", iteration.optimizerType().name());
        summary.put("input_parameter_checksum", inputChecksum);
        summary.put("output_parameter_checksum", outputChecksum);
        summary.put("next_walker_checksum", walkerChecksum(iteration.nextWalkers()));
        summary.put("walker_count", iteration.nextWalkers().size());
        summary.put("sample_count", energy.count());
        summary.put("mean_energy_hartree", energy.meanHartree());
        summary.put("standard_error_hartree", energy.standardErrorHartree());
        summary.put("standard_deviation_hartree", energy.standardDeviationHartree());
        summary.put("acceptance", iteration.vmcResult().acceptance());
        summary.put("gradient_norm", sr.gradientNorm());
        summary.put("raw_update_norm", sr.rawUpdateNorm());
        summary.put("applied_update_norm", sr.appliedUpdateNorm());
        summary.put("update_rescaled", sr.updateRescaled());
        summary.put("absolute_sr_residual", sr.absoluteTrueResidual());
        summary.put("relative_sr_residual", sr.relativeTrueResidual());
        summary.put("vmc_nanos", iteration.vmcNanos());
        summary.put("sr_nanos", iteration.updateNanos());
        summary.put("total_iteration_nanos", iteration.totalNanos());
        summary.put("statistics_construction_nanos", timing.observationConstructionNanos());
        summary.put("sample_space_solve_nanos", timing.sampleSpaceSolveNanos());
        summary.put("update_rescaling_nanos", timing.updateRescalingNanos());
        summary.put("new_state_construction_nanos", timing.newStateConstructionNanos());
        summary.put("local_energy_tails", localEnergyTails(
                iteration.vmcResult().localEnergies()));
        if (branch != null) {
            summary.put("execution_mode", "BRANCH_FROM");
            summary.put("parent_checkpoint_sha256", branch.parentCheckpointSha256());
            summary.put("parent_parameter_sha256", branch.parentParameterSha256());
            summary.put("parent_geometry_sha256", branch.parentGeometrySha256());
            summary.put("child_geometry_sha256", branch.childGeometrySha256());
            summary.put("geometry_key", branch.geometryKey());
            summary.put("branch_sampling_seed", branch.samplingSeed());
            summary.put("walker_initialization_seed", branch.walkerInitializationSeed());
            summary.put("branch_session_identity", branch.sessionIdentity());
        } else {
            summary.put("execution_mode", checkpoint.completedIterations() > 1
                    ? "RESUME_OR_CANONICAL_TRAJECTORY" : "CANONICAL");
        }
        JSON.writeValue(directory.resolve("iteration-summary.json").toFile(), summary);
    }

    private static void printIteration(
            FermiNetVariationalOptimizer.OptimizationIterationResult iteration,
            String inputChecksum,
            String outputChecksum) {
        var energy = iteration.energyStatistics();
        var sr = iteration.exactSrResult();
        System.out.printf(Locale.ROOT, """
                EXACT_SR iteration %d
                  input checksum       : %s
                  output checksum      : %s
                  energy               : %+.10f +/- %.10f Ha
                  standard deviation   : %.10f Ha
                  acceptance           : %.6f
                  gradient norm        : %.10e
                  raw update norm      : %.10e
                  applied update norm  : %.10e
                  clipped              : %s
                  relative residual    : %.10e
                  VMC ms               : %.3f
                  SR ms                : %.3f
                  total ms             : %.3f

                """,
                iteration.iteration(), inputChecksum, outputChecksum,
                energy.meanHartree(), energy.standardErrorHartree(),
                energy.standardDeviationHartree(), iteration.vmcResult().acceptance(),
                sr.gradientNorm(), sr.rawUpdateNorm(), sr.appliedUpdateNorm(),
                sr.updateRescaled(), sr.relativeTrueResidual(),
                milliseconds(iteration.vmcNanos()),
                milliseconds(iteration.updateNanos()),
                milliseconds(iteration.totalNanos()));
    }

    static BranchProvenance verifyAndCreateBranchProvenance(
            Path parentCheckpoint,
            long baselineSeed,
            FermiNetH2oGeometryManifest.Entry child,
            FermiNetV1State childState,
            FermiNetOptimizationCheckpoint parent) throws IOException {
        if (FermiNetH2oGeometryManifest.CANONICAL_KEY.equals(child.key())) {
            throw new IllegalArgumentException(
                    "BRANCH_FROM requires a displaced frozen geometry");
        }
        verifyIdentity(parent.parameterChecksum(),
                EXPECTED_BRANCH_PARENT_PARAMETER_CHECKSUM,
                "iteration-17 parent parameter checksum");
        verifyIdentity(parent.geometryIdentity(), EXPECTED_GEOMETRY_IDENTITY,
                "branch parent canonical geometry identity");
        verifyIdentity(parent.rootParameterChecksum(), EXPECTED_PARAMETER_CHECKSUM,
                "branch root pretrained parameter checksum");
        verifyIdentity(FermiNetPretrainingQualification.parameterChecksum(childState),
                parent.parameterChecksum(), "branch copied parameter checksum");
        verifyIdentity(FermiNetPretrainingQualification.geometryIdentity(
                childState.molecule()), child.geometryIdentity(),
                "branch child geometry identity");
        if (parent.completedIterations() != 18
                || parent.optimizerType() != FermiNetOptimizerType.EXACT_SR
                || parent.walkers().size() != CANONICAL_WALKERS) {
            throw new IllegalStateException(
                    "branch parent is not the qualified iteration-17 checkpoint");
        }
        long geometryBits = Long.parseUnsignedLong(
                child.geometryIdentity().substring(0, 16), 16);
        long samplingSeed = baselineSeed ^ geometryBits;
        long walkerSeed = samplingSeed ^ 0x6a09e667f3bcc909L;
        String checkpointSha = fileSha256(parentCheckpoint);
        MessageDigest identity = sha256();
        updateString(identity, checkpointSha);
        updateString(identity, child.geometryIdentity());
        updateLong(identity, samplingSeed);
        updateLong(identity, walkerSeed);
        return new BranchProvenance(
                parentCheckpoint, checkpointSha,
                parent.rootParameterChecksum(), parent.parameterChecksum(),
                parent.geometryIdentity(), child.geometryIdentity(), child.key(),
                samplingSeed, walkerSeed,
                java.util.HexFormat.of().formatHex(identity.digest()));
    }

    static List<QuantumCoordinates> freshBranchWalkers(
            FermiNetV1State state, long seed) {
        List<QuantumCoordinates> walkers = new ArrayList<>(CANONICAL_WALKERS);
        for (int attempt = 0; walkers.size() < CANONICAL_WALKERS; attempt++) {
            if (attempt >= 1024) {
                throw new IllegalStateException(
                        "unable to initialize 64 nonsingular branch walkers");
            }
            long candidateSeed = seed + attempt;
            var defaults = ReferenceFermiNetPretrainer.Configuration.referenceDefaults(
                    1, candidateSeed);
            var initialization = new ReferenceFermiNetPretrainer.Configuration(
                    0, 1, defaults.learningRate(),
                    defaults.moveWidthBohr(), defaults.initialWidthBohr(),
                    defaults.scfFraction(), candidateSeed);
            QuantumCoordinates candidate = new ReferenceFermiNetPretrainer()
                    .begin(state, initialization).walkers().getFirst();
            try {
                FermiNetStateAccess.sampling(state, candidate);
                FermiNetStateAccess.spatial(state, candidate);
                boolean legacyInitializationAccepted =
                        FermiNetStateAccess.orbitals(state, candidate)
                                .determinants().stream()
                                .allMatch(head -> head.sign() != 0
                                        && head.logMagnitude() >= Math.log(1.0e-14));
                if (!legacyInitializationAccepted) continue;
                walkers.add(candidate);
            } catch (IllegalArgumentException | IllegalStateException rejectedNode) {
                // Deterministically redraw initial walkers that lie on a nodal singularity.
            }
        }
        return List.copyOf(walkers);
    }

    private static void persistBranchProvenance(
            Path output, BranchProvenance branch) throws IOException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schema", "prometheus-ferminet-h2o-geometry-branch-v1");
        record.put("execution_mode", "BRANCH_FROM");
        record.put("parent_checkpoint", branch.parentCheckpoint().toString());
        record.put("parent_checkpoint_sha256", branch.parentCheckpointSha256());
        record.put("root_pretrained_parameter_sha256", branch.rootParameterSha256());
        record.put("parent_parameter_sha256", branch.parentParameterSha256());
        record.put("parent_geometry_sha256", branch.parentGeometrySha256());
        record.put("child_geometry_sha256", branch.childGeometrySha256());
        record.put("geometry_key", branch.geometryKey());
        record.put("branch_sampling_seed", branch.samplingSeed());
        record.put("walker_initialization_seed", branch.walkerInitializationSeed());
        record.put("branch_session_identity", branch.sessionIdentity());
        JSON.writeValue(output.resolve("branch-provenance.json").toFile(), record);
    }

    private static void evaluateFinalBranchEnergy(
            Path output,
            FermiNetVariationalOptimizer.SamplingConfiguration sampling,
            FermiNetVariationalOptimizer.CheckpointedIteration last) throws IOException {
        FermiNetV1State state = last.result().updatedState();
        FermiNetOptimizationCheckpoint checkpoint = last.checkpoint();
        var request = new FermiNetRuntimeSampling.Request(
                sampling.walkers(), sampling.warmupSweeps(),
                sampling.retainedPerWalker(), sampling.sweepsBetweenRetained(),
                sampling.stepSizeBohr(), sampling.baseSeed());
        long started = System.nanoTime();
        FermiNetRuntimeSampling.Continuation continuation;
        try (var session = FermiNetRuntimeSampling.resumeSession(
                state, request, checkpoint, VMC_PARALLELISM)) {
            continuation = session.sample(state, 0, sampling.retainedPerWalker(),
                    sampling.sweepsBetweenRetained());
        }
        requireOperationalAcceptance(continuation.result().acceptance(),
                "final branch energy");
        Path directory = output.resolve("final-energy");
        Files.createDirectories(directory);
        writeParameters(directory.resolve("parameters.hex"),
                FermiNetStateAccess.parameterSnapshot(state));
        writeWalkers(directory.resolve("retained-configurations.csv"),
                continuation.result().samples());
        writeEnergySamples(directory.resolve("local-energy-samples.csv"),
                continuation.result().localEnergies());
        EnergyStatistics energy = energyStatistics(continuation.result().localEnergies());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schema", "prometheus-ferminet-h2o-final-energy-v1");
        summary.put("parameter_checksum",
                FermiNetPretrainingQualification.parameterChecksum(state));
        summary.put("geometry_identity",
                FermiNetPretrainingQualification.geometryIdentity(state.molecule()));
        summary.put("sample_count", energy.count());
        summary.put("mean_energy_hartree", energy.mean());
        summary.put("standard_error_hartree", energy.standardError());
        summary.put("standard_deviation_hartree", energy.standardDeviation());
        summary.put("acceptance", continuation.result().acceptance());
        summary.put("warmup_sweeps", 0);
        summary.put("proposed", continuation.proposed());
        summary.put("accepted", continuation.accepted());
        summary.put("elapsed_nanos", System.nanoTime() - started);
        summary.put("local_energy_tails", localEnergyTails(
                continuation.result().localEnergies()));
        JSON.writeValue(directory.resolve("energy-summary.json").toFile(), summary);
    }

    private static Map<String, Object> localEnergyTails(
            List<LocalEnergyComponents> energies) {
        double[] values = energies.stream().mapToDouble(
                LocalEnergyComponents::totalHartree).sorted().toArray();
        double mean = java.util.Arrays.stream(values).average().orElseThrow();
        double variance = 0.0;
        for (double value : values) variance += (value - mean) * (value - mean);
        double standardDeviation = Math.sqrt(variance / values.length);
        int beyondFive = 0, beyondTen = 0;
        for (double value : values) {
            double deviation = Math.abs(value - mean);
            if (deviation > 5.0 * standardDeviation) beyondFive++;
            if (deviation > 10.0 * standardDeviation) beyondTen++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minimum", values[0]);
        result.put("percentile_0_1", percentile(values, 0.001));
        result.put("percentile_1", percentile(values, 0.01));
        result.put("median", percentile(values, 0.5));
        result.put("percentile_99", percentile(values, 0.99));
        result.put("percentile_99_9", percentile(values, 0.999));
        result.put("maximum", values[values.length - 1]);
        result.put("beyond_5_sigma", beyondFive);
        result.put("beyond_10_sigma", beyondTen);
        return result;
    }

    private static double percentile(double[] values, double probability) {
        double position = probability * (values.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return values[lower];
        return values[lower] + (position - lower) * (values[upper] - values[lower]);
    }

    static void verifyProvenance(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers,
            String hfArtifactSha256) {
        String parameterChecksum =
                FermiNetPretrainingQualification.parameterChecksum(state);
        verifyIdentity(parameterChecksum, EXPECTED_PARAMETER_CHECKSUM,
                "decoded parameter checksum");
        String geometryIdentity =
                FermiNetPretrainingQualification.geometryIdentity(state.molecule());
        verifyIdentity(geometryIdentity, EXPECTED_GEOMETRY_IDENTITY,
                "canonical geometry identity");
        verifyIdentity(hfArtifactSha256, EXPECTED_HF_ARTIFACT_SHA256,
                "HF artifact SHA-256");
        if (walkers.size() != CANONICAL_WALKERS) {
            throw new IllegalStateException("canonical walker count must be exactly "
                    + CANONICAL_WALKERS + ", found " + walkers.size());
        }
    }

    static void verifyResumeProvenance(
            FermiNetV1State state,
            FermiNetOptimizationCheckpoint checkpoint,
            String hfArtifactSha256) {
        verifyIdentity(checkpoint.rootParameterChecksum(),
                EXPECTED_PARAMETER_CHECKSUM, "root pretrained parameter checksum");
        verifyIdentity(FermiNetPretrainingQualification.parameterChecksum(state),
                checkpoint.parameterChecksum(), "checkpoint parameter checksum");
        verifyIdentity(FermiNetOptimizationCheckpoint.walkerChecksum(
                        checkpoint.walkers()),
                checkpoint.walkerChecksum(), "checkpoint walker checksum");
        verifyIdentity(FermiNetPretrainingQualification.geometryIdentity(state.molecule()),
                checkpoint.geometryIdentity(), "checkpoint geometry identity");
        verifyIdentity(checkpoint.geometryIdentity(), EXPECTED_GEOMETRY_IDENTITY,
                "canonical geometry identity");
        verifyIdentity(hfArtifactSha256, EXPECTED_HF_ARTIFACT_SHA256,
                "HF artifact SHA-256");
        if (checkpoint.walkers().size() != CANONICAL_WALKERS) {
            throw new IllegalStateException("canonical walker count must be exactly "
                    + CANONICAL_WALKERS);
        }
        if (checkpoint.optimizerType() != FermiNetOptimizerType.EXACT_SR) {
            throw new IllegalStateException("resume checkpoint optimizer is not EXACT_SR");
        }
    }

    static void verifyIdentity(String actual, String expected, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(label + " mismatch: " + actual);
        }
    }

    private static String hfArtifactSha256() throws IOException {
        try (InputStream input = FermiNetH2oSrDriver.class.getResourceAsStream(
                HF_ARTIFACT_RESOURCE)) {
            if (input == null) {
                throw new IOException("missing HF artifact resource: " + HF_ARTIFACT_RESOURCE);
            }
            MessageDigest digest = sha256();
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) {
                digest.update(buffer, 0, count);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        }
    }

    private static String walkerChecksum(List<QuantumCoordinates> walkers) {
        MessageDigest digest = sha256();
        for (QuantumCoordinates walker : walkers) {
            for (var particle : walker.particles()) {
                updateLong(digest, particle.particleIndex());
                updateLong(digest, particle.spin().ordinal());
                updateLong(digest, Double.doubleToRawLongBits(particle.xBohr()));
                updateLong(digest, Double.doubleToRawLongBits(particle.yBohr()));
                updateLong(digest, Double.doubleToRawLongBits(particle.zBohr()));
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static String fileSha256(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void updateLong(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        updateLong(digest, bytes.length);
        digest.update(bytes);
    }

    static FermiNetRuntimeSampling.Result sampleCanonicalVmc(
            FermiNetV1State state,
            FermiNetRuntimeSampling.Request configuration,
            List<QuantumCoordinates> initialWalkers,
            int parallelism) {
        return FermiNetRuntimeSampling.sampleParallel(
                state, configuration, initialWalkers, parallelism);
    }

    static int canonicalVmcParallelism() {
        return VMC_PARALLELISM;
    }

    private static double milliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }

    private static void requireOperationalAcceptance(double acceptance, String stage) {
        if (!Double.isFinite(acceptance)
                || acceptance < MIN_ACCEPTANCE
                || acceptance > MAX_ACCEPTANCE) {
            throw new IllegalStateException(stage
                    + " VMC acceptance outside pilot-operational range: " + acceptance);
        }
    }

    private static void verifyFiniteParameters(FermiNetV1State state) {
        double[] parameters = FermiNetStateAccess.parameterSnapshot(state);
        for (int i = 0; i < parameters.length; i++) {
            if (!Double.isFinite(parameters[i])) {
                throw new IllegalStateException("non-finite updated parameter at index " + i);
            }
        }
    }

    private static void verifySamplingParity(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers) {

        int checks = Math.min(4, walkers.size());
        for (int i = 0; i < checks; i++) {
            QuantumCoordinates coordinates = walkers.get(i);
            var fast = FermiNetStateAccess.sampling(state, coordinates);
            var full = FermiNetStateAccess.spatial(state, coordinates);
            if (fast.sign() != full.sign()) {
                throw new IllegalStateException("sampling/spatial sign mismatch at walker " + i);
            }
            double delta = Math.abs(
                    fast.logAbsoluteWavefunction() - full.logAbsoluteWavefunction());
            if (delta > 1.0e-12) {
                throw new IllegalStateException(
                        "sampling/spatial log|Psi| mismatch at walker " + i + ": " + delta);
            }
        }
    }

    private static EnergyStatistics energyStatistics(List<LocalEnergyComponents> energies) {
        if (energies.size() < 2) {
            throw new IllegalArgumentException("at least two local-energy samples are required");
        }
        double sum = 0.0;
        for (LocalEnergyComponents energy : energies) {
            double value = energy.totalHartree();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("non-finite local energy");
            }
            sum += value;
        }
        double mean = sum / energies.size();
        double sumSquared = 0.0;
        for (LocalEnergyComponents energy : energies) {
            double delta = energy.totalHartree() - mean;
            sumSquared += delta * delta;
        }
        double variance = sumSquared / (energies.size() - 1);
        double standardDeviation = Math.sqrt(variance);
        double standardError = standardDeviation / Math.sqrt(energies.size());
        return new EnergyStatistics(energies.size(), mean, standardDeviation, standardError);
    }

    private static double[] readParameters(Path file, int expectedCount) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("missing pretrained parameters: " + file);
        }
        double[] values = new double[expectedCount];
        boolean[] seen = new boolean[expectedCount];
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] fields = trimmed.split("\\s+");
            if (fields.length != 2) {
                throw new IOException("invalid pretrained parameter line: " + line);
            }
            int index;
            double value;
            try {
                index = Integer.parseInt(fields[0]);
                value = Double.parseDouble(fields[1]);
            } catch (NumberFormatException exception) {
                throw new IOException("invalid pretrained parameter line: " + line, exception);
            }
            if (index < 0 || index >= expectedCount || seen[index]) {
                throw new IOException("invalid/duplicate parameter index: " + index);
            }
            if (!Double.isFinite(value)) {
                throw new IOException("non-finite pretrained parameter: " + index);
            }
            values[index] = value;
            seen[index] = true;
        }
        for (int i = 0; i < expectedCount; i++) {
            if (!seen[i]) throw new IOException("missing pretrained parameter index: " + i);
        }
        return values;
    }

    private static List<QuantumCoordinates> readWalkers(Path file, Molecule molecule)
            throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("missing pretrained walkers: " + file);
        }
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) throw new IOException("pretrained walker artifact is empty");

        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> grouped = new LinkedHashMap<>();
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex).trim();
            if (line.isEmpty()) continue;
            String[] fields = line.split(",");
            if (fields.length != 6) throw new IOException("invalid walker CSV line: " + line);
            try {
                int walker = Integer.parseInt(fields[0]);
                int electron = Integer.parseInt(fields[1]);
                SpinProjection spin = SpinProjection.valueOf(fields[2]);
                double x = Double.parseDouble(fields[3]);
                double y = Double.parseDouble(fields[4]);
                double z = Double.parseDouble(fields[5]);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    throw new IOException("non-finite walker coordinate");
                }
                grouped.computeIfAbsent(walker, ignored -> new ArrayList<>())
                        .add(new QuantumCoordinates.ParticleCoordinate(electron, x, y, z, spin));
            } catch (IllegalArgumentException exception) {
                throw new IOException("invalid walker CSV line: " + line, exception);
            }
        }
        if (grouped.isEmpty()) throw new IOException("pretrained walker artifact is empty");

        List<QuantumCoordinates> result = new ArrayList<>();
        int expectedWalker = 0;
        for (var entry : grouped.entrySet()) {
            if (entry.getKey() != expectedWalker++) {
                throw new IOException("walker indices must be contiguous from zero");
            }
            List<QuantumCoordinates.ParticleCoordinate> particles = entry.getValue();
            particles.sort(Comparator.comparingInt(
                    QuantumCoordinates.ParticleCoordinate::particleIndex));
            if (particles.size() != molecule.electrons().value()) {
                throw new IOException("walker electron count mismatch");
            }
            for (int i = 0; i < particles.size(); i++) {
                var particle = particles.get(i);
                if (particle.particleIndex() != i) {
                    throw new IOException("walker particle ordering mismatch");
                }
                SpinProjection expected = i < molecule.spin().alphaElectrons()
                        ? SpinProjection.ALPHA : SpinProjection.BETA;
                if (particle.spin() != expected) {
                    throw new IOException("walker spin ordering mismatch");
                }
            }
            result.add(new QuantumCoordinates(particles));
        }
        return result;
    }

    private static void writeParameters(Path file, double[] parameters) throws IOException {
        StringBuilder text = new StringBuilder("# index value_hex\n");
        for (int i = 0; i < parameters.length; i++) {
            text.append(i).append(' ').append(Double.toHexString(parameters[i])).append('\n');
        }
        Files.writeString(file, text.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void writeWalkers(Path file, List<QuantumCoordinates> walkers)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "sample,electron,spin,x_bohr_hex,y_bohr_hex,z_bohr_hex\n");
        for (int sample = 0; sample < walkers.size(); sample++) {
            for (var electron : walkers.get(sample).particles()) {
                csv.append(sample).append(',')
                        .append(electron.particleIndex()).append(',')
                        .append(electron.spin()).append(',')
                        .append(Double.toHexString(electron.xBohr())).append(',')
                        .append(Double.toHexString(electron.yBohr())).append(',')
                        .append(Double.toHexString(electron.zBohr())).append('\n');
            }
        }
        Files.writeString(file, csv.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void writeEnergySamples(Path file, List<LocalEnergyComponents> energies)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "sample,kinetic_hartree,electron_nuclear_hartree,"
                        + "electron_electron_hartree,nuclear_nuclear_hartree,total_hartree\n");
        for (int i = 0; i < energies.size(); i++) {
            LocalEnergyComponents energy = energies.get(i);
            csv.append(i).append(',')
                    .append(Double.toHexString(energy.kineticHartree())).append(',')
                    .append(Double.toHexString(energy.electronNuclearHartree())).append(',')
                    .append(Double.toHexString(energy.electronElectronHartree())).append(',')
                    .append(Double.toHexString(energy.nuclearNuclearHartree())).append(',')
                    .append(Double.toHexString(energy.totalHartree())).append('\n');
        }
        Files.writeString(file, csv.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void writeSummary(
            Arguments arguments,
            FermiNetRuntimeSampling.Result baseline,
            EnergyStatistics baselineEnergy,
            FermiNetMatrixFreeSrOptimizer.Result sr,
            FermiNetRuntimeSampling.Result post,
            EnergyStatistics postEnergy,
            double deltaEnergy,
            String direction,
            Instant started,
            Instant finished) throws IOException {

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schema", "prometheus-ferminet-h2o-sr-v2");
        summary.put("started_utc", started.toString());
        summary.put("finished_utc", finished.toString());
        summary.put("preset", arguments.preset());
        summary.put("parameter_input", arguments.parameterFile().toString());
        summary.put("walker_input", arguments.walkerFile().toString());
        summary.put("parameter_count", sr.state().parameterCount());
        summary.put("sr_steps_executed", 1);
        summary.put("additional_sr_steps_executed", 0);

        Map<String, Object> baselineMap = new LinkedHashMap<>();
        baselineMap.put("acceptance", baseline.acceptance());
        baselineMap.put("samples", baselineEnergy.count());
        baselineMap.put("mean_energy_hartree", baselineEnergy.mean());
        baselineMap.put("sample_standard_deviation_hartree", baselineEnergy.standardDeviation());
        baselineMap.put("naive_standard_error_hartree", baselineEnergy.standardError());
        summary.put("baseline", baselineMap);

        Map<String, Object> srMap = new LinkedHashMap<>();
        srMap.put("learning_rate", arguments.learningRate());
        srMap.put("damping", arguments.damping());
        srMap.put("max_update_norm", arguments.maxUpdateNorm());
        srMap.put("solver", "STRUCTURED_JACOBIAN_FREE_SAMPLE_SPACE_CHOLESKY_SR");
        srMap.put("observation_parallelism", arguments.observationParallelism());
        srMap.put("gradient_norm", sr.gradientNorm());
        srMap.put("raw_update_norm", sr.rawUpdateNorm());
        srMap.put("applied_update_norm", sr.appliedUpdateNorm());
        srMap.put("update_rescaled", sr.updateRescaled());
        srMap.put("solver_iterations", sr.solverIterations());
        srMap.put("relative_true_residual", sr.relativeTrueResidual());
        srMap.put("sample_evaluations", sr.sampleEvaluations());
        srMap.put("sr_sample_mean_energy_hartree", sr.initialEnergyHartree());
        summary.put("sr", srMap);

        Map<String, Object> postMap = new LinkedHashMap<>();
        postMap.put("acceptance", post.acceptance());
        postMap.put("samples", postEnergy.count());
        postMap.put("mean_energy_hartree", postEnergy.mean());
        postMap.put("sample_standard_deviation_hartree", postEnergy.standardDeviation());
        postMap.put("naive_standard_error_hartree", postEnergy.standardError());
        summary.put("post_sr", postMap);

        summary.put("delta_energy_hartree", deltaEnergy);
        summary.put("observed_energy_direction", direction);
        summary.put("scientifically_accepted", false);
        summary.put("forces_evaluated", false);
        summary.put("continue_sr_automatically", false);

        JSON.writeValue(arguments.outputDirectory().resolve("sr-summary.json").toFile(), summary);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-v1-water",
                List.of(
                        new NuclearCenter(0, "O", new NuclearCharge(8),
                                new CartesianPosition(0.0, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(1, "H", new NuclearCharge(1),
                                new CartesianPosition(1.7952398191849366, 0.0, 0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(2, "H", new NuclearCharge(1),
                                new CartesianPosition(-0.46464225035067114,
                                        1.7340684963325879, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }

    private record EnergyStatistics(
            int count,
            double mean,
            double standardDeviation,
            double standardError) {}

    record BranchProvenance(
            Path parentCheckpoint,
            String parentCheckpointSha256,
            String rootParameterSha256,
            String parentParameterSha256,
            String parentGeometrySha256,
            String childGeometrySha256,
            String geometryKey,
            long samplingSeed,
            long walkerInitializationSeed,
            String sessionIdentity) {}

    private record Arguments(
            String preset,
            Path parameterFile,
            Path walkerFile,
            Path outputDirectory,
            Path resumeCheckpoint,
            Path branchFromCheckpoint,
            String geometryKey,
            int iterations,
            int sampleCount,
            int retainedPerWalker,
            int warmupSweeps,
            int sweepsBetweenRetained,
            double stepSizeBohr,
            long baselineSeed,
            long postSrSeed,
            double learningRate,
            double damping,
            double maxUpdateNorm,
            int observationParallelism) {

        private static Arguments parse(String[] args) {
            String preset = "qualified-hf-n1024";
            Path repositoryRoot = Path.of("/Users/yazan/totah-lab");
            Path parameters = repositoryRoot.resolve(
                    "artifacts/prometheus/h2o/ferminet/pretraining-qualification/"
                            + "checkpoint-008000-20260818/pretrained-parameters.hex");
            Path walkers = repositoryRoot.resolve(
                    "artifacts/prometheus/h2o/ferminet/pretraining-qualification/"
                            + "checkpoint-008000-20260818/pretrained-walkers.csv");
            Path output = repositoryRoot.resolve(
                    "artifacts/prometheus/h2o/ferminet/sr/latest");
            Path resume = null;
            Path branchFrom = null;
            String geometry = null;
            int iterations = 1;
            int sampleCount = 1024;
            int retainedPerWalker = 16;
            int warmupSweeps = 100;
            int sweepsBetweenRetained = 10;
            double stepSizeBohr = 0.02;
            long baselineSeed = 20260818L;
            long postSrSeed = 20260819L;
            double learningRate = 0.01;
            double damping = 1.0;
            double maxUpdateNorm = 0.05;
            int observationParallelism = 12;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--preset" -> preset = value(args, ++i, "--preset");
                    case "--parameters" -> parameters = Path.of(value(args, ++i, "--parameters"));
                    case "--walkers" -> walkers = Path.of(value(args, ++i, "--walkers"));
                    case "--output" -> output = Path.of(value(args, ++i, "--output"));
                    case "--resume" -> resume = Path.of(value(args, ++i, "--resume"));
                    case "--branch-from" -> branchFrom = Path.of(
                            value(args, ++i, "--branch-from"));
                    case "--geometry" -> geometry = value(args, ++i, "--geometry");
                    case "--iterations" -> iterations = integer(args, ++i, "--iterations");
                    case "--sample-count" -> sampleCount = integer(args, ++i, "--sample-count");
                    case "--retained-per-walker" -> retainedPerWalker = integer(args, ++i, "--retained-per-walker");
                    case "--warmup-sweeps" -> warmupSweeps = integer(args, ++i, "--warmup-sweeps");
                    case "--sweeps-between-retained" -> sweepsBetweenRetained = integer(args, ++i, "--sweeps-between-retained");
                    case "--step-size-bohr" -> stepSizeBohr = decimal(args, ++i, "--step-size-bohr");
                    case "--baseline-seed" -> baselineSeed = number(args, ++i, "--baseline-seed");
                    case "--post-sr-seed" -> postSrSeed = number(args, ++i, "--post-sr-seed");
                    case "--learning-rate" -> learningRate = decimal(args, ++i, "--learning-rate");
                    case "--damping" -> damping = decimal(args, ++i, "--damping");
                    case "--max-update-norm" -> maxUpdateNorm = decimal(args, ++i, "--max-update-norm");
                    case "--observation-parallelism" -> observationParallelism = integer(args, ++i, "--observation-parallelism");
                    default -> throw usage("unknown argument: " + args[i]);
                }
            }
            if (resume != null && branchFrom != null) {
                throw usage("--resume and --branch-from are mutually exclusive");
            }
            if ((branchFrom == null) != (geometry == null)) {
                throw usage("--geometry and --branch-from must be specified together");
            }
            if (geometry != null) FermiNetH2oGeometryManifest.require(geometry);
            if (!"qualified-hf-n1024".equals(preset)) throw usage("unknown preset: " + preset);
            if (iterations < 1 || sampleCount < 2 || retainedPerWalker < 1
                    || sampleCount % retainedPerWalker != 0
                    || sampleCount / retainedPerWalker != CANONICAL_WALKERS
                    || warmupSweeps < 0 || sweepsBetweenRetained < 1
                    || !(stepSizeBohr > 0.0) || !Double.isFinite(stepSizeBohr)
                    || !(learningRate > 0.0) || !Double.isFinite(learningRate)
                    || !(damping > 0.0) || !Double.isFinite(damping)
                    || !(maxUpdateNorm > 0.0) || !Double.isFinite(maxUpdateNorm)
                    || observationParallelism < 1) {
                throw usage("invalid numeric configuration");
            }
            return new Arguments(preset,
                    parameters.toAbsolutePath().normalize(),
                    walkers.toAbsolutePath().normalize(),
                    output.toAbsolutePath().normalize(),
                    resume == null ? null : resume.toAbsolutePath().normalize(),
                    branchFrom == null ? null : branchFrom.toAbsolutePath().normalize(),
                    geometry,
                    iterations,
                    sampleCount, retainedPerWalker, warmupSweeps, sweepsBetweenRetained,
                    stepSizeBohr, baselineSeed, postSrSeed, learningRate, damping,
                    maxUpdateNorm, observationParallelism);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) throw usage(option + " requires a value");
            return args[index];
        }

        private static int integer(String[] args, int index, String option) {
            try {
                return Integer.parseInt(value(args, index, option));
            } catch (NumberFormatException exception) {
                throw usage(option + " requires an integer");
            }
        }

        private static long number(String[] args, int index, String option) {
            try {
                return Long.parseLong(value(args, index, option));
            } catch (NumberFormatException exception) {
                throw usage(option + " requires an integer");
            }
        }

        private static double decimal(String[] args, int index, String option) {
            try {
                return Double.parseDouble(value(args, index, option));
            } catch (NumberFormatException exception) {
                throw usage(option + " requires a number");
            }
        }

        private static IllegalArgumentException usage(String problem) {
            return new IllegalArgumentException(problem + System.lineSeparator() + """
                    --preset qualified-hf-n1024
                    --parameters PATH --walkers PATH --output PATH
                    [--resume CHECKPOINT]
                    [--geometry FROZEN_KEY --branch-from CHECKPOINT]
                    [--iterations N]
                    [--sample-count N] [--retained-per-walker N]
                    [--warmup-sweeps N] [--sweeps-between-retained N]
                    [--step-size-bohr X] [--baseline-seed N] [--post-sr-seed N]
                    [--learning-rate X] [--damping X] [--max-update-norm X]
                    [--observation-parallelism N]
                    """);
        }
    }
}
