package totah.lab.athena.thermo;

/** Frozen convention: Kd_B / Kd_A; values below one favor B. */
public record SelectivityRatio(double kdBOverKdA) {
    public SelectivityRatio {
        if (!Double.isFinite(kdBOverKdA) || kdBOverKdA <= 0.0) {
            throw new IllegalArgumentException("selectivity ratio must be positive");
        }
    }
}
