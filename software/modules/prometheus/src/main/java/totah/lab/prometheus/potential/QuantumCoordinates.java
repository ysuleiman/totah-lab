package totah.lab.prometheus.potential;

import java.util.Arrays;

/** Immutable Cartesian coordinates in angstrom, in canonical atom order. */
public final class QuantumCoordinates {
    private final double[][] positions;

    public QuantumCoordinates(double[][] positions) {
        if (positions == null || positions.length == 0) throw new IllegalArgumentException("positions required");
        this.positions = new double[positions.length][3];
        for (int atom = 0; atom < positions.length; atom++) {
            if (positions[atom] == null || positions[atom].length != 3) throw new IllegalArgumentException("three coordinates required per atom");
            for (int axis = 0; axis < 3; axis++) {
                double value = positions[atom][axis];
                if (!Double.isFinite(value)) throw new IllegalArgumentException("coordinates must be finite");
                this.positions[atom][axis] = value;
            }
        }
    }

    public int atomCount() { return positions.length; }
    public double coordinate(int atom, int axis) { return positions[atom][axis]; }
    public double[][] positions() { return Arrays.stream(positions).map(double[]::clone).toArray(double[][]::new); }
}
