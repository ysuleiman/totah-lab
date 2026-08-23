package totah.lab.prometheus.neural.ferminet.drivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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

final class FermiNetH2oDisplacedDeterminantValidationTest {

    private static final String RETAINED_SHA =
            "69a5f193ee7d71d4d15187e247731f74db96f36e0d7e64a32a976f77b04c411a";

    @Test
    void deterministicN64ParityAndN1024Consumability() throws Exception {
        Path checkpointPath = checkpoint();
        var parent = FermiNetOptimizationCheckpoint.read(checkpointPath);
        var geometry = FermiNetH2oGeometryManifest.require("symmetric-minus");
        var configuration = FermiNetV1Configuration.locked();
        FermiNetV1State state = new FermiNetV1State(
                geometry.molecule(), configuration,
                FermiNetParameters.fromArray(
                        new FermiNetParameterLayout(configuration, geometry.molecule()),
                        parent.parameters()));
        var provenance = FermiNetH2oSrDriver.verifyAndCreateBranchProvenance(
                checkpointPath, 20260818L, geometry, state, parent);
        List<QuantumCoordinates> walkers = FermiNetH2oSrDriver.freshBranchWalkers(
                state, provenance.walkerInitializationSeed());
        var request = new FermiNetRuntimeSampling.Request(
                64, 100, 16, 10, 0.02, provenance.samplingSeed());

        List<QuantumCoordinates> retained;
        double acceptance;
        try (var session = FermiNetRuntimeSampling.beginSession(
                state, request, walkers, 12)) {
            var replay = session.sampleCoordinates(state, 100, 16, 10);
            retained = replay.samples();
            acceptance = replay.acceptance();
        }
        assertEquals(1024, retained.size());
        assertEquals(RETAINED_SHA, checksum(retained));

        var batched = FermiNetStateAccess.derivatives(
                FermiNetDerivativeConfiguration.batchedForward());
        double sum = 0.0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double forensicEnergy = Double.NaN;
        for (int sample = 0; sample < retained.size(); sample++) {
            QuantumCoordinates coordinates = retained.get(sample);
            var sampling = FermiNetStateAccess.sampling(state, coordinates);
            var reference = FermiNetStateAccess.spatial(state, coordinates);
            assertEquals(sampling.sign(), reference.sign(), "sample " + sample);
            assertEquals(sampling.logAbsoluteWavefunction(),
                    reference.logAbsoluteWavefunction(), 1.0e-12,
                    "sample " + sample);
            double energy = FermiNetRuntimeSampling.localEnergyWithLog(
                    state, coordinates, reference).localEnergy().totalHartree();
            assertTrue(Double.isFinite(energy), "sample " + sample);
            if (sample < 64) {
                var candidate = batched.spatial(state, coordinates);
                assertEquals(reference.logAbsoluteWavefunction(),
                        candidate.logAbsoluteWavefunction(), 1.0e-12,
                        "N64 log sample " + sample);
                assertEquals(reference.laplacianOverWavefunction(),
                        candidate.laplacianOverWavefunction(), 1.0e-8,
                        "N64 Laplacian sample " + sample);
                assertArrayClose(reference.logCoordinateGradient(),
                        candidate.logCoordinateGradient(), 1.0e-10,
                        "N64 gradient sample " + sample);
                double candidateEnergy = FermiNetRuntimeSampling.localEnergyWithLog(
                        state, coordinates, candidate).localEnergy().totalHartree();
                assertEquals(energy, candidateEnergy, 1.0e-8,
                        "N64 energy sample " + sample);
            }
            if (sample == 103) forensicEnergy = energy;
            sum += energy;
            minimum = Math.min(minimum, energy);
            maximum = Math.max(maximum, energy);
        }
        System.out.printf(java.util.Locale.ROOT, """
                FERMINET_DISPLACED_DETERMINANT_VALIDATION
                  retained_sha=%s
                  samples=%d
                  acceptance=%.16f
                  n64_reference_batched_parity=PASS
                  n1024_consumable=PASS
                  forensic_sample_103_local_energy=%.16f
                  mean_local_energy=%.16f
                  min_local_energy=%.16f
                  max_local_energy=%.16f
                """, RETAINED_SHA, retained.size(), acceptance, forensicEnergy,
                sum / retained.size(), minimum, maximum);
    }

    private static void assertArrayClose(
            double[] expected, double[] actual, double tolerance, String label) {
        assertEquals(expected.length, actual.length, label);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], tolerance, label + " axis " + i);
        }
    }

    private static String checksum(List<QuantumCoordinates> coordinates) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (QuantumCoordinates sample : coordinates) {
            for (var particle : sample.particles()) {
                update(digest, particle.particleIndex());
                update(digest, particle.xBohr());
                update(digest, particle.yBohr());
                update(digest, particle.zBohr());
                update(digest, particle.spin().ordinal());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, double value) {
        update(digest, Double.doubleToLongBits(value));
    }

    private static void update(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static Path checkpoint() {
        String relative = "artifacts/prometheus/h2o/ferminet/sr/"
                + "qualified-best-7988-n1024-checkpointed-8step/iteration-017/"
                + "continuation-checkpoint.bin";
        return List.of(Path.of(relative), Path.of("../../..").resolve(relative))
                .stream().map(Path::normalize).filter(Files::isRegularFile)
                .findFirst().orElseThrow();
    }
}
