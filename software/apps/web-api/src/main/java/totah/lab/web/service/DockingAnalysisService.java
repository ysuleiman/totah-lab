package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.web.persistence.DockingAnalysisRepository;
import totah.lab.web.persistence.DockingRunSummaryProjection;
import totah.lab.web.persistence.ResidueAnalysisProjection;
import totah.lab.web.persistence.ResidueScoreBandProjection;
import totah.lab.web.persistence.SelectivityScoreProjection;

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

    @Transactional(readOnly = true)
    public SelectivityPage getSelectivityScores(
            String sortBy,
            String direction,
            String search,
            int page,
            int size
    ) {
        String safeSort = switch (sortBy) {
            case "delta", "score7b", "score7a", "ligandId" -> sortBy;
            default -> "delta";
        };
        String safeDirection = "asc".equalsIgnoreCase(direction)
                ? "asc"
                : "desc";
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        String safeSearch = search == null ? "" : search.trim();
        List<SelectivityScoreProjection> rows =
                repository.findSelectivityScores(
                        safeSort,
                        safeDirection,
                        safeSearch,
                        safeSize,
                        (long) safePage * safeSize
                );
        long total = rows.isEmpty() ? 0 : rows.getFirst().getTotalCount();
        return new SelectivityPage(
                rows.stream().map(this::toSelectivityScore).toList(),
                total,
                safePage,
                safeSize,
                safeSort,
                safeDirection
        );
    }

    @Transactional(readOnly = true)
    public List<SelectivityScore> getSelectivityExport(
            String sortBy,
            String direction,
            String search
    ) {
        String safeSort = switch (sortBy) {
            case "delta", "score7b", "score7a", "ligandId" -> sortBy;
            default -> "delta";
        };
        String safeDirection = "asc".equalsIgnoreCase(direction)
                ? "asc"
                : "desc";
        String safeSearch = search == null ? "" : search.trim();
        return repository.findSelectivityScores(
                        safeSort,
                        safeDirection,
                        safeSearch,
                        10_000,
                        0
                ).stream()
                .map(this::toSelectivityScore)
                .toList();
    }

    private SelectivityScore toSelectivityScore(
            SelectivityScoreProjection row
    ) {
        return new SelectivityScore(
                row.getLigandId(),
                row.getLigandLabel(),
                row.getSmiles(),
                row.getScore7b(),
                row.getScore7a(),
                row.getDelta(),
                row.getRunId7b(),
                row.getRunId7a(),
                row.getPoseId7b(),
                row.getPoseId7a()
        );
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
                row.getContactScoreThreshold(),
                row.getScoreFilteredLigandCount(),
                row.getScoreFilteredContactingLigandCount(),
                row.getScoreFilteredContactingLigandFraction(),
                row.getScoreFilteredPoseCount(),
                row.getScoreFilteredContactingPoseCount(),
                row.getScoreFilteredContactingPoseFraction(),
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
            double contactScoreThreshold,
            long scoreFilteredLigandCount,
            long scoreFilteredContactingLigandCount,
            double scoreFilteredContactingLigandFraction,
            long scoreFilteredPoseCount,
            long scoreFilteredContactingPoseCount,
            double scoreFilteredContactingPoseFraction,
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

    public record SelectivityPage(
            List<SelectivityScore> items,
            long total,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
    }

    public record SelectivityScore(
            String ligandId,
            String ligandLabel,
            String smiles,
            double score7b,
            double score7a,
            double delta,
            long runId7b,
            long runId7a,
            long poseId7b,
            long poseId7a
    ) {
    }
}
