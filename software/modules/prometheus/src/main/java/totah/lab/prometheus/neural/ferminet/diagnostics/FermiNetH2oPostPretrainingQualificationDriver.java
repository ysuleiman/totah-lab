package totah.lab.prometheus.neural.ferminet.diagnostics;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
 * H2O post-HF-pretraining diagnostic using the canonical FermiNetVmc engine.
 *
 * <p>This driver intentionally contains no independent Metropolis sampler and
 * no independent Hamiltonian/local-energy implementation. All scientific
 * sampling and local-energy evaluation are delegated to {@link FermiNetVmc}.
 *
 * <p>This stage is diagnostic only. It can establish that the sampler is
 * operational and that a catastrophic pre-SR energy failure is absent. It does
 * not scientifically qualify the H2O energy, does not start SR, and does not
 * evaluate forces.
 */
public final class FermiNetH2oPostPretrainingQualificationDriver {

    private static final int DEFAULT_BURN_IN_SWEEPS =
            100;

    private static final int DEFAULT_SWEEPS_BETWEEN_RETAINED =
            10;

    private static final int DEFAULT_RETAINED_PER_WALKER =
            1;

    private static final double DEFAULT_STEP_SIZE_BOHR =
            0.02;

    private static final long DEFAULT_SEED =
            20260817L;

    /*
     * Diagnostic sampler-operability window only.
     */
    private static final double MIN_ACCEPTANCE =
            0.20;

    private static final double MAX_ACCEPTANCE =
            0.90;

    /*
     * Broad catastrophic-failure screen only.
     *
     * Passing this range MUST NOT be reported as scientific energy
     * qualification.
     */
    private static final double MIN_SANITY_ENERGY_HARTREE =
            -80.0;

