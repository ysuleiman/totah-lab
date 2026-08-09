package totah.lab.web.poseanalysis;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import totah.lab.web.persistence.StructureEntity;
import totah.lab.web.service.PocketAlphaSphereProjection;

import java.util.List;
import java.util.Optional;

public interface PoseAnalysisRepository
        extends Repository<StructureEntity, Long> {

    @Query(value = """
            SELECT
                receptor.id AS receptorId,
                MIN(run.structure_id) AS structureId,
                receptor.target_name AS targetName,
                receptor.uniprot_id AS uniProtId,
                COUNT(DISTINCT run.id) AS runCount,
                COUNT(DISTINCT pose.ligand_id) AS ligandCount
            FROM docking.receptor receptor
            JOIN docking.docking_run run ON run.receptor_id = receptor.id
            JOIN docking.docking_pose pose ON pose.run_id = run.id
            GROUP BY receptor.id, receptor.target_name, receptor.uniprot_id
            ORDER BY receptor.target_name
            """, nativeQuery = true)
    List<DockingTargetProjection> findDockingTargets();

    @Query(value = """
            SELECT
                pose.ligand_id AS ligandId,
                ligand.label AS label,
                ligand.smiles AS smiles,
                COUNT(DISTINCT pose.run_id) AS runCount,
                COUNT(*) AS poseCount,
                MIN(pose.vina_score) AS bestScore
            FROM docking.docking_pose pose
            JOIN docking.docking_run run ON run.id = pose.run_id
            LEFT JOIN docking.ligand ligand ON ligand.id = pose.ligand_id
            WHERE run.receptor_id = :receptorId
              AND (CAST(:query AS text) IS NULL
                   OR LOWER(pose.ligand_id)
                      LIKE CONCAT('%', LOWER(CAST(:query AS text)), '%')
                   OR LOWER(COALESCE(ligand.label, ''))
                      LIKE CONCAT('%', LOWER(CAST(:query AS text)), '%')
                   OR LOWER(COALESCE(ligand.smiles, ''))
                      LIKE CONCAT('%', LOWER(CAST(:query AS text)), '%'))
            GROUP BY pose.ligand_id, ligand.label, ligand.smiles
            ORDER BY MIN(pose.vina_score), pose.ligand_id
            LIMIT :limit
            """, nativeQuery = true)
    List<LigandOptionProjection> findLigandsForReceptor(
            @Param("receptorId") long receptorId,
            @Param("query") String query,
            @Param("limit") int limit
    );

    /** Label and SMILES of one docked ligand, when registered. */
    @Query(value = """
            SELECT
                pose.ligand_id AS ligandId,
                ligand.label AS label,
                ligand.smiles AS smiles,
                COUNT(DISTINCT pose.run_id) AS runCount,
                COUNT(*) AS poseCount,
                MIN(pose.vina_score) AS bestScore
            FROM docking.docking_pose pose
            JOIN docking.docking_run run ON run.id = pose.run_id
            LEFT JOIN docking.ligand ligand ON ligand.id = pose.ligand_id
            WHERE run.receptor_id = :receptorId
              AND pose.ligand_id = :ligandId
            GROUP BY pose.ligand_id, ligand.label, ligand.smiles
            """, nativeQuery = true)
    Optional<LigandOptionProjection> findLigandOption(
            @Param("receptorId") long receptorId,
            @Param("ligandId") String ligandId
    );

    /**
     * One option per (ligand, run): the picker reports separate runs
     * instead of collapsing them into a ligand with a run count.
     */
    @Query(value = """
            SELECT
                pose.ligand_id AS ligandId,
                ligand.label AS label,
                ligand.smiles AS smiles,
                run.id AS runId,
                run.source_metadata ->> 'method' AS method,
                COUNT(*) AS poseCount,
                MIN(pose.vina_score) AS bestScore
            FROM docking.docking_pose pose
            JOIN docking.docking_run run ON run.id = pose.run_id
            LEFT JOIN docking.ligand ligand ON ligand.id = pose.ligand_id
            WHERE run.receptor_id = :receptorId
              AND (CAST(:query AS text) IS NULL
                   OR LOWER(pose.ligand_id)
                      LIKE CONCAT('%', LOWER(CAST(:query AS text)), '%')
                   OR LOWER(COALESCE(ligand.label, ''))
                      LIKE CONCAT('%', LOWER(CAST(:query AS text)), '%')
                   OR LOWER(COALESCE(ligand.smiles, ''))
                      LIKE CONCAT('%', LOWER(CAST(:query AS text)), '%'))
            GROUP BY pose.ligand_id, ligand.label, ligand.smiles,
                     run.id, run.source_metadata
            ORDER BY MIN(pose.vina_score), pose.ligand_id, run.id
            LIMIT :limit
            """, nativeQuery = true)
    List<LigandRunOptionProjection> findLigandRunsForReceptor(
            @Param("receptorId") long receptorId,
            @Param("query") String query,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT
                run.id AS id,
                run.receptor_id AS receptorId,
                run.structure_id AS structureId,
                receptor.target_name AS targetName,
                receptor.uniprot_id AS uniProtId,
                run.source_metadata ->> 'method' AS method,
                run.source_metadata ->> 'receptor_artifact_id'
                    AS receptorArtifactId,
                COUNT(pose.id) AS poseCount,
                MIN(pose.vina_score) AS bestScore
            FROM docking.docking_run run
            JOIN docking.receptor receptor
              ON receptor.id = run.receptor_id
            JOIN docking.docking_pose pose
              ON pose.run_id = run.id
             AND pose.ligand_id = :ligandId
            WHERE run.receptor_id = :receptorId
            GROUP BY run.id, run.receptor_id, receptor.target_name,
                     receptor.uniprot_id, run.source_metadata
            ORDER BY run.id
            """, nativeQuery = true)
    List<PoseRunProjection> findRunsForLigand(
            @Param("receptorId") long receptorId,
            @Param("ligandId") String ligandId
    );

    @Query(value = """
            SELECT
                pose.id AS id,
                pose.ligand_label AS ligandLabel,
                pose.vina_score AS vinaScore,
                pose.pose_file AS poseFile
            FROM docking.docking_pose pose
            WHERE pose.run_id = :runId
              AND pose.ligand_id = :ligandId
            ORDER BY pose.vina_score, pose.id
            """, nativeQuery = true)
    List<PoseProjection> findPoses(
            @Param("runId") long runId,
            @Param("ligandId") String ligandId
    );

    @Query(value = """
            SELECT
                pose.id AS id,
                pose.ligand_label AS ligandLabel,
                pose.vina_score AS vinaScore,
                pose.pose_file AS poseFile
            FROM docking.docking_pose pose
            WHERE pose.id = :poseId
            """, nativeQuery = true)
    Optional<PoseProjection> findPose(@Param("poseId") long poseId);

    @Query(value = """
            SELECT
                run.id AS id,
                run.receptor_id AS receptorId,
                run.structure_id AS structureId,
                receptor.target_name AS targetName,
                receptor.uniprot_id AS uniProtId,
                run.source_metadata ->> 'method' AS method,
                run.source_metadata ->> 'receptor_artifact_id'
                    AS receptorArtifactId,
                NULL AS poseCount,
                NULL AS bestScore
            FROM docking.docking_run run
            JOIN docking.receptor receptor
              ON receptor.id = run.receptor_id
            WHERE run.id = :runId
            """, nativeQuery = true)
    Optional<PoseRunProjection> findRun(@Param("runId") long runId);

    /** The run a pose belongs to, for per-pose on-demand analysis. */
    @Query(value = """
            SELECT
                run.id AS id,
                run.receptor_id AS receptorId,
                run.structure_id AS structureId,
                receptor.target_name AS targetName,
                receptor.uniprot_id AS uniProtId,
                run.source_metadata ->> 'method' AS method,
                run.source_metadata ->> 'receptor_artifact_id'
                    AS receptorArtifactId,
                NULL AS poseCount,
                NULL AS bestScore
            FROM docking.docking_run run
            JOIN docking.receptor receptor
              ON receptor.id = run.receptor_id
            JOIN docking.docking_pose pose
              ON pose.run_id = run.id
            WHERE pose.id = :poseId
            """, nativeQuery = true)
    Optional<PoseRunProjection> findRunForPose(@Param("poseId") long poseId);

    /**
     * Receptor PDBQT artifact of any run of the same receptor; the
     * receptor file is receptor-level data, so runs imported without
     * {@code receptor_artifact_id} in their metadata can borrow it from
     * a sibling run.
     */
    @Query(value = """
            SELECT run.source_metadata ->> 'receptor_artifact_id'
            FROM docking.docking_run run
            WHERE run.receptor_id = :receptorId
              AND COALESCE(run.source_metadata ->> 'receptor_artifact_id',
                           '') <> ''
            ORDER BY run.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findSiblingReceptorArtifactId(
            @Param("receptorId") long receptorId
    );

    @Query(value = """
            SELECT artifact.storage_location
            FROM docking.pocket pocket
            JOIN docking.artifacts artifact
              ON artifact.id = pocket.artifact_id
            WHERE pocket.receptor_id = :receptorId
              AND pocket.source = 'BIOHUB'
            ORDER BY pocket.pocket_number
            """, nativeQuery = true)
    List<String> findBiohubArtifactLocations(
            @Param("receptorId") long receptorId
    );

    /**
     * Every pose of a run, across ligands — the input set of the
     * pocket-occupancy report.
     */
    @Query(value = """
            SELECT
                pose.id AS id,
                pose.ligand_label AS ligandLabel,
                pose.vina_score AS vinaScore,
                pose.pose_file AS poseFile
            FROM docking.docking_pose pose
            WHERE pose.run_id = :runId
            ORDER BY pose.vina_score, pose.id
            """, nativeQuery = true)
    List<PoseProjection> findPosesByRunId(@Param("runId") long runId);

    /**
     * Candidate pockets of one structure for pose-to-pocket assignment,
     * in deterministic (source, pocket number) order.
     */
    @Query(value = """
            SELECT
                pocket.id AS id,
                pocket.pocket_number AS pocketNumber,
                pocket.source::text AS source
            FROM docking.pocket pocket
            WHERE pocket.structure_id = :structureId
            ORDER BY pocket.source, pocket.pocket_number
            """, nativeQuery = true)
    List<PosePocketProjection> findPocketsByStructureId(
            @Param("structureId") long structureId
    );

    /**
     * Persisted fpocket alpha spheres of all pockets of one structure,
     * grouped by pocket and ordered by sphere_index within a pocket.
     */
    @Query(value = """
            SELECT
                sphere.pocket_id AS pocketId,
                sphere.sphere_index AS sphereIndex,
                sphere.center_x AS centerX,
                sphere.center_y AS centerY,
                sphere.center_z AS centerZ,
                sphere.radius AS radius
            FROM docking.pocket_alpha_sphere sphere
            JOIN docking.pocket pocket
              ON pocket.id = sphere.pocket_id
            WHERE pocket.structure_id = :structureId
            ORDER BY sphere.pocket_id, sphere.sphere_index
            """, nativeQuery = true)
    List<PocketAlphaSphereProjection> findAlphaSpheresByStructureId(
            @Param("structureId") long structureId
    );

    /**
     * Member residues of all pockets of one structure, grouped by
     * pocket, for the pocket-residue coverage signals of the assignment.
     */
    @Query(value = """
            SELECT
                membership.pocket_id AS pocketId,
                residue.chain AS chain,
                residue.residue_number AS residueNumber,
                residue.insertion_code AS insertionCode,
                residue.residue_name AS residueName
            FROM docking.pocket_residue membership
            JOIN docking.residue residue
              ON residue.id = membership.residue_id
            JOIN docking.pocket pocket
              ON pocket.id = membership.pocket_id
            WHERE pocket.structure_id = :structureId
            ORDER BY
                membership.pocket_id,
                residue.chain,
                residue.residue_number,
                residue.insertion_code
            """, nativeQuery = true)
    List<PosePocketResidueProjection> findPocketResiduesByStructureId(
            @Param("structureId") long structureId
    );

    /**
     * The structure artifact of one structure (the model its pocket
     * rows were generated from), for coordinate-frame provenance
     * validation of pocket-geometry analyses.
     */
    @Query(value = """
            SELECT
                structure.id AS structureId,
                artifact.id AS artifactId,
                artifact.filename AS artifactFilename,
                artifact.storage_location AS artifactStorageLocation,
                structure.source AS structureSource,
                structure.source_accession AS sourceAccession
            FROM docking.structure structure
            JOIN docking.artifacts artifact
              ON artifact.id = structure.artifact_id
            WHERE structure.id = :structureId
            """, nativeQuery = true)
    Optional<StructureArtifactProjection> findStructureArtifact(
            @Param("structureId") long structureId
    );

    /**
     * Structure artifacts of all pocket-bearing structures — the
     * candidates for content-hash matching of an externally produced
     * receptor file against the model its pocket rows were generated
     * from.
     */
    @Query(value = """
            SELECT
                structure.id AS structureId,
                artifact.id AS artifactId,
                artifact.filename AS artifactFilename,
                artifact.storage_location AS artifactStorageLocation,
                structure.source AS structureSource,
                structure.source_accession AS sourceAccession
            FROM docking.structure structure
            JOIN docking.artifacts artifact
              ON artifact.id = structure.artifact_id
            WHERE EXISTS (
                SELECT 1 FROM docking.pocket pocket
                WHERE pocket.structure_id = structure.id
            )
            ORDER BY structure.id
            """, nativeQuery = true)
    List<StructureArtifactProjection> findPocketedStructureArtifacts();
}
