package totah.lab.euclid.linear.preconditioner;


import totah.lab.euclid.linear.Preconditioner;
import totah.lab.euclid.linear.SparseMatrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Block-Jacobi preconditioner: invert per-block diagonal submatrices.
 */
public class BlockJacobiPreconditioner implements Preconditioner {
    private final List<int[]> blocks;
    private final List<double[][]> invBlocks;

    public BlockJacobiPreconditioner(SparseMatrix H, List<int[]> blocks) {
        validateCoverage(H.size, blocks);
        this.blocks = new ArrayList<>(blocks.size());
        this.invBlocks = new ArrayList<>();

        for (int[] blockIdx : blocks) {
            this.blocks.add(blockIdx.clone());
            int size = blockIdx.length;
            double[][] sub = new double[size][size];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    sub[i][j] = H.get(blockIdx[i], blockIdx[j]);
                }
            }
            invBlocks.add(invertDense(sub));
        }
    }

    @Override
    public double[] apply(double[] r) {
        double[] z = new double[r.length];
        for (int b = 0; b < blocks.size(); b++) {
            int[] idx = blocks.get(b);
            double[][] inv = invBlocks.get(b);
            for (int i = 0; i < idx.length; i++) {
                double sum = 0.0;
                for (int j = 0; j < idx.length; j++) {
                    sum += inv[i][j] * r[idx[j]];
                }
                z[idx[i]] = sum;
            }
        }
        return z;
    }

    /**
     * Blocks must partition [0, n): an uncovered index would silently stay
     * zero in apply(), and an overlapping index would be written twice.
     */
    private static void validateCoverage(int n, List<int[]> blocks) {
        boolean[] covered = new boolean[n];
        for (int[] block : blocks) {
            for (int idx : block) {
                if (idx < 0 || idx >= n) {
                    throw new IllegalArgumentException(
                            "Block index " + idx + " is out of range [0, " + n + ")");
                }
                if (covered[idx]) {
                    throw new IllegalArgumentException(
                            "Index " + idx + " is covered by more than one block");
                }
                covered[idx] = true;
            }
        }
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!covered[i]) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Blocks do not cover every index in [0, " + n + "); missing: " + missing);
        }
    }

    private double[][] invertDense(double[][] A) {
        int n = A.length;
        double[][] M = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            M[i][n + i] = 1.0;
        }

        for (int col = 0; col < n; col++) {
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(M[row][col]) > Math.abs(M[maxRow][col])) {
                    maxRow = row;
                }
            }
            double[] tmp = M[col];
            M[col] = M[maxRow];
            M[maxRow] = tmp;

            double piv = M[col][col];
            if (Math.abs(piv) < 1e-12) {
                // Singular block, return identity fallback
                double[][] id = new double[n][n];
                for (int i = 0; i < n; i++) id[i][i] = 1.0;
                return id;
            }
            for (int j = col; j < 2 * n; j++) {
                M[col][j] /= piv;
            }
            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = M[row][col];
                for (int j = col; j < 2 * n; j++) {
                    M[row][j] -= factor * M[col][j];
                }
            }
        }

        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(M[i], n, inv[i], 0, n);
        }
        return inv;
    }
}