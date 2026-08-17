package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Enforces explicit SAM presence and physical ligand-SAM coexistence. */
public final class SamCompatibilityGate {

    public record Evidence(boolean samExplicitlyPresent,
                           boolean sameDockingProtocolForIsoforms,
                           boolean severeHeavyAtomClash,
                           boolean artificiallyDisplacesSam,
                           boolean deliberateSamCompetitiveControl) {
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
        if (!evidence.samExplicitlyPresent()) {
            reasons.add("docking receptor did not explicitly include SAM");
        }
        if (!evidence.sameDockingProtocolForIsoforms()) {
            reasons.add("TMT1A and TMT1B did not use the same ligand protocol and search effort");
        }
        if (evidence.severeHeavyAtomClash()) {
            reasons.add("ligand has a severe heavy-atom clash with SAM");
        }
        if (evidence.artificiallyDisplacesSam()
                && !evidence.deliberateSamCompetitiveControl()) {
            reasons.add("ligand artificially displaces SAM");
        }
        return new Result(reasons.isEmpty(), reasons);
    }
}
