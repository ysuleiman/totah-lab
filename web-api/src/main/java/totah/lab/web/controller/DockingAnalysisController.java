package totah.lab.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.DockingAnalysisService;

import java.util.List;

@RestController
@RequestMapping("/api")
public final class DockingAnalysisController {

    private final DockingAnalysisService service;

    public DockingAnalysisController(DockingAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/structures/{structureId}/docking-runs")
    public List<DockingAnalysisService.DockingRunSummary> dockingRuns(
            @PathVariable("structureId") long structureId
    ) {
        return service.getRunsForStructure(structureId);
    }

    @GetMapping("/docking-runs/{runId}/residue-summary")
    public List<DockingAnalysisService.ResidueAnalysis> residueSummary(
            @PathVariable("runId") long runId
    ) {
        return service.getResidueSummary(runId);
    }

    @GetMapping("/docking-runs/{runId}/residue-score-bands")
    public List<DockingAnalysisService.ResidueScoreBand> residueScoreBands(
            @PathVariable("runId") long runId,
            @RequestParam(name = "residueId", required = false)
            Long residueId
    ) {
        return service.getResidueScoreBands(runId, residueId);
    }
}
