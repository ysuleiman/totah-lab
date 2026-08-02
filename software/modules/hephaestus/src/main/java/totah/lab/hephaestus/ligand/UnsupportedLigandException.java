package totah.lab.hephaestus.ligand;

import java.util.Objects;

/** Expected, machine-readable rejection at the ligand preparation boundary. */
public final class UnsupportedLigandException extends IllegalArgumentException {

    private final String componentId;
    private final LigandUnsupportedReason reason;

    public UnsupportedLigandException(
            String componentId,
            LigandUnsupportedReason reason,
            String detail,
            Throwable cause) {
        super(message(componentId, reason, detail), cause);
        this.componentId = componentId.trim();
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public UnsupportedLigandException(
            String componentId,
            LigandUnsupportedReason reason,
            String detail) {
        this(componentId, reason, detail, null);
    }

    public String getComponentId() {
        return componentId;
    }

    public LigandUnsupportedReason getReason() {
        return reason;
    }

    private static String message(
            String componentId,
            LigandUnsupportedReason reason,
            String detail) {
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("componentId must not be blank");
        }
        Objects.requireNonNull(reason, "reason");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail must not be blank");
        }
        return "Unsupported ligand " + componentId.trim() + " [" + reason + "]: " + detail;
    }
}
