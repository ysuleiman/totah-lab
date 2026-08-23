package totah.lab.prometheus.neural.ferminet.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * TEST_ID: E3, E4 — checkpoint resume is bit-identical, and optimizer state
 * is persisted whole.
 *
 * <p>Seam: {@link FermiNetVariationalOptimizer#optimizeCheckpointed} /
 * {@code resume} with {@link FermiNetOptimizationCheckpoint#write} /
 * {@code read} — the checkpoint carries parameters, walkers, the serialized
 * sampler RNG state, the completed-iteration counter, and SHA-256 checksums
 * over each payload. The optimizer is exact SR
 * ({@link FermiNetMatrixFreeSrOptimizer}): deterministic given the samples,
 * so any state loss shows up as a trajectory divergence.
 *
 * <p>Fixture: the smallest reference-aligned FermiNet state
 * ({@code FermiNetV1Configuration.testFixture()}), two walkers, N = 2
 * iterations before the checkpoint, M = 3 after, against an uninterrupted
 * N + M = 5 run. All constants are distinct from the existing optimizer
 * tests.
 */
final class AdversarialCheckpointResumeAcceptanceTest {

    private static final int N_BEFORE_CHECKPOINT = 2;
    private static final int M_AFTER_RESUME = 3;
    private static final String GEOMETRY_IDENTITY =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    /**
     * E3 — resume-from-checkpoint equals uninterrupted execution, bit for
     * bit. The checkpoint survives a real disk round-trip (write → read)
     * before resuming, so the assertion covers persistence, not just
     * in-memory continuity. Oracle: at every resumed global iteration the
     * parameter checksum, walker checksum, and serialized-RNG-state checksum
     * equal the uninterrupted run's, the updated parameter arrays are
     * bit-identical, and global iteration numbering continues.
     */
    @Test
    void e3ResumeAfterDiskRoundTripIsBitIdenticalToUninterruptedRun() throws IOException {
        Fixture fixture = fixture();
        var sampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                2, 2, 2, 1, 0.02, 50211L);
        var optimization = FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                new FermiNetMatrixFreeSrOptimizer.Configuration(0.015, 0.8, 5.0, 1));
        String root = FermiNetOptimizationCheckpoint.parameterChecksum(
                fixture.state().parameterArray());

        List<FermiNetVariationalOptimizer.CheckpointedIteration> continuous;
        List<FermiNetVariationalOptimizer.CheckpointedIteration> first;
        try (var optimizer = new FermiNetVariationalOptimizer(1)) {
            continuous = optimizer.optimizeCheckpointed(fixture.state(), fixture.walkers(),
                    sampling, optimization, root, GEOMETRY_IDENTITY,
                    N_BEFORE_CHECKPOINT + M_AFTER_RESUME);
        }
        try (var optimizer = new FermiNetVariationalOptimizer(1)) {
            first = optimizer.optimizeCheckpointed(fixture.state(), fixture.walkers(),
                    sampling, optimization, root, GEOMETRY_IDENTITY, N_BEFORE_CHECKPOINT);
        }

        Path persisted = temporaryDirectory.resolve("e3-continuation.bin");
        first.get(N_BEFORE_CHECKPOINT - 1).checkpoint().write(persisted);
        FermiNetOptimizationCheckpoint restored = FermiNetOptimizationCheckpoint.read(persisted);
        assertEquals(N_BEFORE_CHECKPOINT, restored.completedIterations());

        List<FermiNetVariationalOptimizer.CheckpointedIteration> resumed;
        try (var optimizer = new FermiNetVariationalOptimizer(1)) {
            resumed = optimizer.resume(
                    fixture.state().withParameters(restored.parameters()), restored,
                    sampling, optimization, GEOMETRY_IDENTITY, M_AFTER_RESUME);
        }

        assertEquals(M_AFTER_RESUME, resumed.size());
        for (int local = 0; local < M_AFTER_RESUME; local++) {
            int global = N_BEFORE_CHECKPOINT + local;
            var expected = continuous.get(global);
            var actual = resumed.get(local);
            assertEquals(global, actual.result().iteration(),
                    "global iteration numbering continues across the resume");
            assertEquals(N_BEFORE_CHECKPOINT + local + 1,
                    actual.checkpoint().completedIterations());
            assertEquals(expected.checkpoint().parameterChecksum(),
                    actual.checkpoint().parameterChecksum());
            assertEquals(expected.checkpoint().walkerChecksum(),
                    actual.checkpoint().walkerChecksum());
            assertEquals(expected.checkpoint().randomStateChecksum(),
                    actual.checkpoint().randomStateChecksum(),
                    "the sampler RNG state resumes bit-identically");
            assertArrayEquals(expected.result().updatedState().parameterArray(),
                    actual.result().updatedState().parameterArray());
        }
    }

    /**
     * E4 (deterministic resume) — reloading the same checkpoint twice and
     * continuing both copies yields bit-identical trajectories.
     */
    @Test
    void e4DoubleReloadResumeIsDeterministic() throws IOException {
        Fixture fixture = fixture();
        var sampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                2, 2, 2, 1, 0.02, 60253L);
        var optimization = FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                new FermiNetMatrixFreeSrOptimizer.Configuration(0.015, 0.8, 5.0, 1));
        String root = FermiNetOptimizationCheckpoint.parameterChecksum(
                fixture.state().parameterArray());

        FermiNetVariationalOptimizer.CheckpointedIteration completed;
        try (var optimizer = new FermiNetVariationalOptimizer(1)) {
            completed = optimizer.optimizeCheckpointed(fixture.state(), fixture.walkers(),
                    sampling, optimization, root, GEOMETRY_IDENTITY, 1).get(0);
        }
        Path persisted = temporaryDirectory.resolve("e4-determinism.bin");
        completed.checkpoint().write(persisted);

        FermiNetV1State state = completed.result().updatedState();
        double[][] finals = new double[2][];
        for (int copy = 0; copy < 2; copy++) {
            FermiNetOptimizationCheckpoint restored =
                    FermiNetOptimizationCheckpoint.read(persisted);
            try (var optimizer = new FermiNetVariationalOptimizer(1)) {
                var resumed = optimizer.resume(state, restored, sampling,
                        optimization, GEOMETRY_IDENTITY, 2);
                finals[copy] = resumed.get(1).result().updatedState().parameterArray();
            }
        }
        assertArrayEquals(finals[0], finals[1],
                "two resumes from the same persisted state must be bit-identical");
    }

    /**
     * E4 (state persisted whole) — corrupting or truncating any state
     * component makes the load refuse: truncation mid-file, a 4-byte
     * truncation clipping the serialized RNG state, one flipped byte inside
     * the RNG-state region, one trailing appended byte, and a checkpoint
     * object whose parameters do not match the offered state. None of these
     * may resume silently.
     */
    @Test
    void e4CorruptedTruncatedOrInconsistentStateIsRefused() throws IOException {
        Fixture fixture = fixture();
        var sampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                2, 2, 2, 1, 0.02, 70411L);
        var optimization = FermiNetVariationalOptimizer.OptimizationConfiguration.exactSr(
                new FermiNetMatrixFreeSrOptimizer.Configuration(0.015, 0.8, 5.0, 1));
        String root = FermiNetOptimizationCheckpoint.parameterChecksum(
                fixture.state().parameterArray());

        FermiNetVariationalOptimizer.CheckpointedIteration completed;
        try (var optimizer = new FermiNetVariationalOptimizer(1)) {
            completed = optimizer.optimizeCheckpointed(fixture.state(), fixture.walkers(),
                    sampling, optimization, root, GEOMETRY_IDENTITY, 1).get(0);
        }
        FermiNetOptimizationCheckpoint checkpoint = completed.checkpoint();
        FermiNetV1State state = completed.result().updatedState();

        Path whole = temporaryDirectory.resolve("e4-whole.bin");
        checkpoint.write(whole);
        byte[] bytes = Files.readAllBytes(whole);

        Path truncatedHalf = temporaryDirectory.resolve("e4-truncated-half.bin");
        Files.write(truncatedHalf, Arrays.copyOf(bytes, bytes.length / 2));
        assertThrows(IOException.class,
                () -> FermiNetOptimizationCheckpoint.read(truncatedHalf),
                "mid-file truncation must refuse the load");

        Path truncatedTail = temporaryDirectory.resolve("e4-truncated-tail.bin");
        Files.write(truncatedTail, Arrays.copyOf(bytes, bytes.length - 4));
        assertThrows(IOException.class,
                () -> FermiNetOptimizationCheckpoint.read(truncatedTail),
                "clipping the serialized RNG state must refuse the load");

        Path flippedRandom = temporaryDirectory.resolve("e4-flipped-random.bin");
        byte[] flipped = bytes.clone();
        flipped[flipped.length - 1] ^= 0x01;
        Files.write(flippedRandom, flipped);
        assertThrows(IOException.class,
                () -> FermiNetOptimizationCheckpoint.read(flippedRandom),
                "a flipped byte in the RNG state must fail the payload checksum");

        Path trailing = temporaryDirectory.resolve("e4-trailing.bin");
        byte[] withTrailing = Arrays.copyOf(bytes, bytes.length + 1);
        Files.write(trailing, withTrailing);
        assertThrows(IOException.class,
                () -> FermiNetOptimizationCheckpoint.read(trailing),
                "trailing garbage must refuse the load");

        double[] wrongParameters = checkpoint.parameters();
        wrongParameters[0] += 1.0;
        var inconsistent = new FermiNetOptimizationCheckpoint(
                checkpoint.completedIterations(), checkpoint.rootParameterChecksum(),
                checkpoint.samplingConfigurationIdentity(),
                checkpoint.optimizerConfigurationIdentity(),
                checkpoint.geometryIdentity(), checkpoint.optimizerType(),
                wrongParameters, checkpoint.walkers(),
                checkpoint.serializedRandomState());
        try (var optimizer = new FermiNetVariationalOptimizer(1)) {
            assertThrows(IllegalArgumentException.class, () -> optimizer.resume(
                    state, inconsistent, sampling, optimization, GEOMETRY_IDENTITY, 1),
                    "parameters that do not match the offered state must refuse resume");
        }
    }

    private record Fixture(FermiNetV1State state, List<QuantumCoordinates> walkers) {}

    private static Fixture fixture() {
        Molecule molecule = new Molecule(
                "adversarial-checkpoint-h2",
                List.of(
                        new NuclearCenter(0, "H", new NuclearCharge(1),
                                new CartesianPosition(-0.7, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(1, "H", new NuclearCharge(1),
                                new CartesianPosition(0.7, 0.0, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2), new SpinSector(1, 1, 1));
        FermiNetV1Configuration configuration = FermiNetV1Configuration.testFixture();
        FermiNetV1State state = new FermiNetV1State(
                molecule, configuration, FermiNetParameters.initialize(
                        new FermiNetParameterLayout(configuration, molecule), 88103L));
        return new Fixture(state, List.of(coordinates(0.0), coordinates(0.03)));
    }

    private static QuantumCoordinates coordinates(double shift) {
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        particles.add(new QuantumCoordinates.ParticleCoordinate(
                0, -0.4 + shift, 0.15, -0.1, SpinProjection.ALPHA));
        particles.add(new QuantumCoordinates.ParticleCoordinate(
                1, 0.45 - shift, -0.12, 0.08, SpinProjection.BETA));
        return new QuantumCoordinates(particles);
    }
}
