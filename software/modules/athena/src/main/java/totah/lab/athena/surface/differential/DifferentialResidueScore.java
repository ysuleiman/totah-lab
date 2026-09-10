package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;
import java.util.Optional;

/** Directional residue-level output with every SurfDiff score kept separate. */
public record DifferentialResidueScore(
        ResidueId queryResidue,
        Optional<ResidueId> subjectResidue,
        double relativeSasa,
        double exposureWeight,
        double rup,
        double rus,
        double rss) {
    public DifferentialResidueScore {
        Objects.requireNonNull(queryResidue, "queryResidue");
        subjectResidue = Objects.requireNonNull(subjectResidue, "subjectResidue");
        requireUnit(relativeSasa, "relativeSasa", false);
        requireUnit(exposureWeight, "exposureWeight", true);
        requireUnit(rup, "rup", true);
        requireUnit(rus, "rus", true);
        requireUnit(rss, "rss", true);
    }

    private static void requireUnit(double value, String name, boolean bounded) {
        if (!Double.isFinite(value) || value < 0.0 || (bounded && value > 1.0)) {
            throw new IllegalArgumentException(name + " is outside its valid range");
        }
    }
}
