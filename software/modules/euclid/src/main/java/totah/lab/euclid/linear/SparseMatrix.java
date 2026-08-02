package totah.lab.euclid.linear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sparse symmetric matrix in HashMap-CSR form.
 */
public class SparseMatrix implements Matrix {
    public final List<Map<Integer, Double>> rows;
    public final int size;

    public SparseMatrix(int size) {
        this.size = size;
        this.rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new HashMap<>());
        }
    }

    @Override
    public int size() {
        return size;
    }

    public void set(int row, int col, double value) {
        rows.get(row).put(col, value);
    }

    @Override
    public double get(int row, int col) {
        return rows.get(row).getOrDefault(col, 0.0);
    }

    @Override
    public double[] multiply(double[] x) {
        double[] y = new double[size];
        for (int i = 0; i < size; i++) {
            double sum = 0.0;
            for (Map.Entry<Integer, Double> e : rows.get(i).entrySet()) {
                sum += e.getValue() * x[e.getKey()];
            }
            y[i] = sum;
        }
        return y;
    }

    public double[] getDiagonal() {
        double[] diag = new double[size];
        for (int i = 0; i < size; i++) {
            diag[i] = get(i, i);
        }
        return diag;
    }

    public double[][] toDense(int fullSize) {
        double[][] M = new double[fullSize][fullSize];
        for (int i = 0; i < size && i < fullSize; i++) {
            for (Map.Entry<Integer, Double> e : rows.get(i).entrySet()) {
                int j = e.getKey();
                if (j < fullSize) {
                    M[i][j] = e.getValue();
                }
            }
        }
        return M;
    }
}
