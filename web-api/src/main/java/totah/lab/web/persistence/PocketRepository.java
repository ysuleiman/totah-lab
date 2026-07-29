package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PocketRepository
        extends JpaRepository<PocketEntity, Long> {

    @Query(value = """
            SELECT
                p.id AS id,
                p.pocket_number AS pocketNumber,
                p.source::text AS source,
                p.volume AS volume,
                p.druggability_score AS druggabilityScore,
                s.id AS structureId,
                s.source AS structureSource,
                s.source_accession AS structureAccession,
                s.chain AS chain,
                s.model_number AS modelNumber,
                r.id AS receptorId,
                r.target_name AS targetName,
                a.id AS artifactId,
                a.filename AS artifactFilename,
                a.label AS artifactLabel,
                a.storage_location AS artifactStorageLocation
            FROM docking.pocket p
            JOIN docking.structure s
                ON s.id = p.structure_id
            JOIN docking.receptor r
                ON r.id = s.receptor_id
            JOIN docking.artifacts a
                ON a.id = p.artifact_id
            WHERE p.id = :pocketId
            """, nativeQuery = true)
    Optional<PocketDetailsProjection> findPocketDetails(
            @Param("pocketId") long pocketId
    );

    @Query(value = """
            SELECT
                p.id AS id,
                p.pocket_number AS pocketNumber,
                p.source::text AS source,
                p.volume AS volume,
                p.druggability_score AS druggabilityScore,
                s.id AS structureId,
                s.source AS structureSource,
                s.source_accession AS structureAccession,
                s.chain AS chain,
                s.model_number AS modelNumber,
                r.id AS receptorId,
                r.target_name AS targetName,
                a.id AS artifactId,
                a.filename AS artifactFilename,
                a.label AS artifactLabel,
                a.storage_location AS artifactStorageLocation
            FROM docking.pocket p
            JOIN docking.structure s
                ON s.id = p.structure_id
            JOIN docking.receptor r
                ON r.id = s.receptor_id
            JOIN docking.artifacts a
                ON a.id = p.artifact_id
            WHERE p.structure_id = :structureId
            ORDER BY p.source, p.pocket_number
            """, nativeQuery = true)
    List<PocketDetailsProjection> findPocketDetailsByStructureId(
            @Param("structureId") long structureId
    );

    @Query(value = """
            SELECT
                residue.id AS id,
                residue.chain AS chain,
                residue.residue_number AS residueNumber,
                residue.insertion_code AS insertionCode,
                residue.residue_name AS residueName
            FROM docking.pocket_residue membership
            JOIN docking.residue residue
                ON residue.id = membership.residue_id
            WHERE membership.pocket_id = :pocketId
            ORDER BY
                residue.chain,
                residue.residue_number,
                residue.insertion_code
            """, nativeQuery = true)
    List<PocketResidueProjection> findResiduesByPocketId(
            @Param("pocketId") long pocketId
    );
}
