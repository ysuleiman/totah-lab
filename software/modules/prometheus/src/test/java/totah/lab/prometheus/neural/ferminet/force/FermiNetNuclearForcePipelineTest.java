package totah.lab.prometheus.neural.ferminet.force;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeConfiguration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngineType;

final class FermiNetNuclearForcePipelineTest {

    @Test
    void completedN1024ReferenceMapsBitExactlyIntoCommonSchema() throws Exception {
        Path root = Path.of("../../..").toAbsolutePath().normalize();
        Path force = root.resolve("artifacts/prometheus/h2o/ferminet/forces/"
                + "correlated-fd-iteration-017-n1024");
        Path referenceFile = force.resolve("correlated-fd-reference.json");
        Path checkpointFile = root.resolve("artifacts/prometheus/h2o/ferminet/sr/"
                + "qualified-best-7988-n1024-checkpointed-8step/iteration-017/"
                + "continuation-checkpoint.bin");
        Assumptions.assumeTrue(Files.exists(referenceFile) && Files.exists(checkpointFile));
        var legacy = new ObjectMapper().readValue(referenceFile.toFile(),
                FermiNetCorrelatedFiniteDifferenceForceReference.Result.class);
        var checkpoint = FermiNetOptimizationCheckpoint.read(checkpointFile);
        Molecule molecule = water();
        var network = FermiNetV1Configuration.locked();
        var layout = new FermiNetParameterLayout(network, molecule);
        var state = new FermiNetV1State(molecule, network,
                FermiNetParameters.fromArray(layout, checkpoint.parameters()));
        var context = new FermiNetForceEvaluationContext(
                state, checkpoint.parameterChecksum(), checkpoint.geometryIdentity(),
                force.resolve("configurations.csv"), legacy.dataset(),
                sha256(checkpointFile), checkpoint.rootParameterChecksum());

        NuclearForceResult common = new CorrelatedFdFermiNetForceEstimator()
                .adapt(context, NuclearForceConfiguration.correlatedFd(1.0e-3), legacy);

        assertEquals(NuclearForceEstimatorType.CORRELATED_FD, common.estimatorType());
        assertEquals(CorrelatedFdFermiNetForceEstimator.CLASSIFICATION,
                common.classification());
        assertEquals(legacy.parameterChecksum(), common.parameterChecksum());
        assertEquals(legacy.dataset().sha256(), common.datasetChecksum());
        assertEquals(9, common.components().size());
        for (int i = 0; i < 9; i++) {
            var expected = legacy.components().get(i);
            var actual = common.components().get(i);
            assertEquals(Double.doubleToRawLongBits(expected.forceHartreePerBohr()),
                    Double.doubleToRawLongBits(actual.meanHartreePerBohr()));
            assertEquals(Double.doubleToRawLongBits(expected.forceStandardError()),
                    Double.doubleToRawLongBits(actual.chainStandardError()));
            assertArrayEquals(expected.rawForceSamples(), actual.rawSamples(), 0.0);
            assertEquals(sha256(expected.rawForceSamples()), actual.rawSampleChecksum());
        }
    }

