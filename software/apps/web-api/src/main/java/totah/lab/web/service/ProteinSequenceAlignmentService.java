package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.SequenceResidue;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.web.persistence.ProteinSequenceAlignmentEntity;
import totah.lab.web.persistence.ProteinSequenceAlignmentPairEntity;
import totah.lab.web.persistence.ProteinSequenceAlignmentPairRepository;
import totah.lab.web.persistence.ProteinSequenceAlignmentRepository;
import totah.lab.web.persistence.ReceptorEntity;
import totah.lab.web.persistence.ReceptorRepository;
import totah.lab.web.persistence.StructureEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Protein-level sequence-alignment cache for sequence-seeded pocket
 * alignment. The alignment of an ordered receptor pair is computed
 * once from the receptors' structure artifacts (via Athena's
 * Needleman-Wunsch aligner), persisted atomically, and served from the
 * cache afterwards. The direction is meaningful: query -> candidate
 * pairs; the reverse direction is a separate row.
 */
@Service
public class ProteinSequenceAlignmentService {

    /**
     * Needleman-Wunsch v1 (match +2, mismatch -1, linear gap -2), as
     * validated against the METTL7 correspondence reference.
     */
    public static final int ALGORITHM_VERSION = 1;

    private final ProteinSequenceAlignmentRepository alignmentRepository;
    private final ProteinSequenceAlignmentPairRepository pairRepository;
    private final ReceptorRepository receptorRepository;
    private final StructureArtifactService structureArtifactService;
    private final PlatformTransactionManager transactionManager;
    private final NeedlemanWunschSequenceAligner sequenceAligner =
            new NeedlemanWunschSequenceAligner();

    public ProteinSequenceAlignmentService(
            ProteinSequenceAlignmentRepository alignmentRepository,
            ProteinSequenceAlignmentPairRepository pairRepository,
            ReceptorRepository receptorRepository,
            StructureArtifactService structureArtifactService,
            PlatformTransactionManager transactionManager
    ) {
        this.alignmentRepository = alignmentRepository;
        this.pairRepository = pairRepository;
        this.receptorRepository = receptorRepository;
        this.structureArtifactService = structureArtifactService;
        this.transactionManager = transactionManager;
    }

    /**
     * Returns the cached alignment of the ordered receptor pair,
     * computing and persisting it on first use.
     */
    @Transactional
    public SequenceAlignment alignmentFor(
            long queryReceptorId,
            long candidateReceptorId
    ) {
        return alignmentRepository
                .findByQueryReceptorIdAndCandidateReceptorIdAndAlgorithmVersion(
                        queryReceptorId,
                        candidateReceptorId,
                        ALGORITHM_VERSION
                )
                .map(this::toSequenceAlignment)
                .orElseGet(() -> computeAndPersist(
                        queryReceptorId,
                        candidateReceptorId
                ));
    }

    private SequenceAlignment toSequenceAlignment(
            ProteinSequenceAlignmentEntity entity
    ) {
        List<AlignedResiduePair> pairs = pairRepository
                .findByAlignmentIdOrderByQueryResidueNumberAscCandidateResidueNumberAsc(
                        entity.getId()
                )
                .stream()
                .map(pair -> new AlignedResiduePair(
                        pair.getQueryResidueNumber(),
                        pair.getCandidateResidueNumber(),
                        pair.getQueryResidueName(),
                        pair.getCandidateResidueName()
                ))
                .toList();

        return new SequenceAlignment(entity.getIdentity(), pairs);
    }

    private SequenceAlignment computeAndPersist(
            long queryReceptorId,
            long candidateReceptorId
    ) {
        List<SequenceResidue> querySequence =
                sequenceFor(queryReceptorId);
        List<SequenceResidue> candidateSequence =
                sequenceFor(candidateReceptorId);

        SequenceAlignment alignment = sequenceAligner.align(
                querySequence,
                candidateSequence
        );

        // Persist in an independent write transaction: callers such as
        // the similarity pipeline run read-only transactions, which
        // must never see or veto this write.
        persist(queryReceptorId, candidateReceptorId, alignment);

        return alignment;
    }

    private void persist(
            long queryReceptorId,
            long candidateReceptorId,
            SequenceAlignment alignment
    ) {
        TransactionTemplate writeTransaction =
                new TransactionTemplate(transactionManager);
        writeTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        writeTransaction.executeWithoutResult(status -> {
            ProteinSequenceAlignmentEntity entity =
                    alignmentRepository.save(new ProteinSequenceAlignmentEntity(
                            queryReceptorId,
                            candidateReceptorId,
                            alignment.identity(),
                            ALGORITHM_VERSION
                    ));

            List<ProteinSequenceAlignmentPairEntity> pairEntities =
                    new ArrayList<>(alignment.pairs().size());

            for (AlignedResiduePair pair : alignment.pairs()) {
                pairEntities.add(new ProteinSequenceAlignmentPairEntity(
                        entity.getId(),
                        pair.queryResidueNumber(),
                        pair.candidateResidueNumber(),
                        pair.queryResidueName(),
                        pair.candidateResidueName()
                ));
            }

            pairRepository.saveAll(pairEntities);
        });
    }

    /**
     * The receptor's sequence: the ordered residues of every chain of
     * its canonical (lowest-id) structure's artifact Gaia Structure.
     * The artifact service caches loaded structures per artifact id.
     */
    private List<SequenceResidue> sequenceFor(long receptorId) {
        ReceptorEntity receptor = receptorRepository
                .findById(receptorId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Receptor not found: " + receptorId
                ));

        StructureEntity structure = receptor.getStructures()
                .stream()
                .min(Comparator.comparing(StructureEntity::getId))
                .orElseThrow(() -> new ResponseStatusException(
                        UNPROCESSABLE_ENTITY,
                        "Receptor " + receptorId + " has no structure"
                ));

        final Structure gaiaStructure;

        try {
            gaiaStructure = structureArtifactService.load(
                    structure.getArtifact().getId(),
                    structure.getArtifact().getStorageLocation()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Receptor " + receptorId
                            + " structure artifact cannot be loaded: "
                            + exception.getMessage(),
                    exception
            );
        }

        List<SequenceResidue> sequence = new ArrayList<>();

        for (Chain chain : gaiaStructure.getChains()) {
            for (Residue residue : chain.residues()) {
                sequence.add(new SequenceResidue(
                        residue.getNumber(),
                        residue.getName()
                ));
            }
        }

        return sequence;
    }
}
