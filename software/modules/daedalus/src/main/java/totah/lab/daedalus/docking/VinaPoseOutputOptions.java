package totah.lab.daedalus.docking;

/** Explicit Vina controls for retained pose count and score-window output. */
public record VinaPoseOutputOptions(
        int maximumModes,
        double energyRangeKcalPerMol) {

    public VinaPoseOutputOptions {
        if (maximumModes < 1) {
            throw new IllegalArgumentException(
                    "maximumModes must be at least 1");
        }
        if (!Double.isFinite(energyRangeKcalPerMol)
                || energyRangeKcalPerMol <= 0.0) {
            throw new IllegalArgumentException(
                    "energyRangeKcalPerMol must be positive and finite");
        }
    }
}
