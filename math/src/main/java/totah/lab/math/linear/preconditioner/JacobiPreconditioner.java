package totah.lab.math.linear.preconditioner;


import totah.lab.math.linear.Preconditioner;

/**
 * Simple diagonal (Jacobi) preconditioner.
 */
public class JacobiPreconditioner implements Preconditioner {
    private final double[] invDiag;

    public JacobiPreconditioner(double[] diag) {
        this.invDiag = new double[diag.length];
        for (int i = 0; i < diag.length; i++) {
            invDiag[i] = (Math.abs(diag[i]) > 1e-10) ? 1.0 / diag[i] : 1.0;
        }
    }

    @Override
    public double[] apply(double[] r) {
        double[] z = new double[r.length];
        for (int i = 0; i < r.length; i++) {
            z[i] = invDiag[i] * r[i];
        }
        return z;
    }
}
