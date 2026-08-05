package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence for the cached protein-level sequence alignments, keyed
 * by the ordered receptor pair and the aligner's algorithm version.
 */
public interface ProteinSequenceAlignmentRepository
        extends JpaRepository<ProteinSequenceAlignmentEntity, Long> {

    Optional<ProteinSequenceAlignmentEntity>
            findByQueryReceptorIdAndCandidateReceptorIdAndAlgorithmVersion(
                    long queryReceptorId,
                    long candidateReceptorId,
                    int algorithmVersion
            );
}
