package totah.lab.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.StructureReportService;

@RestController
@RequestMapping("/api/structures")
public final class StructureReportController {

    private final StructureReportService reportService;

    public StructureReportController(StructureReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{structureId}/report")
    public StructureReportService.StructureReport report(
            @PathVariable("structureId") long structureId
    ) {
        return reportService.generate(structureId);
    }
}
