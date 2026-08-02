package totah.lab.web.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class DockingReportAggregateMapper {

    public Map<String, Object> map(
            DockingAnalysisService.DockingRunSummary run,
            List<DockingAnalysisService.ResidueAnalysis> residues,
            List<DockingAnalysisService.ResidueScoreBand> scoreBands
    ) {
        if (residues.isEmpty()) {
            throw new IllegalArgumentException(
                    "Docking residue summary must not be empty");
        }
        DockingAnalysisService.ResidueAnalysis first = residues.getFirst();
        if (first.runId() != run.id()) {
            throw new IllegalArgumentException(
                    "Docking residue summary belongs to a different run");
        }

        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("runId", run.id());
        aggregate.put("totalLigandCount", run.totalLigandCount());
        aggregate.put("totalPoseCount", run.totalPoseCount());
        aggregate.put("contactScoreThreshold",
                first.contactScoreThreshold());
        aggregate.put("scoreFilteredLigandCount",
                first.scoreFilteredLigandCount());
        aggregate.put("scoreFilteredPoseCount",
                first.scoreFilteredPoseCount());
        aggregate.put("residues", residues.stream()
                .map(this::mapResidue)
                .toList());
        aggregate.put("scoreBands", scoreBands.stream()
                .map(this::mapScoreBand)
                .toList());
        return Map.copyOf(aggregate);
    }

    private Map<String, Object> mapResidue(
            DockingAnalysisService.ResidueAnalysis residue
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("residueId", residue.residueId());
        row.put("chain", residue.chain());
        row.put("residueNumber", residue.residueNumber());
        row.put("residueName", residue.residueName());
        row.put("contactingLigandCount", residue.contactingLigandCount());
        row.put("contactingLigandFraction",
                residue.contactingLigandFraction());
        row.put("contactingPoseCount", residue.contactingPoseCount());
        row.put("contactingPoseFraction", residue.contactingPoseFraction());
        row.put("scoreFilteredLigandCount",
                residue.scoreFilteredLigandCount());
        row.put("scoreFilteredContactingLigandCount",
                residue.scoreFilteredContactingLigandCount());
        row.put("scoreFilteredContactingLigandFraction",
                residue.scoreFilteredContactingLigandFraction());
        row.put("scoreFilteredPoseCount",
                residue.scoreFilteredPoseCount());
        row.put("scoreFilteredContactingPoseCount",
                residue.scoreFilteredContactingPoseCount());
        row.put("scoreFilteredContactingPoseFraction",
                residue.scoreFilteredContactingPoseFraction());
        row.put("contactFractionDifference",
                residue.contactFractionDifference());
        putIfPresent(row, "enrichmentRatio", residue.enrichmentRatio());
        putIfPresent(row, "log2Enrichment", residue.log2Enrichment());
        putIfPresent(row, "avgContactingScore",
                residue.avgContactingScore());
        putIfPresent(row, "medianContactingScore",
                residue.medianContactingScore());
        putIfPresent(row, "bestContactingScore",
                residue.bestContactingScore());
        putIfPresent(row, "worstContactingScore",
                residue.worstContactingScore());
        putIfPresent(row, "closestDistance", residue.closestDistance());
        putIfPresent(row, "avgLigandMinDistance",
                residue.avgLigandMinDistance());
        putIfPresent(row, "avgPoseMinDistance",
                residue.avgPoseMinDistance());
        return Map.copyOf(row);
    }

    private Map<String, Object> mapScoreBand(
            DockingAnalysisService.ResidueScoreBand band
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("runId", band.runId());
        row.put("scoreLower", band.scoreLower());
        row.put("scoreUpper", band.scoreUpper());
        row.put("residueId", band.residueId());
        row.put("chain", band.chain());
        row.put("residueNumber", band.residueNumber());
        row.put("residueName", band.residueName());
        row.put("ligandCount", band.ligandCount());
        row.put("contactingLigandCount", band.contactingLigandCount());
        row.put("contactingLigandFraction",
                band.contactingLigandFraction());
        row.put("poseCount", band.poseCount());
        row.put("contactingPoseCount", band.contactingPoseCount());
        row.put("contactingPoseFraction", band.contactingPoseFraction());
        putIfPresent(row, "avgContactingScore", band.avgContactingScore());
        putIfPresent(row, "medianContactingScore",
                band.medianContactingScore());
        putIfPresent(row, "bestContactingScore",
                band.bestContactingScore());
        putIfPresent(row, "worstContactingScore",
                band.worstContactingScore());
        putIfPresent(row, "closestDistance", band.closestDistance());
        putIfPresent(row, "avgLigandMinDistance",
                band.avgLigandMinDistance());
        putIfPresent(row, "avgPoseMinDistance",
                band.avgPoseMinDistance());
        return Map.copyOf(row);
    }

    private void putIfPresent(
            Map<String, Object> values,
            String name,
            Object value
    ) {
        if (value != null) {
            values.put(name, value);
        }
    }
}
