package totah.lab.ligand;

import java.util.Objects;

public final class UnsupportedLigandException extends IllegalArgumentException {

    private final String componentId;
    private final LigandUnsupportedReason reason;

    public UnsupportedLigandException(
            String componentId,
            LigandUnsupportedReason reason,
            String detail) {
        super(message(componentId, reason, detail));
        this.componentId = requireComponentId(componentId);
        this.reason = Objects.requireNonNull(reason, "reason is null");
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
        String id = requireComponentId(componentId);
        Objects.requireNonNull(reason, "reason is null");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail is blank");
        }
        return "Unsupported ligand " + id + " [" + reason + "]: " + detail;
    }

    private static String requireComponentId(String componentId) {
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("componentId is blank");
        }
        return componentId.trim();
    }
}
