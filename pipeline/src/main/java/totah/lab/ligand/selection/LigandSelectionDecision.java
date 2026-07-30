package totah.lab.ligand.selection;

import java.util.Objects;

public record LigandSelectionDecision(
        boolean eligible,
        LigandSelectionFailure failure,
        String reason
) {
    public LigandSelectionDecision {
        reason = Objects.requireNonNull(reason, "reason is null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason is blank");
        }
        if (eligible && failure != null) {
            throw new IllegalArgumentException("Eligible decision cannot have a failure");
        }
        if (!eligible && failure == null) {
            throw new IllegalArgumentException("Ineligible decision requires a failure");
        }
    }

    public static LigandSelectionDecision eligible(String reason) {
        return new LigandSelectionDecision(true, null, reason);
    }

    public static LigandSelectionDecision rejected(
            LigandSelectionFailure failure,
            String reason) {
        return new LigandSelectionDecision(false, failure, reason);
    }
}
