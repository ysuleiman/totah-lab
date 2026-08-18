package totah.lab.prometheus.neural.ferminet.reference;

import totah.lab.prometheus.neural.ferminet.runtime.*;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import java.io.IOException;
import java.util.List;

/**
 * Test support for exact serial/parallel SR observation parity.
 *
 * <p>Intentionally has no JUnit dependency so it can be called from the project's
 * existing test harness or from a temporary diagnostic main.
 */
final class FermiNetSrObservationFileParallelParity {

    private static final int PARAMETER_BLOCK = 256;

    private FermiNetSrObservationFileParallelParity() {}

    static void assertBitIdentical(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            int parallelism)
            throws IOException {

        try (FermiNetSrObservationFile serial =
                     FermiNetSrObservationFile.build(state, samples);
             FermiNetSrObservationFile parallel =
                     FermiNetSrObservationFile.buildParallel(
                             state,
                             samples,
                             parallelism)) {

            requireEqual(
                    "sample count",
                    serial.sampleCount(),
                    parallel.sampleCount());

            requireEqual(
                    "parameter count",
                    serial.parameterCount(),
                    parallel.parameterCount());

            requireEqual(
                    "derivative bytes",
                    serial.derivativeBytes(),
                    parallel.derivativeBytes());

            requireEqual(
                    "neural evaluations",
                    serial.neuralEvaluations(),
                    parallel.neuralEvaluations());

            for (int sample = 0;
                 sample < serial.sampleCount();
                 sample++) {

                requireBitsEqual(
                        "weight sample=" + sample,
                        serial.weight(sample),
                        parallel.weight(sample));

                requireBitsEqual(
                        "local energy sample=" + sample,
                        serial.localEnergyHartree(sample),
                        parallel.localEnergyHartree(sample));
            }

            int parameterCount = serial.parameterCount();
            int sampleCount = serial.sampleCount();

            for (int start = 0;
                 start < parameterCount;
                 start += PARAMETER_BLOCK) {

                int length =
                        Math.min(
                                PARAMETER_BLOCK,
                                parameterCount - start);

                double[] serialBlock =
                        new double[Math.multiplyExact(sampleCount, length)];

                double[] parallelBlock =
                        new double[Math.multiplyExact(sampleCount, length)];

                serial.readParameterBlock(
                        start,
                        length,
                        serialBlock);

                parallel.readParameterBlock(
                        start,
                        length,
                        parallelBlock);

                for (int sample = 0;
                     sample < sampleCount;
                     sample++) {

                    for (int local = 0;
                         local < length;
                         local++) {

                        int index =
                                sample * length + local;

                        requireBitsEqual(
                                "derivative sample="
                                        + sample
                                        + " parameter="
                                        + (start + local),
                                serialBlock[index],
                                parallelBlock[index]);
                    }
                }
            }
        }
    }

    private static void requireBitsEqual(
            String label,
            double expected,
            double actual) {

        long expectedBits =
                Double.doubleToLongBits(expected);

        long actualBits =
                Double.doubleToLongBits(actual);

        if (expectedBits != actualBits) {
            throw new AssertionError(
                    label
                            + " bit mismatch: expected="
                            + expected
                            + " actual="
                            + actual
                            + " expectedBits=0x"
                            + Long.toHexString(expectedBits)
                            + " actualBits=0x"
                            + Long.toHexString(actualBits));
        }
    }

    private static void requireEqual(
            String label,
            long expected,
            long actual) {

        if (expected != actual) {
            throw new AssertionError(
                    label
                            + " mismatch: expected="
                            + expected
                            + " actual="
                            + actual);
        }
    }
}