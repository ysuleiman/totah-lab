package totah.lab.prometheus.neural.ferminet.force;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeConfiguration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngine;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngineType;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngines;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Serial vs ExecutorService-parallel equality for
 * {@link SwctFermiNetForceEstimator#estimate} (branch on
 * engine.sampleParallelism(), lines ~116-150). Each sample is evaluated
 * independently into its own slot and all reductions run serially in a fixed
 * order, so a thread-safe engine must reproduce the serial run bit for bit on
 * the same seeded dataset. A deterministic 16-sample / 4-walker dataset on
 * the small seeded network (public-constructor equivalent of the
 * package-private testFixture configuration, seed 44017L) keeps the whole
 * comparison in-JVM. A fabricated diagnostic-only FD reference with huge
 * variances satisfies the load gates; it never enters the compared values.
 */
final class SwctSerialParallelEqualityTest {

    private static final int COMPONENTS = 9;
    private static final int SAMPLES = 16;
    private static final int WALKERS = 4;

    @TempDir Path temporary;

    /** PARITY-SWCT-01: BATCHED_FORWARD parallelism 1 vs 4, bitwise equal. */
    @Test
    void batchedForwardSerialAndParallelProduceBitwiseIdenticalResults()
            throws Exception {
        FermiNetForceEvaluationContext context = newContext();
        writeFdReference(context);
        FermiNetDerivativeEngine serial = FermiNetDerivativeEngines.create(
                FermiNetDerivativeConfiguration.batchedForward(1));
        FermiNetDerivativeEngine parallel = FermiNetDerivativeEngines.create(
                FermiNetDerivativeConfiguration.batchedForward(4));
        assertThat(serial.sampleParallelism()).isEqualTo(1);
        assertThat(parallel.sampleParallelism()).isGreaterThan(1);

        NuclearForceResult expected = estimate(context, serial);
        NuclearForceResult actual = estimate(context, parallel);

        assertBitwiseIdentical(expected, actual);
    }

    /** PARITY-SWCT-02: REFERENCE_JET parallelism 1 vs 4, bitwise equal. */
    @Test
    void referenceJetSerialAndParallelProduceBitwiseIdenticalResults()
            throws Exception {
        FermiNetForceEvaluationContext context = newContext();
        writeFdReference(context);
        FermiNetDerivativeEngine serial = FermiNetDerivativeEngines.create(
                new FermiNetDerivativeConfiguration(
                        FermiNetDerivativeEngineType.REFERENCE_JET, 1));
        FermiNetDerivativeEngine parallel = FermiNetDerivativeEngines.create(
                new FermiNetDerivativeConfiguration(
                        FermiNetDerivativeEngineType.REFERENCE_JET, 4));
        assertThat(serial.sampleParallelism()).isEqualTo(1);
        assertThat(parallel.sampleParallelism()).isGreaterThan(1);

        NuclearForceResult expected = estimate(context, serial);
        NuclearForceResult actual = estimate(context, parallel);

        assertBitwiseIdentical(expected, actual);
    }

    // ------------------------------------------------------------------
    // Self-contained fixture helpers (copiable for reuse).
    // ------------------------------------------------------------------