    @Test
    void dispatcherUsesSingleSelectionBoundaryAndFailsClosed() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        var estimator = (FermiNetNuclearForceEstimator)
                (context, configuration, derivativeEngine) -> {
            invoked.set(true);
            return dummy();
        };
        var pipeline = new FermiNetNuclearForcePipeline(
                Map.of(NuclearForceEstimatorType.CORRELATED_FD, estimator));
        pipeline.estimate(nullContext(), NuclearForceConfiguration.correlatedFd(1.0e-3));
        assertTrue(invoked.get());
        assertThrows(UnsupportedOperationException.class, () ->
                pipeline.estimate(nullContext(),
                        NuclearForceConfiguration.unsupported(
                                NuclearForceEstimatorType.SWCT)));
    }

    @Test
    void scientificIdentitySeparatesEstimatorDerivativeAndParallelOrder()
            throws Exception {
        var context = nullContext();
        String reference = FermiNetForceScientificIdentity.create(context,
                NuclearForceConfiguration.swct(),
                new FermiNetDerivativeConfiguration(
                        FermiNetDerivativeEngineType.REFERENCE_JET, 1));
        String engine = FermiNetForceScientificIdentity.create(context,
                NuclearForceConfiguration.swct(),
                new FermiNetDerivativeConfiguration(
                        FermiNetDerivativeEngineType.BATCHED_FORWARD, 1));
        String parallel = FermiNetForceScientificIdentity.create(context,
                NuclearForceConfiguration.swct(),
                new FermiNetDerivativeConfiguration(
                        FermiNetDerivativeEngineType.BATCHED_FORWARD, 2));
        String estimator = FermiNetForceScientificIdentity.create(context,
                NuclearForceConfiguration.acZv(),
                new FermiNetDerivativeConfiguration(
                        FermiNetDerivativeEngineType.REFERENCE_JET, 1));
        assertEquals(4, java.util.Set.of(reference, engine, parallel, estimator).size());
    }

    @Test void declaredCheckpointChecksumIsNotTreatedAsVerification()
            throws Exception {
        var context = nullContext();
        Path corrupt = Files.createTempFile("declared-checkpoint", ".bin");
        Files.writeString(corrupt, "not the declared checkpoint");
        assertThrows(java.io.IOException.class,
                () -> context.verifyCheckpoint(corrupt));
        assertEquals("0".repeat(64),
                context.declaredProvenance().checkpointChecksum());
    }

    @Test void pipelinePersistsCompleteIdentityRatherThanEstimatorOnly()
            throws Exception {
        var estimator = (FermiNetNuclearForceEstimator)
                (context, configuration, derivativeEngine) -> dummy();
        var pipeline = new FermiNetNuclearForcePipeline(
                Map.of(NuclearForceEstimatorType.CORRELATED_FD, estimator));
        var result = pipeline.estimate(nullContext(),
                NuclearForceConfiguration.correlatedFd(1.0e-3),
                FermiNetDerivativeConfiguration.batchedForward(2));
        assertNotEquals(NuclearForceConfiguration.correlatedFd(1.0e-3).identity(),
                result.estimatorConfigurationIdentity());
        assertEquals(64, result.estimatorConfigurationIdentity().length());
    }

    private static FermiNetForceEvaluationContext nullContext() throws Exception {
        Molecule molecule = water();
        var configuration = FermiNetV1Configuration.locked();
        var layout = new FermiNetParameterLayout(configuration, molecule);
        var state = new FermiNetV1State(molecule, configuration,
                FermiNetParameters.initialize(layout, 7L));
        Path file = Files.createTempFile("force-context", ".csv");
        var coordinates = List.of(testCoordinates(), testCoordinates());
        Files.delete(file);
        var identity = FermiNetCorrelatedFdConfigurationFile.write(file, coordinates, 2);
        String parameter = FermiNetOptimizationCheckpoint.parameterChecksum(
                totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess
                        .parameterSnapshot(state));
        String geometry = totah.lab.prometheus.neural.ferminet.pretraining
                .FermiNetPretrainingQualification.geometryIdentity(molecule);
        return new FermiNetForceEvaluationContext(
                state, parameter, geometry, file, identity, "0".repeat(64), parameter);
    }

    private static NuclearForceResult dummy() {
        double[] raw = {0.0, 0.0};
        var tails = new NuclearForceResult.TailDiagnostics(
                0, 0, 0, 0, 0, 0, 0, 0, 0);
        return new NuclearForceResult(
                NuclearForceEstimatorType.CORRELATED_FD,
                CorrelatedFdFermiNetForceEstimator.CLASSIFICATION,
                "0".repeat(64), "0".repeat(64), "0".repeat(64),
                "0".repeat(64), "0".repeat(64), 2, 2, 1,
                List.of(new NuclearForceResult.Component(
                        0, 0, "x", 0, 0, 0, 2, 0, tails,
                        sha256(raw), raw)),
                new NuclearForceResult.CorrelatedFdDiagnostics(1.0e-3, List.of()));
    }

    private static String sha256(double[] values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (double value : values) {
                long bits = Double.doubleToRawLongBits(value);
                for (int shift = 56; shift >= 0; shift -= 8) {
                    digest.update((byte) (bits >>> shift));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static totah.lab.prometheus.variational.QuantumCoordinates testCoordinates() {
        java.util.ArrayList<totah.lab.prometheus.variational.QuantumCoordinates.ParticleCoordinate>
                values = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            values.add(new totah.lab.prometheus.variational.QuantumCoordinates.ParticleCoordinate(
                    i, 0.1 * (i + 1), -0.07 * i, 0.03 * i,
                    i < 5 ? totah.lab.prometheus.variational.SpinProjection.ALPHA
                            : totah.lab.prometheus.variational.SpinProjection.BETA));
        }
        return new totah.lab.prometheus.variational.QuantumCoordinates(values);
    }

    private static Molecule water() {
        return new Molecule("ferminet-v1-water", List.of(
                new NuclearCenter(0, "O", new NuclearCharge(8),
                        new CartesianPosition(0, 0, 0, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(1.7952398191849366, 0, 0, LengthUnit.BOHR)),
                new NuclearCenter(2, "H", new NuclearCharge(1),
                        new CartesianPosition(-0.46464225035067114,
                                1.7340684963325879, 0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(10), new SpinSector(5, 5, 1));
    }
}
