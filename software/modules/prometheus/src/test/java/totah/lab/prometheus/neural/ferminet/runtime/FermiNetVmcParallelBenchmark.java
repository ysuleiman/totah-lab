package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Non-acceptance benchmark. Parity is enforced by the separate parity test.
 */
final class FermiNetVmcParallelBenchmark {

    @Test
    void reportSequentialAndParallelTiming() {
        Fixture fixture = fixture();

        FermiNetVmc.Configuration configuration =
                new FermiNetVmc.Configuration(
                        fixture.walkers.size(),
                        20,
                        1,
                        10,
                        0.02,
                        20260825L);

        new FermiNetVmc()
                .sample(
                        fixture.state,
                        configuration,
                        fixture.walkers);

        int parallelism =
                Math.max(
                        2,
                        Math.min(
                                Runtime.getRuntime()
                                        .availableProcessors(),
                                fixture.walkers.size()));

        try (FermiNetVmcParallel sampler =
                     new FermiNetVmcParallel(parallelism)) {

            sampler.sample(
                    fixture.state,
                    configuration,
                    fixture.walkers);
        }

        long sequentialStart = System.nanoTime();

        FermiNetVmc.Result sequential =
                new FermiNetVmc()
                        .sample(
                                fixture.state,
                                configuration,
                                fixture.walkers);

        long sequentialNanos =
                System.nanoTime()
                        - sequentialStart;

        FermiNetVmc.Result parallel;
        long parallelNanos;

        try (FermiNetVmcParallel sampler =
                     new FermiNetVmcParallel(parallelism)) {

            long parallelStart =
                    System.nanoTime();

            parallel =
                    sampler.sample(
                            fixture.state,
                            configuration,
                            fixture.walkers);

            parallelNanos =
                    System.nanoTime()
                            - parallelStart;
        }

        if (Double.doubleToRawLongBits(sequential.acceptance())
                != Double.doubleToRawLongBits(parallel.acceptance())) {
            throw new AssertionError(
                    "benchmark trajectories diverged");
        }

        double speedup =
                (double) sequentialNanos
                        / parallelNanos;

        System.out.printf(
                """
                FERMINET_VMC_PARALLEL_BENCHMARK
                  available_processors=%d
                  parallelism=%d
                  walkers=%d
                  sequential_seconds=%.6f
                  parallel_seconds=%.6f
                  speedup=%.4fx
                  acceptance=%.17g

                """,
                Runtime.getRuntime().availableProcessors(),
                parallelism,
                fixture.walkers.size(),
                sequentialNanos / 1.0e9,
                parallelNanos / 1.0e9,
                speedup,
                sequential.acceptance());
    }

    private static Fixture fixture() {
        Molecule molecule = water();

        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.testFixture();

        FermiNetV1State state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        FermiNetParameters.initialize(
                                new FermiNetParameterLayout(
                                        configuration,
                                        molecule),
                                44017L));

        List<QuantumCoordinates> walkers =
                new ArrayList<>();

        for (int i = 0; i < 32; i++) {
            walkers.add(
                    coordinates(
                            (i - 15.5)
                                    * 0.002));
        }

        return new Fixture(state, List.copyOf(walkers));
    }

    private static QuantumCoordinates coordinates(double shift) {
        double[][] xyz = {
                {.18, .11, .27},
                {-.31, .42, -.16},
                {.57, -.28, .33},
                {-.63, -.37, .21},
                {.24, .71, -.45},
                {-.22, -.15, -.38},
                {.36, -.54, .19},
                {-.48, .26, .51},
                {.69, .18, -.24},
                {-.12, .61, .37}
        };

        List<QuantumCoordinates.ParticleCoordinate> result =
                new ArrayList<>();

        for (int i = 0; i < xyz.length; i++) {
            double signed =
                    i % 2 == 0
                            ? shift
                            : -shift;

            result.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            i,
                            xyz[i][0] + signed,
                            xyz[i][1] - .5 * signed,
                            xyz[i][2] + .25 * signed,
                            i < 5
                                    ? SpinProjection.ALPHA
                                    : SpinProjection.BETA));
        }

        return new QuantumCoordinates(result);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-vmc-parallel-benchmark-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(8),
                                new CartesianPosition(
                                        0,
                                        0,
                                        0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0,
                                        0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -.46464225035067114,
                                        1.7340684963325879,
                                        0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }

    private record Fixture(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers) {
    }
}