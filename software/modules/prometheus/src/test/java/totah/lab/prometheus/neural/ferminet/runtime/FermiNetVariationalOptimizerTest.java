package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

final class FermiNetVariationalOptimizerTest {

    private static final int PARALLELISM = 2;

    @TempDir
    Path temporaryDirectory;

    @Test
    void checkpointedThreePlusFiveIsBitExactWithContinuousEight() throws IOException {
        Fixture fixture = fixture();
        var sampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                2, 3, 2, 2, 0.02, 99117L);
        var optimization = FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                srConfiguration());
        String root = FermiNetOptimizationCheckpoint.parameterChecksum(
                fixture.state().parameterArray());
        String geometry = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        List<FermiNetVariationalOptimizer.CheckpointedIteration> continuous;
        List<FermiNetVariationalOptimizer.CheckpointedIteration> first;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            continuous = optimizer.optimizeCheckpointed(
                    fixture.state(), fixture.walkers(), sampling, optimization,
                    root, geometry, 8);
        }
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            first = optimizer.optimizeCheckpointed(
                    fixture.state(), fixture.walkers(), sampling, optimization,
                    root, geometry, 3);
        }

        Path persisted = temporaryDirectory.resolve("continuation.bin");
        first.get(2).checkpoint().write(persisted);
        FermiNetOptimizationCheckpoint restored =
                FermiNetOptimizationCheckpoint.read(persisted);
        FermiNetV1State resumedState = fixture.state().withParameters(
                restored.parameters());
        List<FermiNetVariationalOptimizer.CheckpointedIteration> resumed;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            resumed = optimizer.resume(resumedState, restored, sampling,
                    optimization, geometry, 5);
        }

        assertEquals(5, resumed.size());
        for (int index = 0; index < resumed.size(); index++) {
            var expected = continuous.get(index + 3);
            var actual = resumed.get(index);
            assertEquals(index + 3, actual.result().iteration());
            assertEquals(expected.checkpoint().parameterChecksum(),
                    actual.checkpoint().parameterChecksum());
            assertEquals(expected.checkpoint().walkerChecksum(),
                    actual.checkpoint().walkerChecksum());
            assertArrayEquals(expected.result().updatedState().parameterArray(),
                    actual.result().updatedState().parameterArray());
            assertCoordinatesExactly(expected.result().nextWalkers(),
                    actual.result().nextWalkers());
            assertSameBits(expected.result().energyStatistics().meanHartree(),
                    actual.result().energyStatistics().meanHartree());
            assertSameBits(expected.result().energyStatistics().standardErrorHartree(),
                    actual.result().energyStatistics().standardErrorHartree());
            assertArrayEquals(expected.result().exactSrResult().energyGradient(),
                    actual.result().exactSrResult().energyGradient());
            assertSameBits(expected.result().exactSrResult().rawUpdateNorm(),
                    actual.result().exactSrResult().rawUpdateNorm());
        }
    }

    @Test
    void checkpointedSingleIterationPreservesExistingOneStepResult() {
        Fixture fixture = fixture();
        var sampling = sampling(2, 2, 811L);
        var optimization = FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                srConfiguration());
        FermiNetVariationalOptimizer.OptimizationIterationResult existing;
        FermiNetVariationalOptimizer.OptimizationIterationResult checkpointed;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            existing = optimizer.oneIteration(
                    0, fixture.state(), fixture.walkers(), sampling, optimization);
        }
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            checkpointed = optimizer.optimizeCheckpointed(
                    fixture.state(), fixture.walkers(), sampling, optimization,
                    FermiNetOptimizationCheckpoint.parameterChecksum(
                            fixture.state().parameterArray()),
                    "1111111111111111111111111111111111111111111111111111111111111111",
                    1).get(0).result();
        }
        assertCoordinatesExactly(existing.vmcResult().samples(),
                checkpointed.vmcResult().samples());
        assertLocalEnergiesExactly(existing.vmcResult().localEnergies(),
                checkpointed.vmcResult().localEnergies());
        assertArrayEquals(existing.updatedState().parameterArray(),
                checkpointed.updatedState().parameterArray());
        assertArrayEquals(existing.exactSrResult().energyGradient(),
                checkpointed.exactSrResult().energyGradient());
    }

    @Test
    void corruptedCheckpointAndConfigurationMismatchesFailClosed() throws IOException {
        Fixture fixture = fixture();
        var sampling = sampling(2, 2, 7712L);
        var optimization = FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                srConfiguration());
        String root = FermiNetOptimizationCheckpoint.parameterChecksum(
                fixture.state().parameterArray());
        String geometry = "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd";
        FermiNetVariationalOptimizer.CheckpointedIteration completed;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            completed = optimizer.optimizeCheckpointed(
                    fixture.state(), fixture.walkers(), sampling, optimization,
                    root, geometry, 1).get(0);
        }
        var checkpoint = completed.checkpoint();
        FermiNetV1State state = completed.result().updatedState();

        var badRandom = new FermiNetOptimizationCheckpoint(
                checkpoint.completedIterations(), checkpoint.rootParameterChecksum(),
                checkpoint.samplingConfigurationIdentity(),
                checkpoint.optimizerConfigurationIdentity(), checkpoint.geometryIdentity(),
                checkpoint.optimizerType(), checkpoint.parameters(), checkpoint.walkers(),
                new byte[]{1, 2, 3});
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            assertThrows(IllegalArgumentException.class, () -> optimizer.resume(
                    state, badRandom, sampling, optimization, geometry, 1));
        }

        var mismatchedSampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                sampling.walkers(), sampling.warmupSweeps(),
                sampling.retainedPerWalker(), sampling.sweepsBetweenRetained() + 1,
                sampling.stepSizeBohr(), sampling.baseSeed());
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            assertThrows(IllegalArgumentException.class, () -> optimizer.resume(
                    state, checkpoint, mismatchedSampling, optimization, geometry, 1));
        }
        var mismatchedOptimizer = FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        0.02, 1.0, 0.05, 2));
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            assertThrows(IllegalArgumentException.class, () -> optimizer.resume(
                    state, checkpoint, sampling, mismatchedOptimizer, geometry, 1));
        }

        Path parameterFile = temporaryDirectory.resolve("corrupt-parameter.bin");
        checkpoint.write(parameterFile);
        byte[] parameterBytes = Files.readAllBytes(parameterFile);
        flipFirstLong(parameterBytes,
                Double.doubleToRawLongBits(checkpoint.parameters()[0]));
        Files.write(parameterFile, parameterBytes);
        assertThrows(IOException.class,
                () -> FermiNetOptimizationCheckpoint.read(parameterFile));

        Path walkerFile = temporaryDirectory.resolve("corrupt-walker.bin");
        checkpoint.write(walkerFile);
        byte[] walkerBytes = Files.readAllBytes(walkerFile);
        flipFirstLong(walkerBytes, Double.doubleToRawLongBits(
                checkpoint.walkers().get(0).particles().get(0).xBohr()));
        Files.write(walkerFile, walkerBytes);
        assertThrows(IOException.class,
                () -> FermiNetOptimizationCheckpoint.read(walkerFile));

        Path randomFile = temporaryDirectory.resolve("corrupt-random.bin");
        checkpoint.write(randomFile);
        byte[] randomBytes = Files.readAllBytes(randomFile);
        randomBytes[randomBytes.length - 1] ^= 1;
        Files.write(randomFile, randomBytes);
        assertThrows(IOException.class,
                () -> FermiNetOptimizationCheckpoint.read(randomFile));
    }

    @Test
    void oneIterationMatchesManualCompositionExactly() {
        Fixture fixture = fixture();
        var sampling = sampling(2, 2, 811L);
        var srConfiguration = srConfiguration();

        FermiNetVariationalOptimizer.OptimizationIterationResult actual;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            actual = optimizer.oneIteration(
                    0,
                    fixture.state(),
                    fixture.walkers(),
                    sampling,
                    FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                            srConfiguration));
        }

        FermiNetVmc.Result manualVmc;
        try (var vmc = new FermiNetVmcParallel(PARALLELISM)) {
            manualVmc = vmc.sample(
                    fixture.state(),
                    new FermiNetVmc.Configuration(
                            sampling.walkers(),
                            sampling.warmupSweeps(),
                            sampling.retainedPerWalker(),
                            sampling.sweepsBetweenRetained(),
                            sampling.stepSizeBohr(),
                            sampling.baseSeed()),
                    fixture.walkers());
        }
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> manualSamples =
                manualVmc.samples().stream()
                        .map(coordinates ->
                                new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                        1.0,
                                        coordinates))
                        .toList();
        FermiNetMatrixFreeSrOptimizer.Result manualSr =
                new FermiNetMatrixFreeSrOptimizer().oneIteration(
                        fixture.state(),
                        manualSamples,
                        FermiNetKnownLocalEnergies.from(fixture.state(), manualVmc),
                        srConfiguration);

        assertSameBits(manualVmc.acceptance(), actual.vmcResult().acceptance());
        assertCoordinatesExactly(manualVmc.samples(), actual.vmcResult().samples());
        assertLocalEnergiesExactly(
                manualVmc.localEnergies(),
                actual.vmcResult().localEnergies());
        assertSameBits(manualSr.initialEnergyHartree(),
                actual.exactSrResult().initialEnergyHartree());
        assertSameBits(manualSr.gradientNorm(), actual.exactSrResult().gradientNorm());
        assertSameBits(manualSr.rawUpdateNorm(), actual.exactSrResult().rawUpdateNorm());
        assertSameBits(manualSr.appliedUpdateNorm(),
                actual.exactSrResult().appliedUpdateNorm());
        assertSameBits(
                manualSr.relativeTrueResidual(),
                actual.exactSrResult().relativeTrueResidual());
        assertArrayEquals(
                manualSr.state().parameterArray(),
                actual.updatedState().parameterArray());

        int start = manualVmc.samples().size() - sampling.walkers();
        assertCoordinatesExactly(
                manualVmc.samples().subList(start, manualVmc.samples().size()),
                actual.nextWalkers());
    }

    @Test
    void optimizeMatchesOnePersistentSessionWithWarmupOnlyOnce() {
        Fixture fixture = fixture();
        var sampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                2,
                3,
                2,
                2,
                0.02,
                1200L);
        var srConfiguration = srConfiguration();
        List<FermiNetVariationalOptimizer.OptimizationIterationResult> results;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            results = optimizer.optimize(
                    fixture.state(),
                    fixture.walkers(),
                    sampling,
                    FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                            srConfiguration),
                    3);
        }

        assertEquals(3, results.size());
        assertEquals(1200L, results.get(0).seed());
        assertEquals(1200L, results.get(1).seed());
        assertEquals(1200L, results.get(2).seed());

        FermiNetV1State manualState = fixture.state();
        try (var vmc = new FermiNetVmcParallel(PARALLELISM)) {
            FermiNetVmcParallel.SamplingSession session = vmc.beginSession(
                    manualState,
                    new FermiNetVmc.Configuration(
                            sampling.walkers(),
                            sampling.warmupSweeps(),
                            sampling.retainedPerWalker(),
                            sampling.sweepsBetweenRetained(),
                            sampling.stepSizeBohr(),
                            sampling.baseSeed()),
                    fixture.walkers());
            for (int iteration = 0; iteration < results.size(); iteration++) {
                int warmup = iteration == 0 ? sampling.warmupSweeps() : 0;
                FermiNetVmcParallel.ContinuationResult manualVmc = session.sample(
                        manualState,
                        warmup,
                        sampling.retainedPerWalker(),
                        sampling.sweepsBetweenRetained());
                long expectedProposals = (long) sampling.walkers()
                        * (warmup + sampling.retainedPerWalker()
                        * sampling.sweepsBetweenRetained());
                assertEquals(expectedProposals, manualVmc.proposed());

                var optimizerResult = results.get(iteration);
                assertSameBits(
                        manualVmc.result().acceptance(),
                        optimizerResult.vmcResult().acceptance());
                assertCoordinatesExactly(
                        manualVmc.result().samples(),
                        optimizerResult.vmcResult().samples());
                assertLocalEnergiesExactly(
                        manualVmc.result().localEnergies(),
                        optimizerResult.vmcResult().localEnergies());

                List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples =
                        manualVmc.result().samples().stream()
                                .map(coordinates ->
                                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                                1.0,
                                                coordinates))
                                .toList();
                FermiNetMatrixFreeSrOptimizer.Result manualSr =
                        new FermiNetMatrixFreeSrOptimizer().oneIteration(
                                manualState,
                                samples,
                                FermiNetKnownLocalEnergies.from(
                                        manualState,
                                        manualVmc.result()),
                                srConfiguration);
                assertArrayEquals(
                        manualSr.state().parameterArray(),
                        optimizerResult.updatedState().parameterArray());
                assertEquals(
                        optimizerResult.vmcResult().samples().size(),
                        optimizerResult.reusedLocalEnergyCount());
                manualState = manualSr.state();
            }
        }

        assertArrayEquals(
                fixture.state().parameterArray(),
                results.get(0).inputState().parameterArray());
        assertArrayEquals(
                results.get(0).updatedState().parameterArray(),
                results.get(1).inputState().parameterArray());
        assertArrayEquals(
                results.get(1).updatedState().parameterArray(),
                results.get(2).inputState().parameterArray());
        for (FermiNetVariationalOptimizer.OptimizationIterationResult result : results) {
            assertFalse(Arrays.equals(
                    result.inputState().parameterArray(),
                    result.updatedState().parameterArray()));
        }
    }

    @Test
    void allRetainedSamplesFeedSrButOnlyLastRetentionSeedsNextIteration() {
        Fixture fixture = fixture();
        var sampling = sampling(2, 3, 2026L);
        FermiNetVariationalOptimizer.OptimizationIterationResult result;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            result = optimizer.oneIteration(
                    0,
                    fixture.state(),
                    fixture.walkers(),
                    sampling,
                    FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                            srConfiguration()));
        }

        int expectedSamples = sampling.walkers() * sampling.retainedPerWalker();
        assertEquals(expectedSamples, result.vmcResult().samples().size());
        assertEquals(expectedSamples, result.exactSrResult().sampleEvaluations());
        assertEquals(sampling.walkers(), result.nextWalkers().size());
        assertCoordinatesExactly(
                result.vmcResult().samples().subList(
                        expectedSamples - sampling.walkers(),
                        expectedSamples),
                result.nextWalkers());
    }

    private static FermiNetVariationalOptimizer.SamplingConfiguration sampling(
            int walkers,
            int retainedPerWalker,
            long seed) {
        return new FermiNetVariationalOptimizer.SamplingConfiguration(
                walkers,
                1,
                retainedPerWalker,
                1,
                0.02,
                seed);
    }

    private static FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration() {
        return new FermiNetMatrixFreeSrOptimizer.Configuration(
                0.01,
                1.0,
                10.0,
                2);
    }

    private static Fixture fixture() {
        Molecule molecule = hydrogen();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.testFixture();
        FermiNetV1State state = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.initialize(
                        new FermiNetParameterLayout(configuration, molecule),
                        44017L));
        return new Fixture(
                state,
                List.of(coordinates(0.0), coordinates(0.025)));
    }

    private static Molecule hydrogen() {
        return new Molecule(
                "ferminet-variational-optimizer-h2",
                List.of(
                        new NuclearCenter(
                                0,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(-0.7, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(0.7, 0.0, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static QuantumCoordinates coordinates(double shift) {
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        particles.add(new QuantumCoordinates.ParticleCoordinate(
                0,
                -0.4 + shift,
                0.15,
                -0.1,
                SpinProjection.ALPHA));
        particles.add(new QuantumCoordinates.ParticleCoordinate(
                1,
                0.45 - shift,
                -0.12,
                0.08,
                SpinProjection.BETA));
        return new QuantumCoordinates(particles);
    }

    private static void assertLocalEnergiesExactly(
            List<LocalEnergyComponents> expected,
            List<LocalEnergyComponents> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            LocalEnergyComponents left = expected.get(index);
            LocalEnergyComponents right = actual.get(index);
            assertSameBits(left.kineticHartree(), right.kineticHartree());
            assertSameBits(left.electronNuclearHartree(), right.electronNuclearHartree());
            assertSameBits(left.electronElectronHartree(), right.electronElectronHartree());
            assertSameBits(left.nuclearNuclearHartree(), right.nuclearNuclearHartree());
            assertSameBits(left.totalHartree(), right.totalHartree());
        }
    }

    private static void assertCoordinatesExactly(
            List<QuantumCoordinates> expected,
            List<QuantumCoordinates> actual) {
        assertEquals(expected.size(), actual.size());
        for (int sample = 0; sample < expected.size(); sample++) {
            List<QuantumCoordinates.ParticleCoordinate> left =
                    expected.get(sample).particles();
            List<QuantumCoordinates.ParticleCoordinate> right =
                    actual.get(sample).particles();
            assertEquals(left.size(), right.size());
            for (int particle = 0; particle < left.size(); particle++) {
                assertEquals(left.get(particle).particleIndex(), right.get(particle).particleIndex());
                assertEquals(left.get(particle).spin(), right.get(particle).spin());
                assertSameBits(left.get(particle).xBohr(), right.get(particle).xBohr());
                assertSameBits(left.get(particle).yBohr(), right.get(particle).yBohr());
                assertSameBits(left.get(particle).zBohr(), right.get(particle).zBohr());
            }
        }
    }

    private static void assertSameBits(double expected, double actual) {
        assertEquals(
                Double.doubleToRawLongBits(expected),
                Double.doubleToRawLongBits(actual));
    }

    private static void flipFirstLong(byte[] bytes, long value) {
        outer: for (int offset = 0; offset <= bytes.length - Long.BYTES; offset++) {
            for (int index = 0; index < Long.BYTES; index++) {
                int shift = 56 - index * 8;
                if (bytes[offset + index] != (byte) (value >>> shift)) continue outer;
            }
            bytes[offset] ^= 1;
            return;
        }
        throw new AssertionError("checkpoint value not found");
    }

    private record Fixture(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers) {
    }
}
