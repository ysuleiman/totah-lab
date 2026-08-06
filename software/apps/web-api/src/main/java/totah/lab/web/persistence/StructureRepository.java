package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StructureRepository
        extends JpaRepository<StructureEntity, Long> {

    @Query(value = """
            SELECT
                s.id AS id,
                s.source AS source,
                s.source_accession AS sourceAccession,
                s.chain AS chain,
                s.model_number AS modelNumber,
                s.preparation_state AS preparationState,
                s.parent_structure_id AS parentStructureId,
                r.id AS receptorId,
                r.target_name AS targetName,
                r.uniprot_id AS uniProtId,
                r.protein_name AS proteinName,
                r.gene_name AS geneName,
                r.organism AS organism,
                a.id AS artifactId,
                a.filename AS artifactFilename,
                a.label AS artifactLabel,
                a.storage_location AS artifactStorageLocation,
                chosen.id AS chosenPocketId,
                chosen.pocket_number AS chosenPocketNumber,
                chosen.source::text AS chosenPocketSource
            FROM docking.structure s
            JOIN docking.receptor r
                ON r.id = s.receptor_id
            JOIN docking.artifacts a
                ON a.id = s.artifact_id
            LEFT JOIN docking.pocket chosen
                ON chosen.id = s.chosen_pocket_id
            WHERE s.id = :structureId
            """, nativeQuery = true)
    Optional<StructureDetailsProjection> findStructureDetails(
            @Param("structureId") long structureId
    );

    @Query(value = """
            SELECT
                residue.id AS id,
                residue.chain AS chain,
                residue.residue_number AS residueNumber,
                residue.insertion_code AS insertionCode,
                residue.residue_name AS residueName
            FROM docking.residue residue
            WHERE residue.structure_id = :structureId
            ORDER BY
                residue.chain,
                residue.residue_number,
                residue.insertion_code
            """, nativeQuery = true)
    List<PocketResidueProjection> findResiduesByStructureId(
            @Param("structureId") long structureId
    );

    @Query(value = """
            SELECT DISTINCT s.chosen_pocket_id
            FROM docking.structure s
            WHERE s.chosen_pocket_id IS NOT NULL
            ORDER BY s.chosen_pocket_id
            """, nativeQuery = true)
    List<Long> findAllChosenPocketIds();

    Optional<StructureEntity> findBySourceAndSourceAccession(
            String source,
            String sourceAccession
    );
}
