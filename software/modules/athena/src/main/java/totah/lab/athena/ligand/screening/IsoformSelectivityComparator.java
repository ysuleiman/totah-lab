package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies the orthogonal TMT1B-over-TMT1A discrimination rule. */
public final class IsoformSelectivityComparator {

    public record Evidence(
            boolean betterCanonicalSiteReproducibility,
            boolean coherentPoseFamilyAbsentInTmt1a,
            boolean betterPocketOccupancy,
            boolean fewerClashes,
            boolean tmt1bSpecificInteraction,
            boolean exploitsTmt1bSubstitutionSpace,
            boolean tmt1aEscapesOrIsIncompatible,
            boolean materiallyDifferentSamPlacement,
            Double dockingScoreDifference) {

        public Evidence {
            if (dockingScoreDifference != null
                    && !Double.isFinite(dockingScoreDifference)) {
                throw new IllegalArgumentException(
                        "dockingScoreDifference must be finite when present");
            }
        }
    }

    public record Result(boolean selectiveEvidencePresent,
                         List<String> dimensions,
                         Double dockingScoreDifference) {
        public Result {
            dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
            if (selectiveEvidencePresent == dimensions.isEmpty()) {
                throw new IllegalArgumentException(
                        "selectiveEvidencePresent requires an orthogonal dimension");
            }
        }
    }

    public Result compare(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        List<String> dimensions = new ArrayList<>();
        add(evidence.betterCanonicalSiteReproducibility(),
                "better canonical-site reproducibility", dimensions);
        add(evidence.coherentPoseFamilyAbsentInTmt1a(),
                "coherent TMT1B pose family absent in TMT1A", dimensions);
        add(evidence.betterPocketOccupancy(), "better pocket occupancy", dimensions);
        add(evidence.fewerClashes(), "fewer clashes", dimensions);
        add(evidence.tmt1bSpecificInteraction(),
                "TMT1B-specific interaction", dimensions);
        add(evidence.exploitsTmt1bSubstitutionSpace(),
                "exploits space created by TMT1B substitutions", dimensions);
        add(evidence.tmt1aEscapesOrIsIncompatible(),
                "TMT1A poses escape or are geometrically incompatible", dimensions);
        add(evidence.materiallyDifferentSamPlacement(),
                "materially different SAM-relative placement", dimensions);
        return new Result(!dimensions.isEmpty(), dimensions,
                evidence.dockingScoreDifference());
    }

    private static void add(boolean present, String description,
                            List<String> dimensions) {
        if (present) {
            dimensions.add(description);
        }
    }
}
