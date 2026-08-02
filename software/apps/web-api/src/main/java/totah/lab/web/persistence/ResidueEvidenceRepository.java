package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResidueEvidenceRepository
        extends Repository<StructureEntity, Long> {

    @Query(value = """
            SELECT DISTINCT ON (analysis.residue_id)
                analysis.residue_id AS residueId,
                analysis.analysis_type AS analysisType,
                analysis.score AS score,
                analysis.rank AS rank,
                analysis.metrics->>'provider' AS provider,
                analysis.metrics->>'model' AS model,
                analysis.metrics->>'bestAlternative' AS bestAlternative,
                (analysis.metrics->>'wildTypeMinusBestAlternative')
                    ::double precision AS wildTypeMinusBestAlternative,
                (analysis.metrics->>'aminoAcidEntropy')
                    ::double precision AS aminoAcidEntropy,
                analysis.artifact_id AS artifactId
            FROM docking.residue_analysis analysis
            JOIN docking.artifacts artifact
                ON artifact.id = analysis.artifact_id
            WHERE analysis.structure_id = :structureId
              AND analysis.analysis_type = :analysisType
            ORDER BY
                analysis.residue_id,
                artifact.created_at DESC,
                artifact.id DESC
            """, nativeQuery = true)
    List<ResidueEvidenceProjection> findLatestByStructureAndType(
            @Param("structureId") long structureId,
            @Param("analysisType") String analysisType
    );
}
