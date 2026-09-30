package totah.lab.aether.basis;

/** Authorized Cartesian angular functions only; kinetic recurrences may use higher intermediate powers. */
public enum CartesianAngularMomentum {
    S(0,0,0), PX(1,0,0), PY(0,1,0), PZ(0,0,1), DXX(2,0,0), DXY(1,1,0), DXZ(1,0,1), DYY(0,2,0), DYZ(0,1,1), DZZ(0,0,2);
    private final int x, y, z;
    CartesianAngularMomentum(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    private static int oddFactorial(int power) {
        int result = 1;
        for (int k = 1; k <= power; k++) result *= 2 * k - 1;
        return result;
    }
    /** Converts the existing normalized s envelope into a normalized Cartesian primitive. */
    public double normalization(PrimitiveGaussian radial) {
        int degree = x + y + z;
        // Preserve the frozen s/p arithmetic; higher powers use the same Cartesian normalization law.
        double factor = degree < 2 ? (this == S ? 1 : StrictMath.sqrt(4 * radial.exponent()))
                : StrictMath.pow(4 * radial.exponent(), degree / 2.0)
                  / StrictMath.sqrt(oddFactorial(x) * oddFactorial(y) * oddFactorial(z));
        double value = radial.normalization() * factor;
        if (!Double.isFinite(value) || value == 0) throw new IllegalArgumentException("Unrepresentable Cartesian normalization");
        return value;
    }
}
