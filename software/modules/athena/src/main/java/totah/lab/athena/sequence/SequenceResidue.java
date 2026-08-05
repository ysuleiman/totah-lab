package totah.lab.athena.sequence;

import java.util.Objects;

/**
 * One residue of a protein sequence: its residue number (as found in
 * the source structure) and its three-letter residue name.
 */
public record SequenceResidue(
        int residueNumber,
        String residueName
) {

    public SequenceResidue {
        Objects.requireNonNull(residueName, "residueName");

        residueName = residueName.trim();

        if (residueName.isEmpty()) {
            throw new IllegalArgumentException(
                    "residueName must not be blank"
            );
        }
    }
}
