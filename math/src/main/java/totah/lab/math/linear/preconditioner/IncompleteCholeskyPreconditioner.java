package totah.lab.math.linear.preconditioner;


import totah.lab.math.linear.Preconditioner;
import totah.lab.math.linear.SparseMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Incomplete Cholesky (IC0) preconditioner.
 * Factor A ≈ L·L^T where L has same sparsity as A's lower triangle.
 */
public class IncompleteCholeskyPreconditioner implements Preconditioner {
    private final SparseMatrix L;
    private final double[] D;

    public IncompleteCholeskyPreconditioner(SparseMatrix A, int n) {
        this.L = new SparseMatrix(n);
        this.D = new double[n];

        for (int i = 0; i < n; i++) {
            Map<Integer, Double> row = A.rows.get(i);
            double diag = row.getOrDefault(i, 1.0);
            double sum = 0.0;

            List<Integer> cols = new ArrayList<>();
            List<Double> vals = new ArrayList<>();

            // HashMap iteration order is arbitrary; the diagonal entry (j == i)
            // must be processed after all j < i so its correction term
            // includes every L_ik. Sort by column to make this deterministic.
            List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(row.entrySet());
            sorted.sort(Map.Entry.comparingByKey());

            for (Map.Entry<Integer, Double> e : sorted) {
                int j = e.getKey();
                double aij = e.getValue();
                if (j > i) continue;

                if (j == i) {
                    for (int k = 0; k < cols.size(); k++) {
                        int ck = cols.get(k);
                        double lik = vals.get(k);
                        double ljk = L.get(j, ck);
                        sum += lik * ljk;
                    }
                    double d = diag - sum;
                    if (d <= 0) d = Math.abs(diag) * 0.001 + 1e-6;
                    D[i] = Math.sqrt(d);
                    L.set(i, i, D[i]);
                } else {
                    double s = 0.0;
                    for (int k = 0; k < cols.size(); k++) {
                        int ck = cols.get(k);
                        if (L.rows.get(j).containsKey(ck)) {
                            s += vals.get(k) * L.get(j, ck);
                        }
                    }
                    double lij = (aij - s) / D[j];
                    L.set(i, j, lij);
                    cols.add(j);
                    vals.add(lij);
                }
            }
        }
    }

    @Override
    public double[] apply(double[] r) {
        int n = r.length;
        double[] y = new double[n];
        double[] z = new double[n];

        // Forward substitution: L·y = r
        for (int i = 0; i < n; i++) {
            double sum = r[i];
            for (Map.Entry<Integer, Double> e : L.rows.get(i).entrySet()) {
                int j = e.getKey();
                if (j < i) sum -= e.getValue() * y[j];
            }
            y[i] = sum / L.get(i, i);
        }

        // Backward substitution: L^T·z = y
        for (int i = n - 1; i >= 0; i--) {
            double sum = y[i];
            for (int j = i + 1; j < n; j++) {
                if (L.rows.get(j).containsKey(i)) {
                    sum -= L.get(j, i) * z[j];
                }
            }
            z[i] = sum / L.get(i, i);
        }
        return z;
    }
}
