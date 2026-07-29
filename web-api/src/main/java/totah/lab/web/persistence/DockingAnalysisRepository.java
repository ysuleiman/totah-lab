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
}
