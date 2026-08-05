package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Maps docking.protein_sequence_alignment_pair: one aligned
 * (non-gapped) residue pair of a cached protein sequence alignment.
 * The primary key is the table's business unique key
 * (alignment, query residue number, candidate residue number); pairs
 * carry no surrogate id.
 */
@Entity
@Table(name = "protein_sequence_alignment_pair")
@IdClass(ProteinSequenceAlignmentPairId.class)
public class ProteinSequenceAlignmentPairEntity {

    @Id
    @Column(name = "alignment_id", nullable = false)
    private Long alignmentId;

    @Id
    @Column(name = "query_residue_number", nullable = false)
    private Integer queryResidueNumber;

    @Id
    @Column(name = "candidate_residue_number", nullable = false)
    private Integer candidateResidueNumber;

    @Column(name = "query_residue_name", length = 3)
    private String queryResidueName;

    @Column(name = "candidate_residue_name", length = 3)
    private String candidateResidueName;

    protected ProteinSequenceAlignmentPairEntity() {
    }

    public ProteinSequenceAlignmentPairEntity(
            long alignmentId,
            int queryResidueNumber,
            int candidateResidueNumber,
            String queryResidueName,
            String candidateResidueName
    ) {
        this.alignmentId = alignmentId;
        this.queryResidueNumber = queryResidueNumber;
        this.candidateResidueNumber = candidateResidueNumber;
        this.queryResidueName = queryResidueName;
        this.candidateResidueName = candidateResidueName;
    }

    public Long getAlignmentId() {
        return alignmentId;
    }

    public Integer getQueryResidueNumber() {
        return queryResidueNumber;
    }

    public Integer getCandidateResidueNumber() {
        return candidateResidueNumber;
    }

    public String getQueryResidueName() {
        return queryResidueName;
    }

    public String getCandidateResidueName() {
        return candidateResidueName;
    }
}
