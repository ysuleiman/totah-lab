package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.web.persistence.ArtifactEntity;
import totah.lab.web.persistence.PipelineRunEntity;
import totah.lab.web.persistence.ProteinSequenceAlignmentEntity;
import totah.lab.web.persistence.ProteinSequenceAlignmentPairEntity;
import totah.lab.web.persistence.ProteinSequenceAlignmentPairRepository;
import totah.lab.web.persistence.ProteinSequenceAlignmentRepository;
import totah.lab.web.persistence.ReceptorEntity;
import totah.lab.web.persistence.ReceptorRepository;
import totah.lab.web.persistence.StructureEntity;
import totah.lab.web.persistence.TargetEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProteinSequenceAlignmentServiceTest {

    private final ProteinSequenceAlignmentRepository alignmentRepository =
            mock(ProteinSequenceAlignmentRepository.class);
    private final ProteinSequenceAlignmentPairRepository pairRepository =
            mock(ProteinSequenceAlignmentPairRepository.class);
    private final ReceptorRepository receptorRepository =
            mock(ReceptorRepository.class);
    private final FakeStructureArtifactService structureArtifactService =
            new FakeStructureArtifactService();
    private final PlatformTransactionManager transactionManager =
            new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(
                        TransactionDefinition definition
                ) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {
                }

                @Override
                public void rollback(TransactionStatus status) {
                }
            };

    private final ProteinSequenceAlignmentService service =
            new ProteinSequenceAlignmentService(
                    alignmentRepository,
                    pairRepository,
                    receptorRepository,
                    structureArtifactService,
                    transactionManager
            );

    @Test
    void cacheHitReturnsTheStoredAlignment() {
        ProteinSequenceAlignmentEntity stored =
                new ProteinSequenceAlignmentEntity(
                        100L, 200L, 0.5,
                        ProteinSequenceAlignmentService.ALGORITHM_VERSION
                ) {
                    @Override
                    public Long getId() {
                        return 1L;
                    }
                };
        when(alignmentRepository
                .findByQueryReceptorIdAndCandidateReceptorIdAndAlgorithmVersion(
                        100L, 200L,
                        ProteinSequenceAlignmentService.ALGORITHM_VERSION
                ))
                .thenReturn(Optional.of(stored));
        when(pairRepository
                .findByAlignmentIdOrderByQueryResidueNumberAscCandidateResidueNumberAsc(
                        stored.getId()
                ))
                .thenReturn(List.of(
                        new ProteinSequenceAlignmentPairEntity(
                                1L, 10, 20, "ALA", "ALA"
                        ),
                        new ProteinSequenceAlignmentPairEntity(
                                1L, 11, 21, "LEU", "VAL"
                        )
                ));

        SequenceAlignment alignment = service.alignmentFor(100L, 200L);

        assertEquals(0.5, alignment.identity(), 1.0e-12);
        assertEquals(2, alignment.pairs().size());
        assertEquals(10, alignment.pairs().get(0).queryResidueNumber());
        assertEquals(20, alignment.pairs().get(0).candidateResidueNumber());
        assertEquals("LEU", alignment.pairs().get(1).queryResidueName());
        assertEquals("VAL", alignment.pairs().get(1).candidateResidueName());

        verify(receptorRepository, never()).findById(anyLong());
    }

    @Test
    void cacheMissComputesPersistsAndReturnsTheAlignment()
            throws IOException {
        stubMiss();
        stubReceptor(100L, structure(
                residues(1, "ALA", "ARG", "ASN", "ASP")
        ));
        stubReceptor(200L, structure(
                residues(7, "ALA", "ARG", "GLY", "ASN", "ASP")
        ));

        stubSaveAssignsId();

        SequenceAlignment alignment = service.alignmentFor(100L, 200L);

        // ALA ARG - ASN ASP against ALA ARG GLY ASN ASP: one
        // candidate insertion, four identical pairs.
        assertEquals(1.0, alignment.identity(), 1.0e-12);
        assertEquals(4, alignment.pairs().size());
        assertEquals(1, alignment.pairs().get(0).queryResidueNumber());
        assertEquals(7, alignment.pairs().get(0).candidateResidueNumber());
        assertEquals(4, alignment.pairs().get(3).queryResidueNumber());
        assertEquals(11, alignment.pairs().get(3).candidateResidueNumber());

        ArgumentCaptor<ProteinSequenceAlignmentEntity> alignmentCaptor =
                ArgumentCaptor.forClass(
                        ProteinSequenceAlignmentEntity.class
                );
        verify(alignmentRepository).save(alignmentCaptor.capture());

        ProteinSequenceAlignmentEntity persisted =
                alignmentCaptor.getValue();

        assertEquals(100L, persisted.getQueryReceptorId());
        assertEquals(200L, persisted.getCandidateReceptorId());
        assertEquals(1.0, persisted.getIdentity(), 1.0e-12);
        assertEquals(
                ProteinSequenceAlignmentService.ALGORITHM_VERSION,
                persisted.getAlgorithmVersion()
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProteinSequenceAlignmentPairEntity>>
                pairsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pairRepository).saveAll(pairsCaptor.capture());

        List<ProteinSequenceAlignmentPairEntity> persistedPairs =
                pairsCaptor.getValue();

        assertEquals(4, persistedPairs.size());
        assertEquals(1, persistedPairs.get(0).getQueryResidueNumber());
        assertEquals(7, persistedPairs.get(0).getCandidateResidueNumber());
        assertEquals("ALA", persistedPairs.get(0).getQueryResidueName());
        assertEquals("ASP", persistedPairs.get(3).getCandidateResidueName());
    }

    @Test
    void reverseDirectionIsASeparateComputation() throws IOException {
        stubMiss();
        stubReceptor(100L, structure(
                residues(1, "ALA", "ARG", "ASN", "ASP")
        ));
        stubReceptor(200L, structure(
                residues(7, "ALA", "ARG", "GLY", "ASN", "ASP")
        ));

        stubSaveAssignsId();

        SequenceAlignment alignment = service.alignmentFor(200L, 100L);

        // Reversed direction: the insertion now gaps the query.
        assertEquals(4, alignment.pairs().size());
        assertEquals(7, alignment.pairs().get(0).queryResidueNumber());
        assertEquals(1, alignment.pairs().get(0).candidateResidueNumber());
    }

    @Test
    void missingReceptorFailsWithNotFound() {
        stubMiss();
        when(receptorRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.alignmentFor(99L, 200L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void receptorWithoutStructureFailsUnprocessable() {
        stubMiss();
        when(receptorRepository.findById(100L))
                .thenReturn(Optional.of(new ReceptorEntity()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.alignmentFor(100L, 200L)
        );

        assertEquals(422, exception.getStatusCode().value());
    }

    /**
     * Stubs the repository's IDENTITY id assignment: with the real
     * persistence context, {@code save} inserts immediately and the
     * returned entity carries its generated id.
     */
    private void stubSaveAssignsId() {
        when(alignmentRepository.save(
                org.mockito.ArgumentMatchers
                        .any(ProteinSequenceAlignmentEntity.class)
        )).thenAnswer(invocation -> {
            ProteinSequenceAlignmentEntity entity =
                    invocation.getArgument(0);

            return new ProteinSequenceAlignmentEntity(
                    entity.getQueryReceptorId(),
                    entity.getCandidateReceptorId(),
                    entity.getIdentity(),
                    entity.getAlgorithmVersion()
            ) {
                @Override
                public Long getId() {
                    return 1L;
                }
            };
        });
    }

    private void stubMiss() {
        when(alignmentRepository
                .findByQueryReceptorIdAndCandidateReceptorIdAndAlgorithmVersion(
                        anyLong(), anyLong(), anyInt()
                ))
                .thenReturn(Optional.empty());
    }

    private void stubReceptor(
            long receptorId,
            Structure structure
    ) throws IOException {
        ReceptorEntity receptor = new ReceptorEntity();

        ArtifactEntity artifact = new ArtifactEntity(
                "structure-" + receptorId + ".pdb",
                "structure",
                "receptor-" + receptorId + "/structure.pdb",
                new PipelineRunEntity(null, null, "COMPLETE"),
                new TargetEntity("target", "UP-" + receptorId)
        ) {
            @Override
            public Long getId() {
                return receptorId * 1000L;
            }
        };

        StructureEntity structureEntity = new StructureEntity();
        structureEntity.setSource("ALPHAFOLD");
        structureEntity.setArtifact(artifact);
        receptor.addStructure(structureEntity);

        when(receptorRepository.findById(receptorId))
                .thenReturn(Optional.of(receptor));
        structureArtifactService.register(
                "receptor-" + receptorId + "/structure.pdb",
                structure
        );
    }

    private static Structure structure(List<Residue> residues) {
        return new Structure(List.of(new Chain("A", residues)));
    }

    private static List<Residue> residues(
            int firstNumber,
            String... names
    ) {
        List<Residue> residues = new java.util.ArrayList<>();

        for (int index = 0; index < names.length; index++) {
            residues.add(new Residue(
                    names[index],
                    firstNumber + index,
                    null,
                    List.of()
            ));
        }

        return residues;
    }

    /**
     * Serves a fixed Gaia Structure per artifact storage location;
     * the artifact id is irrelevant to sequence extraction.
     */
    private static final class FakeStructureArtifactService
            extends StructureArtifactService {

        private final Map<String, Structure> structures =
                new java.util.HashMap<>();

        private FakeStructureArtifactService() {
            super("/unused");
        }

        void register(String storageLocation, Structure structure) {
            structures.put(storageLocation, structure);
        }

        @Override
        public Structure load(long artifactId, String storageLocation) {
            return structures.get(storageLocation);
        }
    }
}