    private static final double MAX_SANITY_ENERGY_HARTREE =
            -65.0;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);

    private FermiNetH2oPostPretrainingQualificationDriver() {
    }

    public static void main(
            String[] args)
            throws Exception {

        Arguments arguments =
                Arguments.parse(
                        args);

        Files.createDirectories(
                arguments.outputDirectory());

        Molecule molecule =
                water();

        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.locked();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        double[] parameterValues =
                readParameters(
                        arguments.pretrainingDirectory()
                                .resolve(
                                        "pretrained-parameters.hex"),
                        layout.parameterCount());

        FermiNetV1State state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        FermiNetParameters.fromArray(
                                layout,
                                parameterValues));

        List<QuantumCoordinates> initialWalkers =
                readWalkers(
                        arguments.pretrainingDirectory()
                                .resolve(
                                        "pretrained-walkers.csv"),
                        molecule);

        /*
         * Production guard: prove that the fast value-only path used by
         * FermiNetVmc is the same wavefunction as spatialEvaluation().
         */
        verifySamplingParity(
                state,
                initialWalkers);

        FermiNetRuntimeSampling.Request vmcConfiguration =
                new FermiNetRuntimeSampling.Request(
                        initialWalkers.size(),
                        arguments.burnInSweeps(),
                        arguments.retainedPerWalker(),
                        arguments.sweepsBetweenRetained(),
                        arguments.stepSizeBohr(),
                        arguments.seed());

        int expectedSamples =
                Math.multiplyExact(
                        initialWalkers.size(),
                        arguments.retainedPerWalker());

        System.out.printf(
                Locale.ROOT,
                """
                Prometheus FermiNet H2O post-pretraining diagnostic
                --------------------------------------------------
                canonical VMC       : FermiNetVmc
                pretraining input   : %s
                output              : %s
                walkers             : %d
                parameters          : %d
                burn-in sweeps      : %d
                step size (bohr)    : %.8g
                sweeps/retained     : %d
                retained/walker     : %d
                retained samples    : %d
                seed                : %d

                """,
                arguments.pretrainingDirectory(),
                arguments.outputDirectory(),
                initialWalkers.size(),
                state.parameterCount(),
                arguments.burnInSweeps(),
                arguments.stepSizeBohr(),
                arguments.sweepsBetweenRetained(),
                arguments.retainedPerWalker(),
                expectedSamples,
                arguments.seed());

        Instant started =
                Instant.now();

        /*
         * Canonical scientific path.
         *
         * No private RWM.
         * No private acceptance function.
         * No private local-energy implementation.
         */
        FermiNetRuntimeSampling.Result result =
                FermiNetRuntimeSampling.sampleSerial(
                        state, vmcConfiguration, initialWalkers);

        Instant finished =
                Instant.now();

        if (result.samples().size()
                != expectedSamples) {

            throw new IllegalStateException(
                    "unexpected retained sample count: "
                            + result.samples().size()
                            + " expected="
                            + expectedSamples);
        }

        if (result.localEnergies().size()
                != expectedSamples) {

            throw new IllegalStateException(
                    "unexpected local-energy count: "
                            + result.localEnergies().size()
                            + " expected="
                            + expectedSamples);
        }

        Statistics kinetic =
                statistics(
                        result.localEnergies()
                                .stream()
                                .mapToDouble(
                                        LocalEnergyComponents::kineticHartree)
                                .toArray());

        Statistics electronNuclear =
                statistics(
                        result.localEnergies()
                                .stream()
                                .mapToDouble(
                                        LocalEnergyComponents::electronNuclearHartree)
                                .toArray());

        Statistics electronElectron =
                statistics(
                        result.localEnergies()
                                .stream()
                                .mapToDouble(
                                        LocalEnergyComponents::electronElectronHartree)
                                .toArray());

        Statistics nuclearNuclear =
                statistics(
                        result.localEnergies()
                                .stream()
                                .mapToDouble(
                                        LocalEnergyComponents::nuclearNuclearHartree)
                                .toArray());

        Statistics total =
                statistics(
                        result.localEnergies()
                                .stream()
                                .mapToDouble(
                                        LocalEnergyComponents::totalHartree)
                                .toArray());

        boolean samplingOperational =
                Double.isFinite(
                        result.acceptance())
                        && result.acceptance()
                        >= MIN_ACCEPTANCE
                        && result.acceptance()
                        <= MAX_ACCEPTANCE;

        boolean catastrophicEnergyFailureAbsent =
                Double.isFinite(
                        total.mean())
                        && total.mean()
                        >= MIN_SANITY_ENERGY_HARTREE
                        && total.mean()
                        <= MAX_SANITY_ENERGY_HARTREE;

        String classification;

        if (!samplingOperational) {

            classification =
                    "SAMPLER_NOT_OPERATIONAL";

        } else if (!catastrophicEnergyFailureAbsent) {

            classification =
                    "CATASTROPHIC_PRE_SR_ENERGY_FAILURE";

        } else {

            classification =
                    "PRE_SR_STATE_OPERATIONAL_NOT_ENERGY_QUALIFIED";
        }

        writeEnergySamples(
                arguments.outputDirectory()
                        .resolve(
                                "local-energy-samples.csv"),
                result.localEnergies());

        writeWalkers(
                arguments.outputDirectory()
                        .resolve(
                                "retained-walkers.csv"),
                result.samples());

        writeSummary(
                arguments,
                result,
                kinetic,
                electronNuclear,
                electronElectron,
                nuclearNuclear,
                total,
                samplingOperational,
                catastrophicEnergyFailureAbsent,
                classification,
                started,
                finished);

        System.out.println();

        System.out.printf(
                Locale.ROOT,
                """
                Post-pretraining H2O diagnostic complete.
                -----------------------------------------
                canonical VMC              : FermiNetVmc
                VMC acceptance             : %.6f
                retained samples           : %d

                kinetic                    : %+.10f +/- %.10f Ha
                electron-nuclear           : %+.10f +/- %.10f Ha
                electron-electron          : %+.10f +/- %.10f Ha
                nuclear-nuclear            : %+.10f +/- %.10f Ha
                total                      : %+.10f +/- %.10f Ha

                sampling operational       : %s
                catastrophic energy absent : %s
                classification             : %s
                energy scientifically qualified : false

                SR was NOT started.
                Forces were NOT evaluated.
                """,
                result.acceptance(),
                result.samples().size(),
                kinetic.mean(),
                kinetic.standardError(),
                electronNuclear.mean(),
                electronNuclear.standardError(),
                electronElectron.mean(),
                electronElectron.standardError(),
                nuclearNuclear.mean(),
                nuclearNuclear.standardError(),
                total.mean(),
                total.standardError(),
                samplingOperational,
                catastrophicEnergyFailureAbsent,
                classification);

        if (!samplingOperational) {

            throw new IllegalStateException(
                    "canonical neural sampler is not operational; "
                            + "SR remains gated");
        }

        if (!catastrophicEnergyFailureAbsent) {

            throw new IllegalStateException(
                    "catastrophic pre-SR energy failure remains; "
                            + "SR remains gated");
        }
    }

    private static void verifySamplingParity(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers) {

        int checks =
                Math.min(
                        4,
                        walkers.size());

        for (int i = 0;
             i < checks;
             i++) {

            QuantumCoordinates coordinates =
                    walkers.get(
                            i);

            var sampling =
                    FermiNetStateAccess.sampling(state,
                            coordinates);

            var spatial =
                    FermiNetStateAccess.spatial(state,
                            coordinates);

            if (sampling.sign()
                    != spatial.sign()) {

                throw new IllegalStateException(
                        "sampling/spatial sign mismatch on production walker "
                                + i);
            }

            double delta =
                    Math.abs(
                            sampling.logAbsoluteWavefunction()
                                    - spatial.logAbsoluteWavefunction());

            if (delta > 1.0e-12) {

                throw new IllegalStateException(
                        "sampling/spatial log|Psi| mismatch on production walker "
                                + i
                                + ": "
                                + delta);
            }
        }
    }

    /*
     * =====================================================================
     * Input artifacts
     * =====================================================================
     */

    private static double[] readParameters(
            Path file,
            int expectedCount)
            throws IOException {

        if (!Files.isRegularFile(
                file)) {

            throw new IOException(
                    "missing pretrained parameters: "
                            + file);
        }

        double[] values =
                new double[
                        expectedCount];

        boolean[] seen =
                new boolean[
                        expectedCount];

        for (String line :
                Files.readAllLines(
                        file)) {

            String trimmed =
                    line.trim();

            if (trimmed.isEmpty()
                    || trimmed.startsWith(
                    "#")) {

                continue;
            }

            String[] fields =
                    trimmed.split(
                            "\\s+");

            if (fields.length != 2) {

                throw new IOException(
                        "invalid pretrained parameter line: "
                                + line);
            }

            int index;
            double value;

            try {

                index =
                        Integer.parseInt(
                                fields[0]);

                value =
                        Double.parseDouble(
                                fields[1]);

            } catch (NumberFormatException exception) {

                throw new IOException(
                        "invalid pretrained parameter line: "
                                + line,
                        exception);
            }

            if (index < 0
                    || index >= expectedCount
                    || seen[index]) {

                throw new IOException(
                        "invalid/duplicate parameter index: "
                                + index);
            }

            if (!Double.isFinite(
                    value)) {

                throw new IOException(
                        "non-finite pretrained parameter: "
                                + index);
            }

            values[index] =
                    value;

            seen[index] =
                    true;
        }

        for (int i = 0;
             i < expectedCount;
             i++) {

            if (!seen[i]) {

                throw new IOException(
                        "missing pretrained parameter index: "
                                + i);
            }
        }

        return values;
    }

    private static List<QuantumCoordinates> readWalkers(
            Path file,
            Molecule molecule)
            throws IOException {

        if (!Files.isRegularFile(
                file)) {

            throw new IOException(
                    "missing pretrained walkers: "
                            + file);
        }

        List<String> lines =
                Files.readAllLines(
                        file);

        if (lines.isEmpty()) {

            throw new IOException(
                    "pretrained walker artifact is empty");
        }

        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> byWalker =
                new LinkedHashMap<>();

        for (int lineIndex = 1;
             lineIndex < lines.size();
             lineIndex++) {

            String line =
                    lines.get(
                                    lineIndex)
                            .trim();

            if (line.isEmpty()) {

                continue;
            }

            String[] fields =
                    line.split(
                            ",");

            if (fields.length != 6) {

                throw new IOException(
                        "invalid walker CSV line: "
                                + line);
            }

            try {

                int walker =
                        Integer.parseInt(
                                fields[0]);

                int electron =
                        Integer.parseInt(
                                fields[1]);

                SpinProjection spin =
                        SpinProjection.valueOf(
                                fields[2]);

                double x =
                        Double.parseDouble(
                                fields[3]);

                double y =
                        Double.parseDouble(
                                fields[4]);

                double z =
                        Double.parseDouble(
                                fields[5]);

                if (!Double.isFinite(
                        x)
                        || !Double.isFinite(
                        y)
                        || !Double.isFinite(
                        z)) {

                    throw new IOException(
                            "non-finite walker coordinate");
                }

                byWalker.computeIfAbsent(
                                walker,
                                ignored ->
                                        new ArrayList<>())
                        .add(
                                new QuantumCoordinates.ParticleCoordinate(
                                        electron,
                                        x,
                                        y,
                                        z,
                                        spin));

            } catch (IllegalArgumentException exception) {

                throw new IOException(
                        "invalid walker CSV line: "
                                + line,
                        exception);
            }
        }

        if (byWalker.isEmpty()) {

            throw new IOException(
                    "pretrained walker artifact is empty");
        }

        List<QuantumCoordinates> result =
                new ArrayList<>();

        int expectedWalker =
                0;

        for (var entry :
                byWalker.entrySet()) {

            if (entry.getKey()
                    != expectedWalker) {

                throw new IOException(
                        "walker indices must be contiguous from zero");
            }

            expectedWalker++;

            List<QuantumCoordinates.ParticleCoordinate> particles =
                    entry.getValue();

            if (particles.size()
                    != molecule.electrons()
                    .value()) {

                throw new IOException(
                        "walker "
                                + entry.getKey()
                                + " electron count mismatch");
            }

            particles.sort(
                    Comparator.comparingInt(
                            QuantumCoordinates.ParticleCoordinate::particleIndex));

            for (int i = 0;
                 i < particles.size();
                 i++) {

                var particle =
                        particles.get(
                                i);

                if (particle.particleIndex()
                        != i) {

                    throw new IOException(
                            "walker particle ordering mismatch");
                }

                SpinProjection expected =
                        i < molecule.spin()
                                .alphaElectrons()
                                ? SpinProjection.ALPHA
                                : SpinProjection.BETA;

                if (particle.spin()
                        != expected) {

                    throw new IOException(
                            "walker spin ordering mismatch");
                }
            }

            result.add(
                    new QuantumCoordinates(
                            particles));
        }

        return result;
    }

    /*
     * =====================================================================
     * Statistics/output only
     * =====================================================================
     */

    private static Statistics statistics(
            double[] values) {

        if (values.length < 2) {

            throw new IllegalArgumentException(
                    "at least two retained samples are required");
        }

        double sum =
                0.0;

        for (double value :
                values) {

            if (!Double.isFinite(
                    value)) {

                throw new IllegalArgumentException(
                        "non-finite retained observable");
            }

            sum +=
                    value;
        }

        double mean =
                sum
                        / values.length;

        double squared =
                0.0;

        for (double value :
                values) {

            double delta =
                    value
                            - mean;

            squared +=
                    delta
                            * delta;
        }

        double variance =
                squared
                        / (values.length - 1);

        return new Statistics(
                values.length,
                mean,
                Math.sqrt(
                        variance),
                Math.sqrt(
                        variance
                                / values.length));
    }

    private static void writeEnergySamples(
            Path file,
            List<LocalEnergyComponents> energies)
            throws IOException {

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "sample,kinetic_hartree,electron_nuclear_hartree,"
                        + "electron_electron_hartree,"
                        + "nuclear_nuclear_hartree,total_hartree\n");

        for (int i = 0;
             i < energies.size();
             i++) {

            LocalEnergyComponents energy =
                    energies.get(
                            i);

            csv.append(
                            i)
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.kineticHartree()))
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.electronNuclearHartree()))
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.electronElectronHartree()))
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.nuclearNuclearHartree()))
                    .append(
                            ',')
                    .append(
                            Double.toHexString(
                                    energy.totalHartree()))
                    .append(
                            '\n');
        }

        Files.writeString(
                file,
                csv.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeWalkers(
            Path file,
            List<QuantumCoordinates> walkers)
            throws IOException {

        StringBuilder csv =
                new StringBuilder();

        csv.append(
                "sample,electron,spin,x_bohr_hex,y_bohr_hex,z_bohr_hex\n");

        for (int sample = 0;
             sample < walkers.size();
             sample++) {

            for (var electron :
                    walkers.get(
                                    sample)
                            .particles()) {

                csv.append(
                                sample)
                        .append(
                                ',')
                        .append(
                                electron.particleIndex())
                        .append(
                                ',')
                        .append(
                                electron.spin())
                        .append(
                                ',')
                        .append(
                                Double.toHexString(
                                        electron.xBohr()))
                        .append(
                                ',')
                        .append(
                                Double.toHexString(
                                        electron.yBohr()))
                        .append(
                                ',')
                        .append(
                                Double.toHexString(
                                        electron.zBohr()))
                        .append(
                                '\n');
            }
        }

        Files.writeString(
                file,
                csv.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeSummary(
            Arguments arguments,
            FermiNetRuntimeSampling.Result result,
            Statistics kinetic,
            Statistics electronNuclear,
            Statistics electronElectron,
            Statistics nuclearNuclear,
            Statistics total,
            boolean samplingOperational,
            boolean catastrophicEnergyFailureAbsent,
            String classification,
            Instant started,
            Instant finished)
            throws IOException {

        Map<String, Object> summary =
                new LinkedHashMap<>();

        summary.put(
                "schema",
                "prometheus-ferminet-h2o-post-pretraining-diagnostic-v2");

        summary.put(
                "stage",
                "POST_PRETRAINING_NEURAL_VMC_DIAGNOSTIC");

        summary.put(
                "canonical_vmc_engine",
                "FermiNetVmc");

        summary.put(
                "started_utc",
                started.toString());

        summary.put(
                "finished_utc",
                finished.toString());

        summary.put(
                "pretraining_directory",
                arguments.pretrainingDirectory()
                        .toString());

        summary.put(
                "ferminet_reference_commit",
                ReferenceFermiNetPretrainer.REFERENCE_COMMIT);

        summary.put(
                "burn_in_sweeps",
                arguments.burnInSweeps());

        summary.put(
                "step_size_bohr",
                arguments.stepSizeBohr());

        summary.put(
                "sweeps_between_retained",
                arguments.sweepsBetweenRetained());

        summary.put(
                "retained_per_walker",
                arguments.retainedPerWalker());

        summary.put(
                "retained_samples",
                result.samples()
                        .size());

        summary.put(
                "seed",
                arguments.seed());

        summary.put(
                "acceptance",
                result.acceptance());

        summary.put(
                "diagnostic_acceptance_range",
                List.of(
                        MIN_ACCEPTANCE,
                        MAX_ACCEPTANCE));

        summary.put(
                "catastrophic_sanity_energy_range_hartree",
                List.of(
                        MIN_SANITY_ENERGY_HARTREE,
                        MAX_SANITY_ENERGY_HARTREE));

        summary.put(
                "kinetic",
                statisticMap(
                        kinetic));

        summary.put(
                "electron_nuclear",
                statisticMap(
                        electronNuclear));

        summary.put(
                "electron_electron",
                statisticMap(
                        electronElectron));

        summary.put(
                "nuclear_nuclear",
                statisticMap(
                        nuclearNuclear));

        summary.put(
                "total",
                statisticMap(
                        total));

        summary.put(
                "sampling_operational",
                samplingOperational);

        summary.put(
                "catastrophic_energy_failure_absent",
                catastrophicEnergyFailureAbsent);

        summary.put(
                "classification",
                classification);

        summary.put(
                "energy_scientifically_qualified",
                false);

        summary.put(
                "sr_started",
                false);

        summary.put(
                "forces_evaluated",
                false);

        OBJECT_MAPPER.writeValue(
                arguments.outputDirectory()
                        .resolve(
                                "qualification-summary.json")
                        .toFile(),
                summary);
    }

    private static Map<String, Object> statisticMap(
            Statistics statistic) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "n",
                statistic.n());

        result.put(
                "mean_hartree",
                statistic.mean());

        result.put(
                "sample_standard_deviation_hartree",
                statistic.standardDeviation());

        result.put(
                "naive_standard_error_hartree",
                statistic.standardError());

        return result;
    }

    private static Molecule water() {

        return new Molecule(
                "ferminet-v1-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(
                                        8),
                                new CartesianPosition(
                                        0.0,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(
                                        1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(
                                        1),
                                new CartesianPosition(
                                        -0.46464225035067114,
                                        1.7340684963325879,
                                        0.0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(
                        0),
                new ElectronCount(
                        10),
                new SpinSector(
                        5,
                        5,
                        1));
    }

    private record Statistics(
            int n,
            double mean,
            double standardDeviation,
            double standardError) {
    }

    private record Arguments(
            Path pretrainingDirectory,
            Path outputDirectory,
            int burnInSweeps,
            int retainedPerWalker,
            int sweepsBetweenRetained,
            double stepSizeBohr,
            long seed) {

        private static Arguments parse(
                String[] args) {

            Path pretraining =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-pretraining");

            Path output =
                    Path.of(
                            "analysis",
                            "prometheus-ferminet-h2o-post-pretraining-qualification");

            int burnIn =
                    DEFAULT_BURN_IN_SWEEPS;

            int retained =
                    DEFAULT_RETAINED_PER_WALKER;

            int between =
                    DEFAULT_SWEEPS_BETWEEN_RETAINED;

            double step =
                    DEFAULT_STEP_SIZE_BOHR;

            long seed =
                    DEFAULT_SEED;

            for (int i = 0;
                 i < args.length;
                 i++) {

                switch (args[i]) {

                    case "--pretraining" -> {

                        if (++i >= args.length) {

                            throw usage(
                                    "--pretraining requires a path");
                        }

                        pretraining =
                                Path.of(
                                        args[i]);
                    }

                    case "--output" -> {

                        if (++i >= args.length) {

                            throw usage(
                                    "--output requires a path");
                        }

                        output =
                                Path.of(
                                        args[i]);
                    }

                    case "--burn-in" -> {

                        if (++i >= args.length) {

                            throw usage(
                                    "--burn-in requires an integer");
                        }

                        burnIn =
                                Integer.parseInt(
                                        args[i]);
                    }

                    case "--retained-per-walker" -> {

                        if (++i >= args.length) {

                            throw usage(
                                    "--retained-per-walker requires an integer");
                        }

                        retained =
                                Integer.parseInt(
                                        args[i]);
                    }

                    case "--sweeps-between-retained" -> {

                        if (++i >= args.length) {

                            throw usage(
                                    "--sweeps-between-retained requires an integer");
                        }

                        between =
                                Integer.parseInt(
                                        args[i]);
                    }

                    case "--step-size" -> {

                        if (++i >= args.length) {

                            throw usage(
                                    "--step-size requires a number");
                        }

                        step =
                                Double.parseDouble(
                                        args[i]);
                    }

                    case "--seed" -> {

                        if (++i >= args.length) {

                            throw usage(
                                    "--seed requires a long");
                        }

                        seed =
                                Long.parseLong(
                                        args[i]);
                    }

                    default ->

                            throw usage(
                                    "unknown argument: "
                                            + args[i]);
                }
            }

            if (burnIn < 0
                    || retained < 1
                    || between < 1
                    || !(step > 0.0)
                    || !Double.isFinite(
                    step)) {

                throw usage(
                        "invalid qualification arguments");
            }

            return new Arguments(
                    pretraining
                            .toAbsolutePath()
                            .normalize(),
                    output
                            .toAbsolutePath()
                            .normalize(),
                    burnIn,
                    retained,
                    between,
                    step,
                    seed);
        }

        private static IllegalArgumentException usage(
                String problem) {

            return new IllegalArgumentException(
                    problem
                            + System.lineSeparator()
                            + """
                              --pretraining PATH
                              --output PATH
                              --burn-in N
                              --retained-per-walker N
                              --sweeps-between-retained N
                              --step-size BOHR
                              --seed LONG
                              """);
        }
    }
}
