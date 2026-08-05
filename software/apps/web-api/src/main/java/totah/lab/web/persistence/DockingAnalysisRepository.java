package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DockingAnalysisRepository
        extends Repository<StructureEntity, Long> {

    @Query(value = """
            SELECT
                run.id AS id,
                run.structure_id AS structureId,
                run.receptor_id AS receptorId,
                run.created_at AS createdAt,
                count(DISTINCT pose.ligand_id) AS totalLigandCount,
                count(pose.id) AS totalPoseCount
            FROM docking.docking_run run
            JOIN docking.docking_pose pose ON pose.run_id = run.id
            WHERE run.structure_id = :structureId
            GROUP BY
                run.id,
                run.structure_id,
                run.receptor_id,
                run.created_at
            ORDER BY run.created_at DESC, run.id DESC
            """, nativeQuery = true)
    List<DockingRunSummaryProjection> findRunsByStructureId(
            @Param("structureId") long structureId
    );

    @Query(value = """
            SELECT *
            FROM docking.docking_run_residue_summary
            WHERE run_id = :runId
            ORDER BY chain, residue_number, residue_id
            """, nativeQuery = true)
    List<ResidueAnalysisProjection> findResidueSummary(
            @Param("runId") long runId
    );

    @Query(value = """
            SELECT *
            FROM docking.docking_run_residue_score_band_summary
            WHERE run_id = :runId
              AND (:residueId IS NULL OR residue_id = :residueId)
            ORDER BY residue_id, score_lower
            """, nativeQuery = true)
    List<ResidueScoreBandProjection> findResidueScoreBands(
            @Param("runId") long runId,
            @Param("residueId") Long residueId
    );

    @Query(value = """
            WITH best_7b AS (
                SELECT DISTINCT ON (pose.ligand_id)
                    pose.ligand_id,
                    pose.ligand_label,
                    pose.vina_score,
                    pose.run_id,
                    pose.id AS pose_id
                FROM docking.docking_pose pose
                WHERE pose.source_system = 'chemflow3'
                  AND pose.receptor_id = 'Q6UX53'
                ORDER BY pose.ligand_id, pose.vina_score, pose.id
            ),
            best_7a AS (
                SELECT DISTINCT ON (pose.ligand_id)
                    pose.ligand_id,
                    pose.ligand_label,
                    pose.vina_score,
                    pose.run_id,
                    pose.id AS pose_id
                FROM docking.docking_pose pose
                WHERE pose.source_system = 'chemflow3'
                  AND pose.receptor_id = 'Q9H8H3'
                ORDER BY pose.ligand_id, pose.vina_score, pose.id
            ),
            paired_score AS (
                SELECT
                    score_7b.ligand_id,
                    coalesce(score_7b.ligand_label,
                             score_7a.ligand_label,
                             score_7b.ligand_id) AS ligand_label,
                    score_7b.vina_score AS score_7b,
                    score_7a.vina_score AS score_7a,
                    score_7a.vina_score - score_7b.vina_score AS delta,
                    score_7b.run_id AS run_id_7b,
                    score_7a.run_id AS run_id_7a,
                    score_7b.pose_id AS pose_id_7b,
                    score_7a.pose_id AS pose_id_7a
                FROM best_7b score_7b
                JOIN best_7a score_7a
                  ON score_7a.ligand_id = score_7b.ligand_id
                WHERE coalesce(
                          score_7b.ligand_label,
                          score_7a.ligand_label,
                          score_7b.ligand_id
                      ) NOT ILIKE 'WH%'
            )
            SELECT
                paired_score.ligand_id AS ligandId,
                paired_score.ligand_label AS ligandLabel,
                ligand.smiles AS smiles,
                paired_score.score_7b AS score7b,
                paired_score.score_7a AS score7a,
                paired_score.delta,
                paired_score.run_id_7b AS runId7b,
                paired_score.run_id_7a AS runId7a,
                paired_score.pose_id_7b AS poseId7b,
                paired_score.pose_id_7a AS poseId7a,
                count(*) OVER () AS totalCount
            FROM paired_score
            LEFT JOIN docking.ligand ligand
              ON ligand.id = paired_score.ligand_id
            WHERE (:search = ''
                   OR paired_score.ligand_id ILIKE '%' || :search || '%'
                   OR paired_score.ligand_label ILIKE '%' || :search || '%')
            ORDER BY
                CASE WHEN :sortBy = 'delta' AND :direction = 'asc'
                     THEN delta END ASC,
                CASE WHEN :sortBy = 'delta' AND :direction = 'desc'
                     THEN delta END DESC,
                CASE WHEN :sortBy = 'score7b' AND :direction = 'asc'
                     THEN score_7b END ASC,
                CASE WHEN :sortBy = 'score7b' AND :direction = 'desc'
                     THEN score_7b END DESC,
                CASE WHEN :sortBy = 'score7a' AND :direction = 'asc'
                     THEN score_7a END ASC,
                CASE WHEN :sortBy = 'score7a' AND :direction = 'desc'
                     THEN score_7a END DESC,
                CASE WHEN :sortBy = 'ligandId' AND :direction = 'asc'
                     THEN ligand_id END ASC,
                CASE WHEN :sortBy = 'ligandId' AND :direction = 'desc'
                     THEN ligand_id END DESC,
                ligand_id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<SelectivityScoreProjection> findSelectivityScores(
            @Param("sortBy") String sortBy,
            @Param("direction") String direction,
            @Param("search") String search,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
