package totah.lab.web.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import totah.lab.web.persistence.ProteinSequenceAlignmentEntity;
import totah.lab.web.persistence.ProteinSequenceAlignmentPairEntity;
import totah.lab.web.persistence.ProteinSequenceAlignmentPairRepository;
import totah.lab.web.persistence.ProteinSequenceAlignmentRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip of the protein sequence-alignment cache tables against
 * the throwaway docking_test schema: validates that the entity
 * mappings (including the pair table's composite business key) match
 * the DDL in docking_test_schema.sql.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=docking_test",
        "totah.artifacts.root=target/test-artifacts"
})
class ProteinSequenceAlignmentPersistenceIntegrationTest
        extends DockingTestSchemaSupport {

    static {
        recreateTestSchema();
    }

    @Autowired
    ProteinSequenceAlignmentRepository alignmentRepository;

    @Autowired
    ProteinSequenceAlignmentPairRepository pairRepository;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void truncateTestSchema() {
        jdbc.execute("""
                TRUNCATE docking_test.protein_sequence_alignment_pair,
                         docking_test.protein_sequence_alignment
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void savesAndReadsBackAnAlignmentWithItsPairs() {
        ProteinSequenceAlignmentEntity saved = alignmentRepository.save(
                new ProteinSequenceAlignmentEntity(
                        100L, 200L, 0.586,
                        ProteinSequenceAlignmentService.ALGORITHM_VERSION
                )
        );

        pairRepository.saveAll(List.of(
                new ProteinSequenceAlignmentPairEntity(
                        saved.getId(), 10, 20, "ALA", "ALA"
                ),
                new ProteinSequenceAlignmentPairEntity(
                        saved.getId(), 12, 24, "LEU", "VAL"
                ),
                new ProteinSequenceAlignmentPairEntity(
                        saved.getId(), 11, 22, "SER", "SER"
                )
        ));

        Optional<ProteinSequenceAlignmentEntity> loaded =
                alignmentRepository
                        .findByQueryReceptorIdAndCandidateReceptorIdAndAlgorithmVersion(
                                100L, 200L,
                                ProteinSequenceAlignmentService
                                        .ALGORITHM_VERSION
                        );

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getIdentity()).isEqualTo(0.586);
        assertThat(loaded.get().getCreatedAt()).isNotNull();

        List<ProteinSequenceAlignmentPairEntity> pairs =
                pairRepository
                        .findByAlignmentIdOrderByQueryResidueNumberAscCandidateResidueNumberAsc(
                                saved.getId()
                        );

        assertThat(pairs)
                .extracting(
                        ProteinSequenceAlignmentPairEntity
                                ::getQueryResidueNumber
                )
                .containsExactly(10, 11, 12);
        assertThat(pairs)
                .extracting(
                        ProteinSequenceAlignmentPairEntity
                                ::getCandidateResidueName
                )
                .containsExactly("ALA", "SER", "VAL");
    }

    @Test
    void cascadeDeleteRemovesThePairs() {
        ProteinSequenceAlignmentEntity saved = alignmentRepository.save(
                new ProteinSequenceAlignmentEntity(
                        100L, 200L, 0.5,
                        ProteinSequenceAlignmentService.ALGORITHM_VERSION
                )
        );

        pairRepository.saveAll(List.of(
                new ProteinSequenceAlignmentPairEntity(
                        saved.getId(), 10, 20, "ALA", "ALA"
                )
        ));

        alignmentRepository.deleteById(saved.getId());

        assertThat(pairRepository
                .findByAlignmentIdOrderByQueryResidueNumberAscCandidateResidueNumberAsc(
                        saved.getId()
                ))
                .isEmpty();
    }
}
