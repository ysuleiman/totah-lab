package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Maps docking.protein_sequence_alignment: the cached protein-level
 * Needleman-Wunsch alignment of one ORDERED receptor pair (the
 * direction is meaningful; the reverse direction is a separate row),
 * stamped with the aligner's algorithm version.
 *
 * Table created by tools/scripts/sql/docking/protein-sequence-alignment.sql.
 */
@Entity
@Table(name = "protein_sequence_alignment")
public class ProteinSequenceAlignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_receptor_id", nullable = false)
    private Long queryReceptorId;

    @Column(name = "candidate_receptor_id", nullable = false)
    private Long candidateReceptorId;

    @Column(name = "identity", nullable = false)
    private double identity;

    @Column(name = "algorithm_version", nullable = false)
    private int algorithmVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProteinSequenceAlignmentEntity() {
    }

    public ProteinSequenceAlignmentEntity(
            long queryReceptorId,
            long candidateReceptorId,
            double identity,
            int algorithmVersion
    ) {
        this.queryReceptorId = queryReceptorId;
        this.candidateReceptorId = candidateReceptorId;
        this.identity = identity;
        this.algorithmVersion = algorithmVersion;
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getQueryReceptorId() {
        return queryReceptorId;
    }

    public Long getCandidateReceptorId() {
        return candidateReceptorId;
    }

    public double getIdentity() {
        return identity;
    }

    public int getAlgorithmVersion() {
        return algorithmVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
