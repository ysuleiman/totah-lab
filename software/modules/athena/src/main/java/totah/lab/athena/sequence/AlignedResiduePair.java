package totah.lab.athena.sequence;

import java.util.Objects;

/**
 * One aligned pair of a protein sequence alignment: the query residue
 * at {@code queryResidueNumber} is aligned to the candidate residue at
 * {@code candidateResidueNumber}. Residue names may differ (a
 * substitution); gapped positions never appear as pairs.
 */
public record AlignedResiduePair(
        int queryResidueNumber,
        int candidateResidueNumber,
        String queryResidueName,
        String candidateResidueName
) {

    public AlignedResiduePair {
        Objects.requireNonNull(queryResidueName, "queryResidueName");
        Objects.requireNonNull(
                candidateResidueName,
                "candidateResidueName"
        );
    }
}
