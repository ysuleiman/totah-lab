package totah.lab.hephaestus.receptor.residue;
import java.util.Objects;

public record ResidueState(
        String chainId,
        int residueNumber,
        Character insertionCode,
        String originalResidueName,
        String preparedResidueName,
        String amberTemplateName,
        boolean nTerminus,
        boolean cTerminus,
        boolean disulfide,
        String note) {

    public ResidueState {
        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(
                originalResidueName,
                "originalResidueName");
        Objects.requireNonNull(
                preparedResidueName,
                "preparedResidueName");
        Objects.requireNonNull(
                amberTemplateName,
                "amberTemplateName");

        chainId = requireNonBlank(chainId, "chainId");
        originalResidueName =
                requireNonBlank(
                        originalResidueName,
                        "originalResidueName");
        preparedResidueName =
                requireNonBlank(
                        preparedResidueName,
                        "preparedResidueName");
        amberTemplateName =
                requireNonBlank(
                        amberTemplateName,
                        "amberTemplateName");

        if (insertionCode != null
                && Character.isWhitespace(insertionCode)) {
            insertionCode = null;
        }

        if (nTerminus && cTerminus) {
            throw new IllegalArgumentException(
                    "A residue cannot be both N- and C-terminal.");
        }

        note = note == null ? "" : note.trim();
    }

    public String residueKey() {
        return chainId
                + ":"
                + residueNumber
                + (insertionCode == null
                ? ""
                : insertionCode);
    }

    private static String requireNonBlank(
            String value,
            String fieldName) {

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank.");
        }

        return normalized;
    }
}