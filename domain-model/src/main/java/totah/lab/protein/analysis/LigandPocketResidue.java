package totah.lab.protein.analysis;

import java.util.Objects;

public record LigandPocketResidue(
        int tokenIndex,
        String chain,
        int residueNumber,
        String residueName,
        double minimumDistance,
        int contactingAtomPairCount
) {

    public LigandPocketResidue {
        if (tokenIndex < 0) {
            throw new IllegalArgumentException(
                    "tokenIndex must not be negative"
            );
        }
        chain = Objects.requireNonNull(chain, "chain");
        if (residueNumber < 1) {
            throw new IllegalArgumentException(
                    "residueNumber must be positive"
            );
        }
        residueName = Objects.requireNonNull(residueName, "residueName");
        if (!Double.isFinite(minimumDistance) || minimumDistance < 0.0) {
            throw new IllegalArgumentException(
                    "minimumDistance must be finite and non-negative"
            );
        }
        if (contactingAtomPairCount < 1) {
            throw new IllegalArgumentException(
                    "contactingAtomPairCount must be positive"
            );
        }
    }
}
