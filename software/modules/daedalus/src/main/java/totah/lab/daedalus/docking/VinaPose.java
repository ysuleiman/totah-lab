package totah.lab.daedalus.docking;

/** One docking pose row from the AutoDock Vina result table. */
public record VinaPose(
        int mode,
        double affinityKcalPerMol,
        double rmsdLowerBound,
        double rmsdUpperBound) {

    public VinaPose {
        if (mode < 1) {
            throw new IllegalArgumentException("mode must be positive.");
        }
    }
}
