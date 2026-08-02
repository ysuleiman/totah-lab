package totah.lab.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import totah.lab.report.model.PocketReport;
import totah.lab.web.service.PocketReportApplicationService;
import totah.lab.web.service.PocketReportDocxService;
import totah.lab.web.service.PocketReportPdfService;

import java.io.IOException;

@RestController
@RequestMapping("/api/pockets")
public final class PocketReportController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument"
                    + ".wordprocessingml.document"
    );

    private final PocketReportApplicationService reportService;
    private final PocketReportPdfService pdfService;
    private final PocketReportDocxService docxService;

    public PocketReportController(
            PocketReportApplicationService reportService,
            PocketReportPdfService pdfService
    ) {
        this(reportService, pdfService, null);
    }

    @Autowired
    public PocketReportController(
            PocketReportApplicationService reportService,
            PocketReportPdfService pdfService,
            PocketReportDocxService docxService
    ) {
        this.reportService = reportService;
        this.pdfService = pdfService;
        this.docxService = docxService;
    }

    @GetMapping("/{pocketId}/report")
    public PocketReport report(
            @PathVariable("pocketId") long pocketId,
            @RequestParam("runId") long runId
    ) throws IOException {
        return reportService.generate(pocketId, runId);
    }

    @GetMapping("/{pocketId}/report/document")
    public PocketReportApplicationService.PocketReportDocument document(
            @PathVariable("pocketId") long pocketId,
            @RequestParam("runId") long runId
    ) throws IOException {
        return reportService.generateDocument(pocketId, runId);
    }

    @GetMapping(
            value = "/{pocketId}/report.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> pdf(
            @PathVariable("pocketId") long pocketId,
            @RequestParam("runId") long runId
    ) throws IOException {
        var document = reportService.generateDocument(pocketId, runId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + pdfService.filename(document, runId)
                                + "\""
                )
                .body(pdfService.render(document, runId));
    }

    @GetMapping(
            value = "/{pocketId}/report.docx",
            produces = "application/vnd.openxmlformats-officedocument"
                    + ".wordprocessingml.document"
    )
    public ResponseEntity<byte[]> docx(
            @PathVariable("pocketId") long pocketId,
            @RequestParam("runId") long runId
    ) throws IOException {
        var document = reportService.generateDocument(pocketId, runId);
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + docxService.filename(document, runId)
                                + "\""
                )
                .body(docxService.render(document, runId));
    }
}
