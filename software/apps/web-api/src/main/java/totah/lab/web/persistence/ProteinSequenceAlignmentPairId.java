package totah.lab.web.persistence;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link ProteinSequenceAlignmentPairEntity}:
 * the pair table's business unique key (alignment, query residue
 * number, candidate residue number).
 */
public class ProteinSequenceAlignmentPairId implements Serializable {

    private Long alignmentId;
    private Integer queryResidueNumber;
    private Integer candidateResidueNumber;

    protected ProteinSequenceAlignmentPairId() {
    }

    public ProteinSequenceAlignmentPairId(
            long alignmentId,
            int queryResidueNumber,
            int candidateResidueNumber
    ) {
        this.alignmentId = alignmentId;
        this.queryResidueNumber = queryResidueNumber;
        this.candidateResidueNumber = candidateResidueNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ProteinSequenceAlignmentPairId that)) {
            return false;
        }

        return Objects.equals(alignmentId, that.alignmentId)
                && Objects.equals(
                        queryResidueNumber,
                        that.queryResidueNumber
                )
                && Objects.equals(
                        candidateResidueNumber,
                        that.candidateResidueNumber
                );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                alignmentId,
                queryResidueNumber,
                candidateResidueNumber
        );
    }
}
