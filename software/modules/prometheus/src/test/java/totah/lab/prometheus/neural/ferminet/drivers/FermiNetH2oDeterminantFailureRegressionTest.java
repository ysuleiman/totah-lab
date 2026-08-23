package totah.lab.prometheus.neural.ferminet.drivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeConfiguration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetRuntimeSampling;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class FermiNetH2oDeterminantFailureRegressionTest {

    @Test
    void forensicSampleSupportsEveryDerivativePath() throws Exception {
        FermiNetV1State state = state();
        QuantumCoordinates coordinates = forensicCoordinates();

        var sampling = FermiNetStateAccess.sampling(state, coordinates);
        var reference = FermiNetStateAccess.spatial(state, coordinates);
        var batched = FermiNetStateAccess.derivatives(
                FermiNetDerivativeConfiguration.batchedForward())
                .spatial(state, coordinates);

        assertEquals(1, sampling.sign());
        assertEquals(-22.664073209695180,
                sampling.logAbsoluteWavefunction(), 0.0);
        assertEquals(sampling.sign(), reference.sign());
        assertEquals(sampling.logAbsoluteWavefunction(),
                reference.logAbsoluteWavefunction(), 1.0e-13);
        assertEquals(reference.logAbsoluteWavefunction(),
                batched.logAbsoluteWavefunction(), 1.0e-13);
        assertEquals(reference.laplacianOverWavefunction(),
                batched.laplacianOverWavefunction(), 1.0e-9);
        assertFinite(reference.logCoordinateGradient());
        assertFinite(reference.laplacianOverWavefunction());
        assertFinite(FermiNetRuntimeSampling.localEnergy(
                state, coordinates).totalHartree());

        var nuclearReference = FermiNetStateAccess.nuclear(state, coordinates);
        var nuclearBatched = FermiNetStateAccess.derivatives(
                FermiNetDerivativeConfiguration.batchedForward())
                .nuclear(state, coordinates);
        assertFinite(nuclearReference.logNuclearGradient());
        assertArrayClose(nuclearReference.logNuclearGradient(),
                nuclearBatched.logNuclearGradient(), 1.0e-10);

        double[] nuclearDirection = new double[9];
        double[] electronDirection = new double[30];
        for (int i = 0; i < nuclearDirection.length; i++) {
            nuclearDirection[i] = (i - 4) * 0.03125;
        }
        for (int i = 0; i < electronDirection.length; i++) {
            electronDirection[i] = (i % 7 - 3) * 0.015625;
        }
        var direction = new FermiNetStateAccess.NuclearDirection(nuclearDirection);
        var electron = new FermiNetStateAccess.ElectronDirection(electronDirection);
        var directionalReference = FermiNetStateAccess.directional(
                state, coordinates, direction, electron);
        var directionalBatched = FermiNetStateAccess.derivatives(
                FermiNetDerivativeConfiguration.batchedForward())
                .directional(state, coordinates, direction, electron);
        assertEquals(directionalReference.directionalLogAbsoluteWavefunction(),
                directionalBatched.directionalLogAbsoluteWavefunction(), 1.0e-9);
        assertEquals(directionalReference.directionalLaplacianOverWavefunction(),
                directionalBatched.directionalLaplacianOverWavefunction(), 1.0e-7);

        FermiNetV1State.Evaluation evaluation = state.evaluate(coordinates);
        assertFinite(evaluation.parameterLogDerivatives());
        Method structured = FermiNetV1State.class.getDeclaredMethod(
                "structuredSrEvaluation", QuantumCoordinates.class);
        structured.setAccessible(true);
        structured.invoke(state, coordinates);
    }

    private static FermiNetV1State state() throws Exception {
        var checkpoint = FermiNetOptimizationCheckpoint.read(checkpoint());
        var molecule = FermiNetH2oGeometryManifest.require("symmetric-minus").molecule();
        var configuration = FermiNetV1Configuration.locked();
        return new FermiNetV1State(molecule, configuration,
                FermiNetParameters.fromArray(
                        new FermiNetParameterLayout(configuration, molecule),
                        checkpoint.parameters()));
    }

    private static Path checkpoint() {
        String relative = "artifacts/prometheus/h2o/ferminet/sr/"
                + "qualified-best-7988-n1024-checkpointed-8step/iteration-017/"
                + "continuation-checkpoint.bin";
        return List.of(Path.of(relative), Path.of("../../..").resolve(relative))
                .stream().map(Path::normalize).filter(Files::isRegularFile)
                .findFirst().orElseThrow();
    }

    private static QuantumCoordinates forensicCoordinates() {
        String[][] xyz = {
                {"-0x1.7a3c7135573d8p-1", "-0x1.b45469e313f3fp0", "-0x1.03041f20709b6p-2"},
                {"0x1.797fc58f28c06p-2", "-0x1.46a044f04216p-2", "-0x1.601a56b4f7166p-1"},
                {"0x1.e487a042425fep-1", "0x1.68ff40390596p0", "0x1.f2e345fed0586p-1"},
                {"0x1.ad9c9e3582538p0", "-0x1.ca9e494bfcf43p-3", "0x1.b576d0c3f6e5bp-2"},
                {"0x1.5f47ea2d1f402p0", "-0x1.dbf4b5a232f76p-3", "0x1.6ff80c24d67dap0"},
                {"-0x1.a277c30b491cp-3", "0x1.f6a0d2b45a03ep-3", "0x1.b8eee0d141dabp-1"},
                {"0x1.1ab5770901f32p-3", "-0x1.c06d48b0747a1p-1", "-0x1.1549b4ae755cap0"},
                {"0x1.092515e22d9aep-1", "0x1.aa23c4caa13c9p-2", "0x1.0efe896f8a51dp1"},
                {"0x1.655f54eed506bp1", "0x1.1590e932ce78fp-2", "0x1.f1f26715b83f5p0"},
                {"-0x1.95ba1fb197e23p0", "0x1.7792c75bdf4f7p1", "-0x1.3f0c8844ec386p0"}
        };
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        for (int i = 0; i < xyz.length; i++) {
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    i, Double.valueOf(xyz[i][0]), Double.valueOf(xyz[i][1]),
                    Double.valueOf(xyz[i][2]),
                    i < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }
        return new QuantumCoordinates(particles);
    }

    private static void assertFinite(double value) {
        assertTrue(Double.isFinite(value), () -> "non-finite value: " + value);
    }

    private static void assertFinite(double[] values) {
        for (double value : values) assertFinite(value);
    }

    private static void assertArrayClose(double[] expected, double[] actual, double tolerance) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], tolerance, "index " + i);
        }
    }
}
