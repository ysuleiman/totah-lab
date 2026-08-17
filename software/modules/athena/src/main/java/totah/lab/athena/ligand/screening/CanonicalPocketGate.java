package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tests reproducible binding in the canonical SAM/substrate pocket. */
public final class CanonicalPocketGate {

    public record Evidence(
            boolean canonicalSiteIsPredominant,
            boolean artificialSamOverlap,
            boolean severeProteinClash,
            boolean strainedLigandGeometry) {
    }

    public record Result(boolean accepted, List<String> reasons) {
        public Result {
            reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
            if (accepted == !reasons.isEmpty()) {
                throw new IllegalArgumentException(
                        "accepted must be equivalent to having no reasons");
            }
        }
    }

    public Result evaluate(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        List<String> reasons = new ArrayList<>();
        if (!evidence.canonicalSiteIsPredominant()) {
            reasons.add("best pose families are not predominantly in the canonical site");
        }
        if (evidence.artificialSamOverlap()) {
            reasons.add("pose occupies an artificial SAM-overlap volume");
        }
        if (evidence.severeProteinClash()) {
            reasons.add("pose contains a severe protein clash");
        }
        if (evidence.strainedLigandGeometry()) {
            reasons.add("pose requires strained ligand geometry");
        }
        return new Result(reasons.isEmpty(), reasons);
    }
}
