package totah.lab.aether.matrix;

import java.util.Arrays;
import java.util.Comparator;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

/** Private auditable Lowdin path. No backend object escapes the scientific API. */
final class OneShotNumerics {
    /** Solver portion only: reusable by RHF and KS without claiming an RHF Fock equation. */
    static final String PROTOCOL="bohr;hartree;binary64;"+OneShotRhfCalculator.PROTOCOL.substring(OneShotRhfCalculator.PROTOCOL.indexOf("Lowdin:"));
    static final double SYMMETRY_TOLERANCE = 1e-12;
    static final double RANK_ABSOLUTE = 1e-12;
    static final double RANK_RELATIVE = 1e-10;
    static final double VALIDATION_TOLERANCE = 1e-10;

    private OneShotNumerics() {}

    static Solution solve(RealMatrix suppliedS, RealMatrix suppliedF) {
        RealMatrix s = checkedSymmetric(suppliedS, true);
        RealMatrix f = checkedSymmetric(suppliedF, false);
        if (s.getRowDimension() != f.getRowDimension()) throw failure("S/F dimensions differ");
        long overlapStarted = System.nanoTime();
        Eigenpairs overlap = eigenpairs(s);
        double largest = overlap.values()[overlap.values().length - 1];
        double cutoff = StrictMath.max(RANK_ABSOLUTE, RANK_RELATIVE * largest);
        if (overlap.values()[0] <= cutoff) throw failure("overlap is not positive definite or is numerically rank deficient; no regularization");
        long overlapTime = System.nanoTime() - overlapStarted;

        long transformStarted = System.nanoTime();
        double[] inverseRoots = Arrays.stream(overlap.values()).map(v -> 1 / StrictMath.sqrt(v)).toArray();
        // X = U diag(s^-1/2) U^T: explicit symmetric Lowdin orthogonalization.
        RealMatrix x = checkedSymmetric(overlap.vectors().multiply(new DiagonalMatrix(inverseRoots))
                .multiply(overlap.vectors().transpose()), false);
        double orthogonalizationError = identityError(x.transpose().multiply(s).multiply(x));
        if (orthogonalizationError > VALIDATION_TOLERANCE) throw failure("X^T S X normalization failure");
        // F' = X^T F X, exposed as typed evidence.
        RealMatrix transformed = checkedSymmetric(x.transpose().multiply(f).multiply(x), false);
        long transformTime = System.nanoTime() - transformStarted;

        long fockStarted = System.nanoTime();
        Eigenpairs orbitals = eigenpairs(transformed);
        long fockTime = System.nanoTime() - fockStarted;
        // C = X C', followed by a deterministic sign choice in the original AO basis.
        RealMatrix c = x.multiply(orbitals.vectors());
        canonicalize(c, orbitals.vectors());
        finite(c);
        double residual = maxAbs(f.multiply(c).subtract(s.multiply(c).multiply(new DiagonalMatrix(orbitals.values()))));
        double scale = StrictMath.max(1, s.getRowDimension() * maxAbs(c)
                * StrictMath.max(maxAbs(f), maxAbs(s) * maxAbs(orbitals.values())));
        if (!Double.isFinite(scale) || residual / scale > VALIDATION_TOLERANCE) throw failure("generalized eigen residual failure");
        double normalization = identityError(c.transpose().multiply(s).multiply(c));
        if (normalization > VALIDATION_TOLERANCE) throw failure("C^T S C eigenvector normalization failure");
        return new Solution(f, overlap.values(), x, transformed, c, orbitals.values(), residual,
                normalization, orthogonalizationError, overlapTime, transformTime, fockTime);
    }

    static RealMatrix checkedSymmetric(RealMatrix input, boolean exact) {
        finite(input);
        if (input.getRowDimension() == 0 || input.getRowDimension() != input.getColumnDimension()) throw failure("expected nonempty square matrix");
        RealMatrix result = input.copy();
        double tolerance = exact ? 0 : SYMMETRY_TOLERANCE * StrictMath.max(1, maxAbs(input));
        for (int i = 0; i < input.getRowDimension(); i++) {
            for (int j = i + 1; j < input.getColumnDimension(); j++) {
                double a = input.getEntry(i,j), b = input.getEntry(j,i);
                if (StrictMath.abs(a - b) > tolerance) throw failure(exact ? "non-symmetric S" : "non-symmetric F/transformation beyond tolerance");
                // Only roundoff-level F/transformation asymmetry is explicitly averaged. S is never changed.
                double value = exact ? a : a / 2 + b / 2;
                result.setEntry(i,j,value); result.setEntry(j,i,value);
            }
        }
        return result;
    }

