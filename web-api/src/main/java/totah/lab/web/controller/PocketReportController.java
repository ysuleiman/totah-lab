package totah.lab.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.report.model.PocketReport;
import totah.lab.web.service.PocketReportApplicationService;

import java.io.IOException;

@RestController
@RequestMapping("/api/pockets")
public final class PocketReportController {

    private final PocketReportApplicationService reportService;

    public PocketReportController(
            PocketReportApplicationService reportService
    ) {
        this.reportService = reportService;
    }

    @GetMapping("/{pocketId}/report")
    public PocketReport report(
            @PathVariable("pocketId") long pocketId,
            @RequestParam("runId") long runId
    ) throws IOException {
        return reportService.generate(pocketId, runId);
    }
}
