package totah.lab.prometheus.neural;

import java.util.Arrays;

/** Immutable row-major rank-2 parameter tensor owned by Prometheus. */
public final class ParameterTensor {
    private final int rows; private final int columns; private final double[] values;

    private ParameterTensor(int rows, int columns, double[] values) {
        if (rows < 1 || columns < 1 || values.length != rows * columns) {
            throw new IllegalArgumentException("tensor shape/value mismatch");
        }
        this.rows=rows; this.columns=columns; this.values=values.clone();
    }

    public static ParameterTensor of(int rows, int columns, double... values) {
        return new ParameterTensor(rows, columns, values);
    }

    public int rows() { return rows; }
    public int columns() { return columns; }
    public double get(int row, int column) { return values[row * columns + column]; }
    public double[] toArray() { return values.clone(); }

    @Override public boolean equals(Object object) {
        return object instanceof ParameterTensor other && rows == other.rows && columns == other.columns
                && Arrays.equals(values, other.values);
    }

    @Override public int hashCode() { return 31 * (31 * rows + columns) + Arrays.hashCode(values); }
}
