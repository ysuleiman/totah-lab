package totah.lab.prometheus.potential;

import java.util.Arrays;

/** Energy in kcal/mol and forces in kcal/mol/angstrom. */
public final class PotentialEvaluation {
    private final double energy;
    private final double[][] forces;

    public PotentialEvaluation(double energy, double[][] forces) {
        if (!Double.isFinite(energy) || forces == null) throw new IllegalArgumentException("finite evaluation required");
        this.energy = energy;
        this.forces = Arrays.stream(forces).map(row -> {
            if (row == null || row.length != 3) throw new IllegalArgumentException("three force components required");
            double[] copy = row.clone();
            for (double value : copy) if (!Double.isFinite(value)) throw new IllegalArgumentException("forces must be finite");
            return copy;
        }).toArray(double[][]::new);
    }

    public double energy() { return energy; }
    public double[][] forces() { return Arrays.stream(forces).map(double[]::clone).toArray(double[][]::new); }
}
