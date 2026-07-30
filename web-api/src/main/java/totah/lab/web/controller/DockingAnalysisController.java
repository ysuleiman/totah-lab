package totah.lab.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.DockingAnalysisService;
import totah.lab.web.service.SelectivityWorkbookService;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api")
public final class DockingAnalysisController {

    private final DockingAnalysisService service;
    private final SelectivityWorkbookService workbookService;

    public DockingAnalysisController(DockingAnalysisService service) {
        this(service, new SelectivityWorkbookService());
    }

    @Autowired
    public DockingAnalysisController(
            DockingAnalysisService service,
            SelectivityWorkbookService workbookService
    ) {
        this.service = service;
        this.workbookService = workbookService;
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

    @GetMapping("/selectivity/scores")
    public DockingAnalysisService.SelectivityPage selectivityScores(
            @RequestParam(name = "sortBy", defaultValue = "delta")
            String sortBy,
            @RequestParam(name = "direction", defaultValue = "desc")
            String direction,
            @RequestParam(name = "search", defaultValue = "")
            String search,
            @RequestParam(name = "page", defaultValue = "0")
            int page,
            @RequestParam(name = "size", defaultValue = "50")
            int size
    ) {
        return service.getSelectivityScores(
                sortBy,
                direction,
                search,
                page,
                size
        );
    }

    @GetMapping("/selectivity/scores.xlsx")
    public ResponseEntity<byte[]> selectivityWorkbook(
            @RequestParam(name = "sortBy", defaultValue = "delta")
            String sortBy,
            @RequestParam(name = "direction", defaultValue = "desc")
            String direction,
            @RequestParam(name = "search", defaultValue = "")
            String search
    ) throws IOException {
        byte[] workbook = workbookService.create(
                service.getSelectivityExport(sortBy, direction, search)
        );
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet"
                ))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"mettl7b-mettl7a-selectivity.xlsx\""
                )
                .body(workbook);
    }
}
