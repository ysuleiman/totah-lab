package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.web.persistence.DockingAnalysisRepository;
import totah.lab.web.persistence.DockingRunSummaryProjection;
import totah.lab.web.persistence.ResidueAnalysisProjection;
import totah.lab.web.persistence.ResidueScoreBandProjection;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DockingAnalysisService {

    private final DockingAnalysisRepository repository;

    public DockingAnalysisService(DockingAnalysisRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DockingRunSummary> getRunsForStructure(long structureId) {
        return repository.findRunsByStructureId(structureId).stream()
                .map(this::toRunSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ResidueAnalysis> getResidueSummary(long runId) {
        List<ResidueAnalysis> summary = repository.findResidueSummary(runId)
                .stream()
                .map(this::toResidueAnalysis)
                .toList();
        if (summary.isEmpty()) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Docking run analysis not found: " + runId
            );
        }
        return summary;
    }

    @Transactional(readOnly = true)
    public List<ResidueScoreBand> getResidueScoreBands(
            long runId,
            Long residueId
    ) {
        List<ResidueScoreBand> bands = repository
                .findResidueScoreBands(runId, residueId)
                .stream()
                .map(this::toScoreBand)
                .toList();
        if (bands.isEmpty()) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Docking run score-band analysis not found: " + runId
            );
        }
        return bands;
    }

    private DockingRunSummary toRunSummary(
            DockingRunSummaryProjection row
    ) {
        return new DockingRunSummary(
                row.getId(),
                row.getStructureId(),
                row.getReceptorId(),
                row.getCreatedAt(),
                row.getTotalLigandCount(),
                row.getTotalPoseCount()
        );
    }

    private ResidueAnalysis toResidueAnalysis(
            ResidueAnalysisProjection row
    ) {
        return new ResidueAnalysis(
                row.getRunId(),
                row.getStructureId(),
                row.getReceptorId(),
                row.getResidueId(),
                row.getChain(),
                row.getResidueNumber(),
                row.getResidueName(),
                row.getTotalLigandCount(),
                row.getContactingLigandCount(),
                row.getContactingLigandFraction(),
                row.getTotalPoseCount(),
                row.getContactingPoseCount(),
                row.getContactingPoseFraction(),
                row.getTotalGoodLigandCount(),
                row.getGoodContactingLigandCount(),
                row.getGoodContactingLigandFraction(),
                row.getTotalBadLigandCount(),
                row.getBadContactingLigandCount(),
                row.getBadContactingLigandFraction(),
                row.getContactFractionDifference(),
                row.getEnrichmentRatio(),
                row.getLog2Enrichment(),
                row.getAvgContactingScore(),
                row.getMedianContactingScore(),
                row.getBestContactingScore(),
                row.getWorstContactingScore(),
                row.getClosestDistance(),
                row.getAvgLigandMinDistance(),
                row.getAvgPoseMinDistance()
        );
    }

    private ResidueScoreBand toScoreBand(
            ResidueScoreBandProjection row
    ) {
        return new ResidueScoreBand(
                row.getRunId(),
                row.getStructureId(),
                row.getReceptorId(),
                row.getScoreLower(),
                row.getScoreUpper(),
                row.getResidueId(),
                row.getChain(),
                row.getResidueNumber(),
                row.getResidueName(),
                row.getLigandCount(),
                row.getContactingLigandCount(),
                row.getContactingLigandFraction(),
                row.getPoseCount(),
                row.getContactingPoseCount(),
                row.getContactingPoseFraction(),
                row.getAvgContactingScore(),
                row.getMedianContactingScore(),
                row.getBestContactingScore(),
                row.getWorstContactingScore(),
                row.getClosestDistance(),
                row.getAvgLigandMinDistance(),
                row.getAvgPoseMinDistance()
        );
    }

    public record DockingRunSummary(
            long id,
            long structureId,
            long receptorId,
            LocalDateTime createdAt,
            long totalLigandCount,
            long totalPoseCount
    ) {
    }

    public record ResidueAnalysis(
            long runId,
            long structureId,
            long receptorId,
            long residueId,
            String chain,
            int residueNumber,
            String residueName,
            long totalLigandCount,
            long contactingLigandCount,
            double contactingLigandFraction,
            long totalPoseCount,
            long contactingPoseCount,
            double contactingPoseFraction,
            long totalGoodLigandCount,
            long goodContactingLigandCount,
            double goodContactingLigandFraction,
            long totalBadLigandCount,
            long badContactingLigandCount,
            double badContactingLigandFraction,
            double contactFractionDifference,
            Double enrichmentRatio,
            Double log2Enrichment,
            Double avgContactingScore,
            Double medianContactingScore,
            Double bestContactingScore,
            Double worstContactingScore,
            Double closestDistance,
            Double avgLigandMinDistance,
            Double avgPoseMinDistance
    ) {
    }

    public record ResidueScoreBand(
            long runId,
            long structureId,
            long receptorId,
            double scoreLower,
            double scoreUpper,
            long residueId,
            String chain,
            int residueNumber,
            String residueName,
            long ligandCount,
            long contactingLigandCount,
            double contactingLigandFraction,
            long poseCount,
            long contactingPoseCount,
            double contactingPoseFraction,
            Double avgContactingScore,
            Double medianContactingScore,
            Double bestContactingScore,
            Double worstContactingScore,
            Double closestDistance,
            Double avgLigandMinDistance,
            Double avgPoseMinDistance
    ) {
    }
}
