package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import totah.lab.gaia.pocket.PocketSource;

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
                p.score AS score,
                p.druggability_score AS druggabilityScore,
                p.probability AS probability,
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
                , structure_artifact.id AS structureArtifactId
                , structure_artifact.storage_location AS structureArtifactStorageLocation
            FROM docking.pocket p
            JOIN docking.structure s
                ON s.id = p.structure_id
            JOIN docking.receptor r
                ON r.id = s.receptor_id
            JOIN docking.artifacts a
                ON a.id = p.artifact_id
            JOIN docking.artifacts structure_artifact
                ON structure_artifact.id = s.artifact_id
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
                p.score AS score,
                p.druggability_score AS druggabilityScore,
                p.probability AS probability,
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
                , structure_artifact.id AS structureArtifactId
                , structure_artifact.storage_location AS structureArtifactStorageLocation
            FROM docking.pocket p
            JOIN docking.structure s
                ON s.id = p.structure_id
            JOIN docking.receptor r
                ON r.id = s.receptor_id
            JOIN docking.artifacts a
                ON a.id = p.artifact_id
            JOIN docking.artifacts structure_artifact
                ON structure_artifact.id = s.artifact_id
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

    @Query(value = """
            SELECT membership.residue_id
            FROM docking.structure structure
            JOIN docking.pocket_residue membership
                ON membership.pocket_id = structure.chosen_pocket_id
            WHERE structure.id = :structureId
            """, nativeQuery = true)
    List<Long> findChosenPocketResidueIds(
            @Param("structureId") long structureId
    );

    Optional<PocketEntity>
    findByStructureIdAndSourceAndPocketNumber(
            long structureId,
            PocketSource source,
            int pocketNumber
    );

    /**
     * Structures having at least one pocket of the given source with zero
     * rows in pocket_alpha_sphere (anti-join used by the alpha-sphere
     * backfill). A null structureAccession selects all structures.
     */
    @Query("""
            SELECT DISTINCT pocket.structure.id
            FROM PocketEntity pocket
            WHERE pocket.source = :source
              AND (:structureAccession IS NULL
                   OR pocket.structure.sourceAccession = :structureAccession)
              AND NOT EXISTS (
                  SELECT 1
                  FROM PocketAlphaSphereEntity sphere
                  WHERE sphere.pocket = pocket
              )
            ORDER BY pocket.structure.id
            """)
    List<Long> findStructureIdsWithPocketsMissingSpheres(
            @Param("source") PocketSource source,
            @Param("structureAccession") String structureAccession
    );

    /**
     * Pockets of one structure with zero rows in pocket_alpha_sphere,
     * with their artifacts fetched for vert-file resolution.
     */
    @Query("""
            SELECT pocket
            FROM PocketEntity pocket
            JOIN FETCH pocket.artifact
            WHERE pocket.source = :source
              AND pocket.structure.id = :structureId
              AND NOT EXISTS (
                  SELECT 1
                  FROM PocketAlphaSphereEntity sphere
                  WHERE sphere.pocket = pocket
              )
            ORDER BY pocket.pocketNumber
            """)
    List<PocketEntity> findPocketsMissingSpheres(
            @Param("source") PocketSource source,
            @Param("structureId") long structureId
    );

    /**
     * Structures having at least one pocket of the given source with
     * persisted alpha spheres but no row in pocket_shape_descriptor
     * (anti-join used by the shape-descriptor backfill).
     */
    @Query("""
            SELECT DISTINCT pocket.structure.id
            FROM PocketEntity pocket
            WHERE pocket.source = :source
              AND EXISTS (
                  SELECT 1
                  FROM PocketAlphaSphereEntity sphere
                  WHERE sphere.pocket = pocket
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM PocketShapeDescriptorEntity descriptor
                  WHERE descriptor.pocketId = pocket.id
              )
            ORDER BY pocket.structure.id
            """)
    List<Long> findStructureIdsWithPocketsMissingDescriptors(
            @Param("source") PocketSource source
    );

    /**
     * Pockets of one structure that have persisted alpha spheres but no
     * row in pocket_shape_descriptor.
     */
    @Query("""
            SELECT pocket.id
            FROM PocketEntity pocket
            WHERE pocket.source = :source
              AND pocket.structure.id = :structureId
              AND EXISTS (
                  SELECT 1
                  FROM PocketAlphaSphereEntity sphere
                  WHERE sphere.pocket = pocket
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM PocketShapeDescriptorEntity descriptor
                  WHERE descriptor.pocketId = pocket.id
              )
            ORDER BY pocket.id
            """)
    List<Long> findPocketIdsMissingDescriptors(
            @Param("source") PocketSource source,
            @Param("structureId") long structureId
    );

    /**
     * Artifact storage locations of one structure's pockets of the
     * given source (for example BIOHUB ligand-contact evidence
     * artifacts), for structure-keyed evidence lookups.
     */
    @Query("""
            SELECT pocket.artifact.storageLocation
            FROM PocketEntity pocket
            WHERE pocket.structure.id = :structureId
              AND pocket.source = :source
            """)
    List<String> findArtifactStorageLocations(
            @Param("structureId") long structureId,
            @Param("source") PocketSource source
    );
}
