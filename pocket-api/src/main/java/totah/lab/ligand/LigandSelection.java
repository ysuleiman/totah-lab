package totah.lab.ligand;

import totah.lab.protein.Residue;

import java.util.Objects;

public record LigandSelection(
        String componentId,
        String chain,
        int residueNumber,
        char insertionCode
) {
    public LigandSelection {
        componentId = requireText(componentId, "componentId");
        chain = Objects.requireNonNull(chain, "chain is null").trim();
    }

    public static LigandSelection from(Residue residue) {
        Objects.requireNonNull(residue, "residue is null");
        return new LigandSelection(
                residue.getName(),
                residue.getChain(),
                residue.getNumber(),
                insertionCode(residue));
    }

    public boolean matches(Residue residue) {
        Objects.requireNonNull(residue, "residue is null");
        return componentId.equalsIgnoreCase(residue.getName().trim())
                && chain.equals(residue.getChain().trim())
                && residueNumber == residue.getNumber()
                && insertionCode == insertionCode(residue);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is blank");
        }
        return value.trim();
    }

    private static char insertionCode(Residue residue) {
        return residue.getInsertionCode() == null ? ' ' : residue.getInsertionCode();
    }
}
