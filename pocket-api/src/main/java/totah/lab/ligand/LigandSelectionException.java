package totah.lab.ligand;

import java.util.Objects;

public final class LigandSelectionException extends IllegalArgumentException {

    private final LigandSelectionFailure failure;

    public LigandSelectionException(
            LigandSelectionFailure failure,
            String detail) {
        super(message(failure, detail));
        this.failure = Objects.requireNonNull(failure, "failure is null");
    }

    public LigandSelectionFailure getFailure() {
        return failure;
    }

    private static String message(
            LigandSelectionFailure failure,
            String detail) {
        Objects.requireNonNull(failure, "failure is null");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail is blank");
        }
        return "Ligand selection failed [" + failure + "]: " + detail;
    }
}
