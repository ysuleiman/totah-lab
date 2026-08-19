package totah.lab.prometheus.neural.ferminet.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizerType;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetRuntimeSampling;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;

/** Produces the preregistered N=1024 correlated-FD reference for frozen H2O. */
public final class FermiNetH2oCorrelatedFiniteDifferenceForceDriver {

    private static final Path DEFAULT_CHECKPOINT = Path.of(
            "artifacts/prometheus/h2o/ferminet/sr/"
                    + "qualified-best-7988-n1024-checkpointed-8step/iteration-017/"
                    + "continuation-checkpoint.bin");
    private static final Path DEFAULT_OUTPUT = Path.of(
            "artifacts/prometheus/h2o/ferminet/forces/correlated-fd-iteration-017-n1024");
    private static final String EXPECTED_PARAMETERS =
            "dfa88d8f0714ea9f9cf45fd3f735a0b198f1f5eef42e6b0a96f2dc7e40341d20";
    private static final String EXPECTED_WALKERS =
            "6121af13862bf5853206a99d8416779d60aada7e508044b06b6ea760322077c6";
    private static final String EXPECTED_RANDOM =
            "dc8715a225e3361bdcaab38654ed741e1aa864dfed4d1c4d181679cea743b8fb";
    private static final String EXPECTED_GEOMETRY =
            "2b5c454215a84de2cfacd6ce7cec2cf018b5b7ee6ab95267332f0fdc26421234";
    private static final String EXPECTED_ROOT_PARAMETERS =
            "43a41da438fdcdccf1f6496db6af7848fda1f572bc657ea8037b399b6c12c16b";
    private static final int WALKERS = 64;
    private static final int RETAINED_PER_WALKER = 16;
    private static final int VMC_PARALLELISM = 1;
    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private FermiNetH2oCorrelatedFiniteDifferenceForceDriver() {}

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        FermiNetOptimizationCheckpoint checkpoint =
                FermiNetOptimizationCheckpoint.read(arguments.checkpoint());
        Molecule molecule = water();
        verifyCheckpoint(checkpoint, molecule);
        FermiNetV1Configuration configuration = FermiNetV1Configuration.locked();
        FermiNetParameterLayout layout = new FermiNetParameterLayout(configuration, molecule);
        FermiNetV1State state = new FermiNetV1State(molecule, configuration,
                FermiNetParameters.fromArray(layout, checkpoint.parameters()));

        Files.createDirectories(arguments.output());
        Path configurationFile = arguments.output().resolve("configurations.csv");
        long samplingStarted = System.nanoTime();
        FermiNetCorrelatedFdConfigurationFile.Identity dataset;
        if (Files.exists(configurationFile)) {
            dataset = FermiNetCorrelatedFdConfigurationFile.inspect(
                    configurationFile, WALKERS);
        } else {
            FermiNetRuntimeSampling.Request request = new FermiNetRuntimeSampling.Request(
                    WALKERS, 100, RETAINED_PER_WALKER, 10, 0.02, 20260818L);
            FermiNetRuntimeSampling.CoordinateContinuation continuation;
            try (FermiNetRuntimeSampling.Session session =
                         FermiNetRuntimeSampling.resumeSession(
                                 state, request, checkpoint, VMC_PARALLELISM)) {
                List<totah.lab.prometheus.variational.QuantumCoordinates> samples =
                        new ArrayList<>(WALKERS * RETAINED_PER_WALKER);
                long proposed = 0L, accepted = 0L;
                for (int retained = 0; retained < RETAINED_PER_WALKER; retained++) {
                    var segment = session.sampleCoordinates(state, 0, 1, 10);
                    samples.addAll(segment.samples());
                    proposed += segment.proposed();
                    accepted += segment.accepted();
                    System.gc();
                }
                continuation = new FermiNetRuntimeSampling.CoordinateContinuation(
                        samples, (double) accepted / proposed, proposed, accepted);
            }
            dataset = FermiNetCorrelatedFdConfigurationFile.write(
                    configurationFile, continuation.samples(), WALKERS);
            writeBatchManifest(arguments, checkpoint, dataset, continuation);
        }
        var reread = FermiNetCorrelatedFdConfigurationFile.inspect(
                configurationFile, WALKERS);
        if (!dataset.equals(reread) || reread.sampleCount() != 1024
                || reread.walkerCount() != 64 || reread.retainedPerWalker() != 16) {
            throw new IllegalStateException("persisted configuration identity mismatch");
        }
        long samplingNanos = System.nanoTime() - samplingStarted;

