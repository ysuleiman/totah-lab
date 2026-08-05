package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for the aligned residue pairs of the cached protein
 * sequence alignments.
 */
public interface ProteinSequenceAlignmentPairRepository
        extends JpaRepository<
                ProteinSequenceAlignmentPairEntity,
                ProteinSequenceAlignmentPairId> {

    List<ProteinSequenceAlignmentPairEntity>
            findByAlignmentIdOrderByQueryResidueNumberAscCandidateResidueNumberAsc(
                    long alignmentId
            );
}
