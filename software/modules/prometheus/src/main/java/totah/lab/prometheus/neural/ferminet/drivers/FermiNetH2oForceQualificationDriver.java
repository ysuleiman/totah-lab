package totah.lab.prometheus.neural.ferminet.drivers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.force.FermiNetForceEvaluationContext;
import totah.lab.prometheus.neural.ferminet.force.FermiNetNuclearForcePipeline;
import totah.lab.prometheus.neural.ferminet.force.FermiNetNuclearForceValidation;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceConfiguration;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceEstimatorType;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeConfiguration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngineType;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizerType;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;

/** One canonical H2O qualification entry point for every FermiNet force plugin. */
public final class FermiNetH2oForceQualificationDriver {

    private static final Path DEFAULT_CHECKPOINT = Path.of(
            "artifacts/prometheus/h2o/ferminet/sr/"
                    + "qualified-best-7988-n1024-checkpointed-8step/iteration-017/"
                    + "continuation-checkpoint.bin");
    private static final Path DEFAULT_DATASET = Path.of(
            "artifacts/prometheus/h2o/ferminet/forces/"
                    + "correlated-fd-iteration-017-n1024/configurations.csv");
    private static final String EXPECTED_PARAMETERS =
            "dfa88d8f0714ea9f9cf45fd3f735a0b198f1f5eef42e6b0a96f2dc7e40341d20";
    private static final String EXPECTED_GEOMETRY =
            "2b5c454215a84de2cfacd6ce7cec2cf018b5b7ee6ab95267332f0fdc26421234";
    private static final String EXPECTED_ROOT_PARAMETERS =
            "43a41da438fdcdccf1f6496db6af7848fda1f572bc657ea8037b399b6c12c16b";
    private static final ObjectMapper JSON =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private FermiNetH2oForceQualificationDriver() {}

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        Molecule molecule = water();
        FermiNetOptimizationCheckpoint checkpoint =
                FermiNetOptimizationCheckpoint.read(arguments.checkpoint());
        verifyCheckpoint(checkpoint, molecule);
        var dataset = FermiNetCorrelatedFdConfigurationFile.inspect(
                arguments.dataset(), 64);
        if (dataset.sampleCount() != 1024 || dataset.retainedPerWalker() != 16) {
            throw new IllegalArgumentException("canonical H2O force dataset topology mismatch");
        }
        FermiNetV1Configuration network = FermiNetV1Configuration.locked();
        FermiNetParameterLayout layout = new FermiNetParameterLayout(network, molecule);
        FermiNetV1State state = new FermiNetV1State(molecule, network,
                FermiNetParameters.fromArray(layout, checkpoint.parameters()));
        var context = new FermiNetForceEvaluationContext(
                state, checkpoint.parameterChecksum(), checkpoint.geometryIdentity(),
                arguments.dataset(), dataset, sha256(arguments.checkpoint()),
                checkpoint.rootParameterChecksum());
        var verifiedContext = context.verified(arguments.checkpoint());
        var provenanceVerification = verifiedContext.provenance();
        NuclearForceConfiguration configuration = switch (arguments.estimator()) {
            case CORRELATED_FD -> NuclearForceConfiguration.correlatedFd(
                    FermiNetCorrelatedFiniteDifferenceForceReference.STEP_BOHR);
            case SWCT -> NuclearForceConfiguration.swct();
            case AC_ZV -> NuclearForceConfiguration.acZv();
            case AC_ZVZB -> NuclearForceConfiguration.acZvzb();
            case AC_ZVZB_DERIV -> arguments.pathakWagner()
                    ? NuclearForceConfiguration.acZvzbDerivPathakWagner(
                            0.100, 0.050, 0.020, 0.010, 0.005)
                    : NuclearForceConfiguration.acZvzbDeriv();
            default -> NuclearForceConfiguration.unsupported(arguments.estimator());
        };
        var derivativeConfiguration = new FermiNetDerivativeConfiguration(
                arguments.derivativeEngine(), arguments.forceParallelism());
        var result = new FermiNetNuclearForcePipeline().estimate(
                verifiedContext, configuration, derivativeConfiguration);
        var validation = FermiNetNuclearForceValidation.validate(molecule, result);
        var physicalDiagnostics = FermiNetNuclearForceValidation
                .physicalDiagnostics(molecule, result);
        Path output = arguments.output() == null
                ? Path.of("artifacts/prometheus/h2o/ferminet/forces")
                        .resolve(dataset.sha256()).resolve(arguments.estimator().name())
                : arguments.output();
        Files.createDirectories(output);
        Path resultFile = output.resolve("nuclear-force-result.json");
        if (Files.exists(resultFile)) {
            throw new IOException("refusing to overwrite force result: " + resultFile);
        }
        JSON.writeValue(resultFile.toFile(), new Artifact(result, validation,
                physicalDiagnostics, context.declaredProvenance(),
                provenanceVerification));
        System.out.printf(Locale.ROOT, """
                FERMINET H2O FORCE QUALIFICATION
                estimator             : %s
                parameter checksum     : %s
                geometry identity      : %s
                dataset checksum       : %s
                samples                : %d (%d chains x %d retained)
                derivative engine      : %s
                force parallelism      : %d
                classification         : %s
                output                 : %s
                """, result.estimatorType(), result.parameterChecksum(),
                result.geometryIdentity(), result.datasetChecksum(), result.sampleCount(),
                result.chainCount(), result.retainedPerChain(), arguments.derivativeEngine(),
                arguments.forceParallelism(), result.classification(), output);
    }

    private record Artifact(
            totah.lab.prometheus.neural.ferminet.force.NuclearForceResult result,
            FermiNetNuclearForceValidation.Result validation,
            FermiNetNuclearForceValidation.PhysicalDiagnostics physicalDiagnostics,
            FermiNetForceEvaluationContext.DeclaredProvenance declaredProvenance,
            FermiNetForceEvaluationContext.ProvenanceVerification
                    cryptographicVerification) {}

    private static void verifyCheckpoint(
            FermiNetOptimizationCheckpoint checkpoint, Molecule molecule) {
        if (!EXPECTED_PARAMETERS.equals(checkpoint.parameterChecksum())
                || !EXPECTED_GEOMETRY.equals(checkpoint.geometryIdentity())
                || !EXPECTED_GEOMETRY.equals(
                        FermiNetPretrainingQualification.geometryIdentity(molecule))
                || !EXPECTED_ROOT_PARAMETERS.equals(checkpoint.rootParameterChecksum())
                || checkpoint.completedIterations() != 18
                || checkpoint.optimizerType() != FermiNetOptimizerType.EXACT_SR
                || checkpoint.walkers().size() != 64) {
            throw new IllegalArgumentException("frozen H2O force checkpoint mismatch");
        }
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

    private record Arguments(
            NuclearForceEstimatorType estimator,
            Path checkpoint,
            Path dataset,
            Path output,
            boolean pathakWagner,
            FermiNetDerivativeEngineType derivativeEngine,
            int forceParallelism) {
        private static Arguments parse(String[] args) {
            NuclearForceEstimatorType estimator = NuclearForceEstimatorType.CORRELATED_FD;
            Path checkpoint = DEFAULT_CHECKPOINT, dataset = DEFAULT_DATASET, output = null;
            boolean pathakWagner = false;
            FermiNetDerivativeEngineType derivativeEngine =
                    FermiNetDerivativeEngineType.BATCHED_FORWARD;
            int forceParallelism = 6;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--estimator" -> estimator = NuclearForceEstimatorType.valueOf(
                            args[++i].toUpperCase(Locale.ROOT));
                    case "--checkpoint" -> checkpoint = Path.of(args[++i]);
                    case "--dataset" -> dataset = Path.of(args[++i]);
                    case "--output" -> output = Path.of(args[++i]);
                    case "--pathak-wagner" -> pathakWagner = true;
                    case "--derivative-engine" -> derivativeEngine =
                            FermiNetDerivativeEngineType.valueOf(
                                    args[++i].toUpperCase(Locale.ROOT));
                    case "--force-parallelism" -> forceParallelism =
                            Integer.parseInt(args[++i]);
                    default -> throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
            if (pathakWagner && estimator != NuclearForceEstimatorType.AC_ZVZB_DERIV) {
                throw new IllegalArgumentException(
                        "--pathak-wagner requires --estimator AC_ZVZB_DERIV");
            }
            return new Arguments(estimator, checkpoint, dataset, output,
                    pathakWagner, derivativeEngine, forceParallelism);
        }
    }
}