        Path resultFile = arguments.output().resolve("correlated-fd-reference.json");
        if (Files.exists(resultFile)) {
            throw new IOException("refusing to overwrite correlated-FD reference: " + resultFile);
        }
        long forceStarted = System.nanoTime();
        var result = new FermiNetCorrelatedFiniteDifferenceForceReference().evaluate(
                state, configurationFile, WALKERS);
        long forceNanos = System.nanoTime() - forceStarted;
        JSON.writeValue(resultFile.toFile(), result);
        writeComponents(arguments.output().resolve("component-summary.csv"), result);
        writeRaw(arguments.output().resolve("raw-force-samples.csv"), result);
        writeProvenance(arguments, checkpoint, result, samplingNanos, forceNanos);

        System.out.printf(Locale.ROOT, """
                FERMINET H2O CORRELATED-FD REFERENCE
                SOURCE WAVEFUNCTION   : frozen iteration-17 checkpoint
                FORCE SAMPLE DATASET  : new deterministic N=1024 continuation batch
                parameter checksum    : %s
                configuration checksum: %s
                paired samples        : %d (%d chains x %d retained)
                delta (bohr)           : %.10g
                dataset stage seconds : %.3f
                force seconds         : %.3f
                output                : %s
                """, checkpoint.parameterChecksum(), result.dataset().sha256(),
                result.dataset().sampleCount(), result.dataset().walkerCount(),
                result.dataset().retainedPerWalker(), result.stepBohr(),
                samplingNanos / 1.0e9, forceNanos / 1.0e9, arguments.output());
    }

    private static void verifyCheckpoint(
            FermiNetOptimizationCheckpoint checkpoint, Molecule molecule) {
        require(EXPECTED_PARAMETERS, checkpoint.parameterChecksum(), "parameters");
        require(EXPECTED_WALKERS, checkpoint.walkerChecksum(), "walkers");
        require(EXPECTED_RANDOM, checkpoint.randomStateChecksum(), "RNG state");
        require(EXPECTED_GEOMETRY, checkpoint.geometryIdentity(), "checkpoint geometry");
        require(EXPECTED_GEOMETRY,
                FermiNetPretrainingQualification.geometryIdentity(molecule),
                "runtime geometry");
        require(EXPECTED_ROOT_PARAMETERS, checkpoint.rootParameterChecksum(),
                "root parameters");
        if (checkpoint.completedIterations() != 18
                || checkpoint.optimizerType() != FermiNetOptimizerType.EXACT_SR
                || checkpoint.walkers().size() != WALKERS) {
            throw new IllegalArgumentException("unexpected iteration-17 checkpoint provenance");
        }
    }

    private static void require(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(label + " identity mismatch: " + actual);
        }
    }

    private static void writeBatchManifest(
            Arguments arguments, FermiNetOptimizationCheckpoint checkpoint,
            FermiNetCorrelatedFdConfigurationFile.Identity dataset,
            FermiNetRuntimeSampling.CoordinateContinuation continuation) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schema", "prometheus-ferminet-correlated-fd-configurations-v1");
        values.put("source_wavefunction", "frozen iteration-17 checkpoint");
        values.put("force_sample_dataset",
                "new deterministic N=1024 continuation batch");
        values.put("configuration_file", "configurations.csv");
        values.put("batch_sha256", dataset.sha256());
        values.put("root_checkpoint", arguments.checkpoint().toAbsolutePath().normalize());
        values.put("root_checkpoint_sha256", sha256(arguments.checkpoint()));
        values.put("parameter_sha256", checkpoint.parameterChecksum());
        values.put("walker_start_sha256", checkpoint.walkerChecksum());
        values.put("rng_start_sha256", checkpoint.randomStateChecksum());
        values.put("sampling_configuration_sha256",
                checkpoint.samplingConfigurationIdentity());
        values.put("geometry_sha256", checkpoint.geometryIdentity());
        values.put("samples", dataset.sampleCount());
        values.put("chains", dataset.walkerCount());
        values.put("retained_per_chain", dataset.retainedPerWalker());
        values.put("warmup_sweeps", 0);
        values.put("sweeps_between_retained", 10);
        values.put("step_size_bohr", 0.02);
        values.put("proposals", continuation.proposed());
        values.put("accepted", continuation.accepted());
        values.put("acceptance", continuation.acceptance());
        JSON.writeValue(arguments.output().resolve(
                "configuration-manifest.json").toFile(), values);
    }

    private static void writeComponents(
            Path path, FermiNetCorrelatedFiniteDifferenceForceReference.Result result)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            writer.write("nucleus,axis,E_plus,E_minus,covariance,force,chain_se,"
                    + "naive_se,variance,ess_plus,ess_minus,paired_ess,"
                    + "plus_geometry_sha256,minus_geometry_sha256\n");
            for (var value : result.components()) {
                writer.write(String.format(Locale.ROOT,
                        "%d,%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,"
                                + "%.17g,%.17g,%.17g,%s,%s%n",
                        value.nucleus(), value.axisName(), value.energyPlusHartree(),
                        value.energyMinusHartree(), value.energyContributionCovariance(),
                        value.forceHartreePerBohr(), value.forceStandardError(),
                        value.naiveIndependentSampleStandardError(), value.forceVariance(),
                        value.plusEffectiveSampleSize(), value.minusEffectiveSampleSize(),
                        value.pairedEffectiveSampleSize(), value.plusGeometryChecksum(),
                        value.minusGeometryChecksum()));
            }
        }
    }

    private static void writeRaw(
            Path path, FermiNetCorrelatedFiniteDifferenceForceReference.Result result)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            writer.write("nucleus,axis,sample,chain,retained,force_hartree_per_bohr\n");
            for (var component : result.components()) {
                double[] samples = component.rawForceSamples();
                for (int sample = 0; sample < samples.length; sample++) {
                    writer.write(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%.17g%n",
                            component.nucleus(), component.axisName(), sample,
                            sample % result.dataset().walkerCount(),
                            sample / result.dataset().walkerCount(), samples[sample]));
                }
            }
        }
    }

    private static void writeProvenance(
            Arguments arguments, FermiNetOptimizationCheckpoint checkpoint,
            FermiNetCorrelatedFiniteDifferenceForceReference.Result result,
            long samplingNanos, long forceNanos) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("source_wavefunction", "frozen iteration-17 checkpoint");
        values.put("force_sample_dataset",
                "new deterministic N=1024 continuation batch");
        values.put("checkpoint_sha256", sha256(arguments.checkpoint()));
        values.put("parameter_sha256", checkpoint.parameterChecksum());
        values.put("walker_start_sha256", checkpoint.walkerChecksum());
        values.put("rng_start_sha256", checkpoint.randomStateChecksum());
        values.put("root_parameter_sha256", checkpoint.rootParameterChecksum());
        values.put("geometry_sha256", checkpoint.geometryIdentity());
        values.put("optimizer", checkpoint.optimizerType());
        values.put("configuration_dataset_sha256", result.dataset().sha256());
        values.put("delta_bohr", result.stepBohr());
        values.put("delta_source",
                "NUCLEAR_FORCE_ESTIMATOR_CAPABILITY_PROTOCOL_LOCKED.md");
        values.put("samples", result.dataset().sampleCount());
        values.put("chains", result.dataset().walkerCount());
        values.put("retained_per_chain", result.dataset().retainedPerWalker());
        values.put("sampling_or_reread_ms", samplingNanos / 1.0e6);
        values.put("force_evaluation_ms", forceNanos / 1.0e6);
        values.put("parameters_reoptimized", false);
        values.put("common_electron_configurations", true);
        values.put("clipping", false);
        values.put("displacement_convergence_study", false);
        JSON.writeValue(arguments.output().resolve("provenance.json").toFile(), values);
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Molecule water() {
        return new Molecule("ferminet-v1-water", List.of(
                new NuclearCenter(0, "O", new NuclearCharge(8),
                        new CartesianPosition(0.0, 0.0, 0.0, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(1.7952398191849366, 0.0, 0.0,
                                LengthUnit.BOHR)),
                new NuclearCenter(2, "H", new NuclearCharge(1),
                        new CartesianPosition(-0.46464225035067114,
                                1.7340684963325879, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(10), new SpinSector(5, 5, 1));
    }

    private record Arguments(Path checkpoint, Path output) {
        private static Arguments parse(String[] args) {
            Path checkpoint = DEFAULT_CHECKPOINT, output = DEFAULT_OUTPUT;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--checkpoint" -> checkpoint = Path.of(args[++i]);
                    case "--output" -> output = Path.of(args[++i]);
                    default -> throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
            return new Arguments(checkpoint, output);
        }
    }
}
