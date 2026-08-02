package totah.lab.web.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.StructureReportPdfService;
import totah.lab.web.service.StructureReportService;

import java.io.IOException;

@RestController
@RequestMapping("/api/structures")
public final class StructureReportController {

    private final StructureReportService reportService;
    private final StructureReportPdfService pdfService;

    public StructureReportController(
            StructureReportService reportService,
            StructureReportPdfService pdfService
    ) {
        this.reportService = reportService;
        this.pdfService = pdfService;
    }

    @GetMapping("/{structureId}/report")
    public StructureReportService.StructureReport report(
            @PathVariable("structureId") long structureId
    ) {
        return reportService.generate(structureId);
    }

    @GetMapping(
            value = "/{structureId}/report.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> reportPdf(
            @PathVariable("structureId") long structureId
    ) throws IOException {
        StructureReportService.StructureReport report =
                reportService.generate(structureId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + pdfService.filename(report)
                                + "\""
                )
                .body(pdfService.render(report));
    }
}
