package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;

/** SurfDiff multi-subject minima for one query residue. */
public record AggregatedDifferentialResidueScore(
        ResidueId queryResidue,
        double minimumRup,
        double minimumRus,
        double minimumRss) {
    public AggregatedDifferentialResidueScore {
        Objects.requireNonNull(queryResidue, "queryResidue");
        requireUnit(minimumRup, "minimumRup");
        requireUnit(minimumRus, "minimumRus");
        requireUnit(minimumRss, "minimumRss");
    }

    private static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be within [0, 1]");
        }
    }
}
