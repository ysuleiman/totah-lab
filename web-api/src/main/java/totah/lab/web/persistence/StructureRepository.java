package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
                a.id AS artifactId,
                a.filename AS artifactFilename,
                a.label AS artifactLabel,
                a.storage_location AS artifactStorageLocation
            FROM docking.structure s
            JOIN docking.receptor r
                ON r.id = s.receptor_id
            JOIN docking.artifacts a
                ON a.id = s.artifact_id
            WHERE s.id = :structureId
            """, nativeQuery = true)
    Optional<StructureDetailsProjection> findStructureDetails(
            @Param("structureId") long structureId
    );
}
