package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable persistent curvature and factorization state for FermiNet KFAC. */
public final class FermiNetKfacState {

    private final int iteration;
    private final Map<String, DenseBlock> denseBlocks;
    private final Map<String, DiagonalBlock> diagonalBlocks;

    public FermiNetKfacState() {
        this(0, Map.of(), Map.of());
    }

    FermiNetKfacState(
            int iteration,
            Map<String, DenseBlock> denseBlocks,
            Map<String, DiagonalBlock> diagonalBlocks) {
        if (iteration < 0) {
            throw new IllegalArgumentException("negative KFAC iteration");
        }
        this.iteration = iteration;
        this.denseBlocks = Map.copyOf(new LinkedHashMap<>(denseBlocks));
        this.diagonalBlocks = Map.copyOf(new LinkedHashMap<>(diagonalBlocks));
    }

    public int iteration() {
        return iteration;
    }

    Map<String, DenseBlock> denseBlocks() {
        return denseBlocks;
    }

    Map<String, DiagonalBlock> diagonalBlocks() {
        return diagonalBlocks;
    }

    record DenseBlock(
            int inputs,
            int outputs,
            double[] inputFactor,
            double[] outputFactor,
            double[] dampedInputCholesky,
            double[] dampedOutputCholesky,
            int factorizationIteration) {
        DenseBlock {
            if (inputs < 1 || outputs < 1 || factorizationIteration < 0) {
                throw new IllegalArgumentException("invalid dense KFAC block");
            }
            inputFactor = copy(inputFactor, Math.multiplyExact(inputs, inputs));
            outputFactor = copy(outputFactor, Math.multiplyExact(outputs, outputs));
            dampedInputCholesky = copy(
                    dampedInputCholesky, Math.multiplyExact(inputs, inputs));
            dampedOutputCholesky = copy(
                    dampedOutputCholesky, Math.multiplyExact(outputs, outputs));
        }

        @Override public double[] inputFactor() { return inputFactor.clone(); }
        @Override public double[] outputFactor() { return outputFactor.clone(); }
        @Override public double[] dampedInputCholesky() { return dampedInputCholesky.clone(); }
        @Override public double[] dampedOutputCholesky() { return dampedOutputCholesky.clone(); }
    }

    record DiagonalBlock(double[] curvature) {
        DiagonalBlock {
            curvature = copy(curvature, curvature.length);
            if (curvature.length == 0) {
                throw new IllegalArgumentException("empty diagonal KFAC block");
            }
        }

        @Override public double[] curvature() { return curvature.clone(); }
    }

    private static double[] copy(double[] values, int expectedLength) {
        Objects.requireNonNull(values, "values");
        if (values.length != expectedLength) {
            throw new IllegalArgumentException("KFAC state dimension mismatch");
        }
        double[] result = values.clone();
        for (double value : result) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("non-finite KFAC state");
            }
        }
        return result;
    }
}