    static Eigenpairs eigenpairs(RealMatrix matrix) {
        EigenDecomposition decomposition;
        try {
            decomposition = new EigenDecomposition(matrix);
        } catch (RuntimeException exception) {
            var failure = failure("symmetric eigendecomposition failed"); failure.initCause(exception); throw failure;
        }
        int n = matrix.getRowDimension();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            double real = decomposition.getRealEigenvalue(i), imaginary = decomposition.getImagEigenvalue(i);
            if (!Double.isFinite(real) || !Double.isFinite(imaginary) || imaginary != 0) throw failure("nonfinite or complex eigenvalue");
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> decomposition.getRealEigenvalue(i)).thenComparingInt(i -> i));
        double[] values = new double[n];
        RealMatrix vectors = new Array2DRowRealMatrix(n,n);
        for (int column = 0; column < n; column++) {
            values[column] = decomposition.getRealEigenvalue(order[column]);
            vectors.setColumnVector(column, decomposition.getEigenvector(order[column]));
        }
        var pairs = new Eigenpairs(values, vectors);
        validateEigenpairs(matrix, pairs);
        return pairs;
    }

    static void validateEigenpairs(RealMatrix matrix, Eigenpairs pairs) {
        for (double value : pairs.values()) if (!Double.isFinite(value)) throw failure("nonfinite eigenvalue");
        finite(pairs.vectors());
        if (pairs.values().length != matrix.getRowDimension() || pairs.vectors().getRowDimension() != matrix.getRowDimension()
                || pairs.vectors().getColumnDimension() != pairs.values().length) throw failure("eigenpair dimensions differ");
        double normalization = identityError(pairs.vectors().transpose().multiply(pairs.vectors()));
        if (normalization > VALIDATION_TOLERANCE) throw failure("eigenvector normalization failure");
        double residual = maxAbs(matrix.multiply(pairs.vectors()).subtract(pairs.vectors().multiply(new DiagonalMatrix(pairs.values()))));
        double scale = StrictMath.max(1, matrix.getRowDimension() * maxAbs(matrix));
        if (!Double.isFinite(scale) || residual / scale > VALIDATION_TOLERANCE) throw failure("eigenpair residual failure");
    }

    static void canonicalize(RealMatrix ao, RealMatrix orthogonal) {
        finite(ao); finite(orthogonal);
        for (int column = 0; column < ao.getColumnDimension(); column++) {
            int pivot = 0;
            for (int row = 1; row < ao.getRowDimension(); row++) {
                if (StrictMath.abs(ao.getEntry(row,column)) > StrictMath.abs(ao.getEntry(pivot,column))) pivot = row;
            }
            if (ao.getEntry(pivot,column) == 0) throw failure("zero eigenvector");
            if (ao.getEntry(pivot,column) < 0) {
                for (int row = 0; row < ao.getRowDimension(); row++) {
                    ao.multiplyEntry(row,column,-1); orthogonal.multiplyEntry(row,column,-1);
                }
            }
        }
    }

    static double identityError(RealMatrix matrix) {
        return maxAbs(matrix.subtract(MatrixUtils.createRealIdentityMatrix(matrix.getRowDimension())));
    }

    static double maxAbs(RealMatrix matrix) {
        finite(matrix);
        double max = 0;
        for (int i = 0; i < matrix.getRowDimension(); i++) for (int j = 0; j < matrix.getColumnDimension(); j++) max = StrictMath.max(max, StrictMath.abs(matrix.getEntry(i,j)));
        return max;
    }

    private static double maxAbs(double[] values) {
        return Arrays.stream(values).map(StrictMath::abs).max().orElseThrow();
    }

    static void finite(RealMatrix matrix) {
        for (int i = 0; i < matrix.getRowDimension(); i++) for (int j = 0; j < matrix.getColumnDimension(); j++) {
            if (!Double.isFinite(matrix.getEntry(i,j))) throw failure("nonfinite matrix/eigenvector");
        }
    }

    private static ArithmeticException failure(String reason) { return new ArithmeticException("NUMERICAL_FAILURE: " + reason); }

    record Eigenpairs(double[] values, RealMatrix vectors) {}
    record Solution(RealMatrix fock, double[] overlapEigenvalues, RealMatrix orthogonalization,
                    RealMatrix orthogonalFock, RealMatrix coefficients, double[] energies,
                    double generalizedResidual, double orthonormalityError, double orthogonalizationError,
                    long overlapTime, long transformTime, long fockTime) {}
}
