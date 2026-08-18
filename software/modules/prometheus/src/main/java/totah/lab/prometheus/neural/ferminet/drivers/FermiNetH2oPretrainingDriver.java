package totah.lab.prometheus.neural.ferminet.drivers;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Production entry point for the locked H2O Hartree-Fock pretraining stage.
 *
 * <p>This driver performs exactly one scientific stage:
 *
 * <pre>
 * frozen H2O geometry
 *   -> frozen UHF/cc-pVDZ orbital target
 *   -> locked production FermiNet-v1
 *   -> 1000 iterations of reference HF pretraining
 *   -> immutable output artifacts
 * </pre>
 *
 * <p>It deliberately does NOT perform:
 *
 * <ul>
 *   <li>post-pretraining neural burn-in</li>
 *   <li>VMC local-energy evaluation</li>
 *   <li>SR/KFAC optimization</li>
 *   <li>force evaluation</li>
 * </ul>
 */
public final class FermiNetH2oPretrainingDriver {

    private static final String HF_RESOURCE =
            "/totah/lab/prometheus/neural/h2o-uhf-ccpvdz.json";

    private static final long DEFAULT_NETWORK_SEED =
            20260815L;

    private static final long DEFAULT_PRETRAINING_SEED =
            20260816L;

    private static final int DEFAULT_WALKERS =
            64;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);

    private FermiNetH2oPretrainingDriver() {
    }

    public static void main(
            String[] args)
            throws Exception {

        Arguments arguments =
                Arguments.parse(args);

        Files.createDirectories(
                arguments.outputDirectory());

        Molecule molecule =
                water();

        Path hfArtifact =
                resolveResource(
                        HF_RESOURCE);

        GaussianHartreeFockOrbitalTarget hfTarget =
                GaussianHartreeFockOrbitalTarget.read(
                        hfArtifact,
                        molecule);

        FermiNetV1Configuration networkConfiguration =
                FermiNetV1Configuration.locked();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        networkConfiguration,
                        molecule);

        FermiNetParameters initialParameters =
                FermiNetParameters.initialize(
                        layout,
                        arguments.networkSeed());

        FermiNetV1State initialState =
                new FermiNetV1State(
                        molecule,
                        networkConfiguration,
                        initialParameters);

        ReferenceFermiNetPretrainer.Configuration pretrainingConfiguration =
                ReferenceFermiNetPretrainer.Configuration.referenceDefaults(
                        arguments.walkers(),
                        arguments.pretrainingSeed());

        System.out.printf(
                Locale.ROOT,
                """
                Prometheus FermiNet H2O pretraining
                -----------------------------------
                output              : %s
                walkers             : %d
                iterations          : %d
                network parameters  : %d
                network seed        : %d
                pretraining seed    : %d
                learning rate       : %.8g
                RWM width (bohr)    : %.8g
                initial width (bohr): %.8g
                SCF fraction        : %.8g
                HF energy (Ha)      : %.15f
                HF basis            : %s
                HF generator        : %s
                FermiNet reference  : %s

                """,
                arguments.outputDirectory(),
                pretrainingConfiguration.walkers(),
                pretrainingConfiguration.iterations(),
                initialState.parameterCount(),
                arguments.networkSeed(),
                arguments.pretrainingSeed(),
                pretrainingConfiguration.learningRate(),
                pretrainingConfiguration.moveWidthBohr(),
                pretrainingConfiguration.initialWidthBohr(),
                pretrainingConfiguration.scfFraction(),
                hfTarget.provenance().scfEnergyHartree(),
                hfTarget.provenance().basis(),
                hfTarget.provenance().generator(),
                ReferenceFermiNetPretrainer.REFERENCE_COMMIT);

        Instant started =
                Instant.now();

        ReferenceFermiNetPretrainer pretrainer =
                new ReferenceFermiNetPretrainer();

        ReferenceFermiNetPretrainer.Result result =
                pretrainer.train(
                        initialState,
                        hfTarget,
                        pretrainingConfiguration,
                        (iteration, loss) -> {

                            /*
                             * Print every iteration early, then every 10
                             * iterations. The complete history is written
                             * after a successful run.
                             */
                            if (iteration <= 10
                                    || iteration % 10 == 0
                                    || iteration
                                    == pretrainingConfiguration.iterations()) {

                                System.out.printf(
                                        Locale.ROOT,
                                        "iteration %4d/%4d  orbital_mse=%.12e%n",
                                        iteration,
                                        pretrainingConfiguration.iterations(),
                                        loss);
                            }
                        });

        Instant finished =
                Instant.now();

        /*
         * Fail closed before persisting a scientifically unusable result.
         */
        validateResult(
                result,
                pretrainingConfiguration,
                layout.parameterCount());

        writeLossHistory(
                arguments.outputDirectory()
                        .resolve(
                                "pretraining-loss.csv"),
                result.lossHistory());

        writeParametersHex(
                arguments.outputDirectory()
                        .resolve(
                                "pretrained-parameters.hex"),
                FermiNetStateAccess.parameterSnapshot(result.state()));

        writeWalkers(
                arguments.outputDirectory()
                        .resolve(
                                "pretrained-walkers.csv"),
                result.walkers());

        writeSummary(
                arguments.outputDirectory()
                        .resolve(
                                "pretraining-summary.json"),
                arguments,
                molecule,
                layout,
                hfTarget,
                pretrainingConfiguration,
                result,
                started,
                finished);

        System.out.println();

        System.out.printf(
                Locale.ROOT,
                """
                H2O HF pretraining completed.
                final orbital MSE : %.12e
                RWM acceptance    : %.6f
                parameter count   : %d

                Artifacts:
                  %s
                  %s
                  %s
                  %s

                No post-pretraining burn-in, local-energy measurement, SR, or forces were run.
                """,
                result.lossHistory()
                        .get(
                                result.lossHistory().size() - 1),
                result.acceptance(),
                result.state().parameterCount(),
                arguments.outputDirectory()
                        .resolve("pretraining-loss.csv"),
                arguments.outputDirectory()
                        .resolve("pretrained-parameters.hex"),
                arguments.outputDirectory()
                        .resolve("pretrained-walkers.csv"),
                arguments.outputDirectory()
                        .resolve("pretraining-summary.json"));
    }

    private static void validateResult(
            ReferenceFermiNetPretrainer.Result result,
            ReferenceFermiNetPretrainer.Configuration configuration,
            int parameterCount) {

        if (result.lossHistory().size()
                != configuration.iterations()) {

            throw new IllegalStateException(
                    "incomplete pretraining loss history");
        }

        for (int iteration = 0;
             iteration < result.lossHistory().size();
             iteration++) {

            double loss =
                    result.lossHistory()
                            .get(iteration);

            if (!Double.isFinite(loss)
                    || loss < 0.0) {

                throw new IllegalStateException(
                        "invalid pretraining loss at iteration "
                                + (iteration + 1)
                                + ": "
                                + loss);
            }
        }

        if (result.state().parameterCount()
                != parameterCount) {

            throw new IllegalStateException(
                    "pretraining changed parameter-vector dimension");
        }

        for (double value : FermiNetStateAccess.parameterSnapshot(result.state())) {

            if (!Double.isFinite(value)) {

                throw new IllegalStateException(
                        "non-finite pretrained parameter");
            }
        }

        if (result.walkers().size()
                != configuration.walkers()) {

            throw new IllegalStateException(
                    "pretraining walker-count mismatch");
        }

        if (!Double.isFinite(
                result.acceptance())
                || result.acceptance() < 0.0
                || result.acceptance() > 1.0) {

            throw new IllegalStateException(
                    "invalid pretraining RWM acceptance: "
                            + result.acceptance());
        }

        if (!ReferenceFermiNetPretrainer.REFERENCE_COMMIT.equals(
                result.referenceCommit())) {

            throw new IllegalStateException(
                    "unexpected FermiNet reference commit");
        }
    }

    private static void writeLossHistory(
            Path output,
            List<Double> history)
            throws IOException {

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "iteration,orbital_mse\n");

        for (int i = 0;
             i < history.size();
             i++) {

            csv.append(i + 1)
                    .append(',')
                    .append(
                            Double.toHexString(
                                    history.get(i)))
                    .append('\n');
        }

        Files.writeString(
                output,
                csv.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * Hexadecimal doubles preserve every parameter bit exactly and are easy
     * for Java to reload with Double.valueOf/Double.parseDouble.
     */
    private static void writeParametersHex(
            Path output,
            double[] parameters)
            throws IOException {

        StringBuilder text =
                new StringBuilder();

        text.append(
                "# prometheus-ferminet-v1 pretrained parameters\n");

        text.append(
                        "# parameter_count=")
                .append(parameters.length)
                .append('\n');

        for (int i = 0;
             i < parameters.length;
             i++) {

            text.append(i)
                    .append('\t')
                    .append(
                            Double.toHexString(
                                    parameters[i]))
                    .append('\n');
        }

        Files.writeString(
                output,
                text.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeWalkers(
            Path output,
            List<QuantumCoordinates> walkers)
            throws IOException {

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "walker,electron,spin,x_bohr_hex,y_bohr_hex,z_bohr_hex\n");

        for (int walker = 0;
             walker < walkers.size();
             walker++) {

            QuantumCoordinates coordinates =
                    walkers.get(walker);

            for (var electron :
                    coordinates.particles()) {

                csv.append(walker)
                        .append(',')
                        .append(
                                electron.particleIndex())
                        .append(',')
                        .append(
                                electron.spin())
                        .append(',')
                        .append(
                                Double.toHexString(
                                        electron.xBohr()))
                        .append(',')
                        .append(
                                Double.toHexString(
                                        electron.yBohr()))
                        .append(',')
                        .append(
                                Double.toHexString(
                                        electron.zBohr()))
                        .append('\n');
            }
        }

        Files.writeString(
                output,
                csv.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeSummary(
            Path output,
            Arguments arguments,
            Molecule molecule,
            FermiNetParameterLayout layout,
            GaussianHartreeFockOrbitalTarget hfTarget,
            ReferenceFermiNetPretrainer.Configuration configuration,
            ReferenceFermiNetPretrainer.Result result,
            Instant started,
            Instant finished)
            throws IOException {

        Map<String, Object> summary =
                new LinkedHashMap<>();

        summary.put(
                "schema",
                "prometheus-ferminet-h2o-pretraining-v1");

        summary.put(
                "stage",
                "HF_PRETRAINING_ONLY");

        summary.put(
                "started_utc",
                started.toString());

        summary.put(
                "finished_utc",
                finished.toString());

        summary.put(
                "ferminet_reference_commit",
                ReferenceFermiNetPretrainer.REFERENCE_COMMIT);

        summary.put(
                "representation_id",
                FermiNetV1Configuration.REPRESENTATION_ID);

        summary.put(
                "molecule_id",
                molecule.moleculeId());

        summary.put(
                "molecular_charge",
                molecule.charge().elementaryCharges());

        summary.put(
                "alpha_electrons",
                molecule.spin().alphaElectrons());

        summary.put(
                "beta_electrons",
                molecule.spin().betaElectrons());

        List<Map<String, Object>> nuclei =
                new ArrayList<>();

        for (var nucleus :
                molecule.nuclei()) {

            var position =
                    nucleus.position()
                            .inBohr();

            Map<String, Object> row =
                    new LinkedHashMap<>();

            row.put(
                    "index",
                    nucleus.orderedIndex());

            row.put(
                    "element",
                    nucleus.element());

            row.put(
                    "nuclear_charge",
                    nucleus.charge()
                            .atomicNumber());

            row.put(
                    "xyz_bohr",
                    List.of(
                            position.x(),
                            position.y(),
                            position.z()));

            nuclei.add(
                    row);
        }

        summary.put(
                "nuclei",
                nuclei);

        summary.put(
                "parameter_count",
                layout.parameterCount());

        summary.put(
                "network_seed",
                arguments.networkSeed());

        summary.put(
                "pretraining_seed",
                arguments.pretrainingSeed());

        summary.put(
                "walkers",
                configuration.walkers());

        summary.put(
                "iterations",
                configuration.iterations());

        summary.put(
                "learning_rate",
                configuration.learningRate());

        summary.put(
                "move_width_bohr",
                configuration.moveWidthBohr());

        summary.put(
                "initial_width_bohr",
                configuration.initialWidthBohr());

        summary.put(
                "scf_fraction",
                configuration.scfFraction());

        summary.put(
                "hf_schema",
                "prometheus-hf-orbitals-v1");

        summary.put(
                "hf_basis",
                hfTarget.provenance()
                        .basis());

        summary.put(
                "hf_restricted",
                hfTarget.provenance()
                        .restricted());

        summary.put(
                "hf_scf_energy_hartree",
                hfTarget.provenance()
                        .scfEnergyHartree());

        summary.put(
                "hf_generator",
                hfTarget.provenance()
                        .generator());

        summary.put(
                "algorithm",
                result.algorithm());

        summary.put(
                "initial_loss",
                result.lossHistory()
                        .get(0));

        summary.put(
                "final_loss",
                result.lossHistory()
                        .get(
                                result.lossHistory().size() - 1));

        summary.put(
                "minimum_loss",
                result.lossHistory()
                        .stream()
                        .mapToDouble(
                                Double::doubleValue)
                        .min()
                        .orElseThrow());

        summary.put(
                "rwm_acceptance",
                result.acceptance());

        summary.put(
                "post_pretraining_burn_in_run",
                false);

        summary.put(
                "local_energy_evaluated",
                false);

        summary.put(
                "sr_started",
                false);

        summary.put(
                "forces_evaluated",
                false);

        OBJECT_MAPPER.writeValue(
                output.toFile(),
                summary);
    }

    private static Path resolveResource(
            String resource)
            throws IOException {

        try {

            var url =
                    FermiNetH2oPretrainingDriver.class
                            .getResource(
                                    resource);

            if (url == null) {

                throw new IOException(
                        "missing resource: "
                                + resource);
            }

            if (!"file".equalsIgnoreCase(
                    url.getProtocol())) {

                /*
                 * This driver is intended to be run from the Maven/IDE class
                 * path where test/main resources are materialized as files.
                 * Fail rather than silently copying an unknown resource.
                 */
                throw new IOException(
                        "HF resource is not a filesystem resource: "
                                + url);
            }

            return Path.of(
                    url.toURI());

        } catch (URISyntaxException exception) {

            throw new IOException(
                    "invalid HF resource URI",
                    exception);
        }
    }

    private static Molecule water() {

        return new Molecule(
                "ferminet-v1-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(8),
                                new CartesianPosition(
                                        0.0,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -0.46464225035067114,
                                        1.7340684963325879,
                                        0.0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(
                        5,
                        5,
                        1));
    }

    private record Arguments(
            Path outputDirectory,
            int walkers,
            long networkSeed,
            long pretrainingSeed) {

        private static Arguments parse(
                String[] args) {

            Path output =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-pretraining");

            int walkers =
                    DEFAULT_WALKERS;

            long networkSeed =
                    DEFAULT_NETWORK_SEED;

            long pretrainingSeed =
                    DEFAULT_PRETRAINING_SEED;

            for (int i = 0;
                 i < args.length;
                 i++) {

                switch (args[i]) {

                    case "--output" -> {

                        if (++i >= args.length) {
                            throw usage(
                                    "--output requires a path");
                        }

                        output =
                                Path.of(
                                        args[i]);
                    }

                    case "--walkers" -> {

                        if (++i >= args.length) {
                            throw usage(
                                    "--walkers requires an integer");
                        }

                        walkers =
                                Integer.parseInt(
                                        args[i]);

                        if (walkers < 1) {
                            throw usage(
                                    "--walkers must be positive");
                        }
                    }

                    case "--network-seed" -> {

                        if (++i >= args.length) {
                            throw usage(
                                    "--network-seed requires a long");
                        }

                        networkSeed =
                                Long.parseLong(
                                        args[i]);
                    }

                    case "--pretraining-seed" -> {

                        if (++i >= args.length) {
                            throw usage(
                                    "--pretraining-seed requires a long");
                        }

                        pretrainingSeed =
                                Long.parseLong(
                                        args[i]);
                    }

                    case "--help", "-h" ->
                            throw usage(
                                    null);

                    default ->
                            throw usage(
                                    "unknown argument: "
                                            + args[i]);
                }
            }

            return new Arguments(
                    output
                            .toAbsolutePath()
                            .normalize(),
                    walkers,
                    networkSeed,
                    pretrainingSeed);
        }

        private static IllegalArgumentException usage(
                String problem) {

            String message =
                    """
                    Usage:
                      FermiNetH2oPretrainingDriver
                        [--output PATH]
                        [--walkers N]
                        [--network-seed LONG]
                        [--pretraining-seed LONG]

                    Defaults:
                      walkers          = 64
                      network seed     = 20260815
                      pretraining seed = 20260816
                    """;

            if (problem != null) {

                message =
                        problem
                                + System.lineSeparator()
                                + System.lineSeparator()
                                + message;
            }

            return new IllegalArgumentException(
                    message);
        }
    }
}
