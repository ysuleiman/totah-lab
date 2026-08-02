package totah.lab.gaia.structure;

import java.util.Objects;

/** Canonical identity of a residue within a molecular structure. */
public record ResidueId(
        String chainId,
        int residueNumber,
        Character insertionCode) {

    public ResidueId {
        Objects.requireNonNull(chainId, "chainId");
        chainId = chainId.trim();
        if (chainId.isEmpty()) {
            throw new IllegalArgumentException(
                    "chainId must not be blank.");
        }
        if (insertionCode != null
                && Character.isWhitespace(insertionCode)) {
            insertionCode = null;
        }
    }
}
