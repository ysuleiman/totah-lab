package totah.lab.prometheus.neural.ferminet.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.neural.ferminet.pretraining.GaussianHartreeFockOrbitalTargetTest;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class FermiNetBatchedWorkspaceRetentionTest {

    @Test
    void resetReusesTheExistingJetSlicesWithoutGrowingChunks() {
        FermiNetBatchedJetWorkspace workspace = new FermiNetBatchedJetWorkspace();
        int peakJets = 5_000;
        for (int cycle = 0; cycle < 4; cycle++) {
            for (int jet = 0; jet < peakJets; jet++) {
                workspace.acquire(jet, -jet, 30, 9);
            }
            if (cycle == 0) {
                assertTrue(workspace.retainedChunkCount() > 1);
            }
            long bytes = workspace.retainedPrimitiveBytes();
            int chunks = workspace.retainedChunkCount();
            int jets = workspace.retainedJetObjects();
            workspace.reset();
            assertEquals(bytes, workspace.retainedPrimitiveBytes());
            assertEquals(chunks, workspace.retainedChunkCount());
            assertEquals(jets, workspace.retainedJetObjects());
        }
    }

    @Test
    void repeatedH2oEvaluationPlateausAndKeepsCopiedResultsStable() {
        FermiNetV1State state = waterState();
        QuantumCoordinates coordinates = waterCoordinates();
        BatchedForwardFermiNetDerivativeEngine engine =
                new BatchedForwardFermiNetDerivativeEngine(1);

        long started = System.nanoTime();
        var expected = engine.nuclear(state, coordinates);
        long warmBytes = engine.retainedPrimitiveBytes();
        int warmChunks = engine.maximumRetainedChunkCount();
        for (int evaluation = 0; evaluation < 12; evaluation++) {
            var actual = engine.nuclear(state, coordinates);
            assertEquals(expected.sign(), actual.sign());
            assertEquals(expected.logAbsoluteWavefunction(),
                    actual.logAbsoluteWavefunction(), 0.0);
            assertArrayEquals(expected.logNuclearGradient(),
                    actual.logNuclearGradient(), 0.0);
            assertEquals(warmBytes, engine.retainedPrimitiveBytes());
            assertEquals(warmChunks, engine.maximumRetainedChunkCount());
        }
        assertArrayEquals(expected.logNuclearGradient(),
                engine.nuclear(state, coordinates).logNuclearGradient(), 0.0);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        System.out.printf("H2O repeated workspace: workers=1 evaluations=14 "
                        + "warmBytes=%d endBytes=%d maxChunks=%d elapsedMs=%d%n",
                warmBytes, engine.retainedPrimitiveBytes(), warmChunks,
                elapsedMillis);
    }

    @Test
    void sixConcurrentH2oWorkspacesAndShapeVariantsStayBounded()
            throws Exception {
        FermiNetV1State state = waterState();
        QuantumCoordinates coordinates = waterCoordinates();
        BatchedForwardFermiNetDerivativeEngine engine =
                new BatchedForwardFermiNetDerivativeEngine(6);
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            List<Future<FermiNetStateAccess.NuclearSnapshot>> futures =
                    new ArrayList<>();
            for (int worker = 0; worker < 6; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return engine.nuclear(state, coordinates);
                }));
            }
            ready.await();
            start.countDown();
            var expected = futures.get(0).get();
            for (Future<FermiNetStateAccess.NuclearSnapshot> future : futures) {
                assertArrayEquals(expected.logNuclearGradient(),
                        future.get().logNuclearGradient(), 0.0);
            }
        }

        assertTrue(engine.retainedWorkspaceCount() <= 6);
        long concurrentBytes = engine.retainedPrimitiveBytes();
        for (int directions = 1; directions <= 12; directions++) {
            engine.directionalBatch(state, coordinates,
                    nuclearDirections(directions), electronDirections(directions));
            assertTrue(engine.retainedWorkspaceCount() <= 6);
            assertTrue(engine.retainedShapeCount() <= 6);
        }
        assertTrue(engine.retainedPrimitiveBytes() <= concurrentBytes);
        System.out.printf("H2O concurrent workspace: workers=6 evaluations=18 "
                        + "warmBytes=%d endBytes=%d retained=%d maxChunks=%d%n",
                concurrentBytes, engine.retainedPrimitiveBytes(),
                engine.retainedWorkspaceCount(),
                engine.maximumRetainedChunkCount());
    }

    private static List<FermiNetStateAccess.NuclearDirection> nuclearDirections(
            int count) {
        List<FermiNetStateAccess.NuclearDirection> result = new ArrayList<>();
        for (int direction = 0; direction < count; direction++) {
            double[] values = new double[9];
            values[direction % values.length] = 1.0;
            result.add(new FermiNetStateAccess.NuclearDirection(values));
        }
        return result;
    }

    private static List<FermiNetStateAccess.ElectronDirection> electronDirections(
            int count) {
        List<FermiNetStateAccess.ElectronDirection> result = new ArrayList<>();
        for (int direction = 0; direction < count; direction++) {
            double[] values = new double[30];
            values[direction % values.length] = 0.125;
            result.add(new FermiNetStateAccess.ElectronDirection(values));
        }
        return result;
    }

    private static FermiNetV1State waterState() {
        var molecule = GaussianHartreeFockOrbitalTargetTest.water();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.testFixture();
        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(configuration, molecule);
        return new FermiNetV1State(molecule, configuration,
                FermiNetParameters.initialize(layout, 91_339L));
    }

    private static QuantumCoordinates waterCoordinates() {
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        for (int electron = 0; electron < 10; electron++) {
            double scale = electron + 1.0;
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    electron, 0.07 * scale, -0.05 * scale, 0.03 * scale,
                    electron < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }
        return new QuantumCoordinates(particles);
    }
}