    private static NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            FermiNetDerivativeEngine engine) throws Exception {
        return new SwctFermiNetForceEstimator().estimate(
                context, NuclearForceConfiguration.swct(), engine);
    }

    private static void assertBitwiseIdentical(
            NuclearForceResult expected, NuclearForceResult actual) {
        assertThat(actual.classification()).isEqualTo(expected.classification());
        assertThat(actual.classification())
                .isEqualTo(SwctFermiNetForceEstimator.VARIANCE_REDUCED);
        assertThat(actual.components()).hasSize(COMPONENTS);
        assertThat(actual.datasetChecksum()).isEqualTo(expected.datasetChecksum());
        for (int component = 0; component < COMPONENTS; component++) {
            var e = expected.components().get(component);
            var a = actual.components().get(component);
            assertThat(a.nucleus()).isEqualTo(e.nucleus());
            assertThat(a.axis()).isEqualTo(e.axis());
            assertThat(a.finiteCount()).isEqualTo(SAMPLES);
            assertThat(a.nonfiniteCount()).isEqualTo(0);
            assertThat(Double.doubleToRawLongBits(a.meanHartreePerBohr()))
                    .as("mean bits component %d", component)
                    .isEqualTo(Double.doubleToRawLongBits(
                            e.meanHartreePerBohr()));
            assertThat(Double.doubleToRawLongBits(a.variance()))
                    .as("variance bits component %d", component)
                    .isEqualTo(Double.doubleToRawLongBits(e.variance()));
            assertThat(Double.doubleToRawLongBits(a.chainStandardError()))
                    .as("chain SE bits component %d", component)
                    .isEqualTo(Double.doubleToRawLongBits(
                            e.chainStandardError()));
            assertThat(a.rawSampleChecksum())
                    .as("raw sample checksum component %d", component)
                    .isEqualTo(e.rawSampleChecksum());
            double[] expectedSamples = e.rawSamples();
            double[] actualSamples = a.rawSamples();
            assertThat(actualSamples).hasSize(expectedSamples.length);
            for (int sample = 0; sample < expectedSamples.length; sample++) {
                assertThat(Double.doubleToRawLongBits(actualSamples[sample]))
                        .as("raw sample bits component %d sample %d",
                                component, sample)
                        .isEqualTo(Double.doubleToRawLongBits(
                                expectedSamples[sample]));
            }
        }
    }

    private FermiNetForceEvaluationContext newContext() throws Exception {
        Molecule molecule = water();
        // Same dimensions as the package-private testFixture configuration,
        // via the public canonical constructor.
        FermiNetV1Configuration network = new FermiNetV1Configuration(
                3, 2, 8, 4, 2, true, true, false, false, false, false);
        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(network, molecule);
        FermiNetV1State state = new FermiNetV1State(molecule, network,
                FermiNetParameters.initialize(layout, 44017L));
        String parameters = FermiNetOptimizationCheckpoint.parameterChecksum(
                FermiNetStateAccess.parameterSnapshot(state));
        String geometry =
                FermiNetPretrainingQualification.geometryIdentity(molecule);
        Path file = temporary.resolve("configurations.csv");
        var identity = FermiNetCorrelatedFdConfigurationFile.write(
                file, samples(), WALKERS);
        return new FermiNetForceEvaluationContext(
                state, parameters, geometry, file, identity,
                "0".repeat(64), parameters);
    }

    /**
     * Fabricated diagnostic-only FD stand-in with huge variances; never a
     * scientific reference. Provenance fields match the context so the
     * estimator load gates pass.
     */
    private static void writeFdReference(
            FermiNetForceEvaluationContext context) throws Exception {
        var tails = new FermiNetCorrelatedFiniteDifferenceForceReference
                .TailDiagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0);
        List<FermiNetCorrelatedFiniteDifferenceForceReference.ComponentResult>
                components = new ArrayList<>();
        for (int nucleus = 0; nucleus < 3; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                components.add(
                        new FermiNetCorrelatedFiniteDifferenceForceReference
                                .ComponentResult(nucleus, axis,
                                        switch (axis) {
                                            case 0 -> "x";
                                            case 1 -> "y";
                                            default -> "z";
                                        },
                                        0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0e12,
                                        1.0, 1.0, 1.0,
                                        context.parameterChecksum(),
                                        "0".repeat(64), "0".repeat(64), tails,
                                        new double[] {0.0}));
            }
        }
        new ObjectMapper().writeValue(
                context.configurationFile().getParent()
                        .resolve("correlated-fd-reference.json").toFile(),
                new FermiNetCorrelatedFiniteDifferenceForceReference.Result(
                        1.0e-3, context.parameterChecksum(), "0".repeat(64),
                        context.dataset(), components));
    }

    /** Deterministic 16-sample dataset: shared base geometry, shifted. */
    private static List<QuantumCoordinates> samples() {
        List<QuantumCoordinates> configurations = new ArrayList<>(SAMPLES);
        for (int sample = 0; sample < SAMPLES; sample++) {
            configurations.add(coordinates(0.007 * sample - 0.05));
        }
        return configurations;
    }

    private static QuantumCoordinates coordinates(double shift) {
        double[][] xyz = {
                {0.18, 0.11, 0.27}, {-0.31, 0.42, -0.16},
                {0.57, -0.28, 0.33}, {-0.63, -0.37, 0.21},
                {0.24, 0.71, -0.45}, {-0.22, -0.15, -0.38},
                {0.36, -0.54, 0.19}, {-0.48, 0.26, 0.51},
                {0.69, 0.18, -0.24}, {-0.12, 0.61, 0.37}
        };
        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>();
        for (int i = 0; i < xyz.length; i++) {
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    i, xyz[i][0] + shift, xyz[i][1], xyz[i][2],
                    i < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }
        return new QuantumCoordinates(particles);
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
                new MolecularCharge(0), new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }
}
