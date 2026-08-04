package totah.lab.web.controller;


import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.hermes.uniprot.UniProtException;
import totah.lab.web.service.AnnotationCsvRenderer;
import totah.lab.web.service.AnnotationMarkdownRenderer;
import totah.lab.web.service.AnnotationReport;
import totah.lab.web.service.ProteinAnnotationService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@RestController
@RequestMapping("/api/annotations")
public class AnnotationController {

    private static final int MAX_ACCESSIONS = 500;

    private final ProteinAnnotationService annotationService;

    public AnnotationController(
            ProteinAnnotationService annotationService
    ) {
        this.annotationService = annotationService;
    }

    @PostMapping("/top-hits")
    public ResponseEntity<byte[]> annotateTopHits(
            @RequestBody AnnotationRequest request,
            @RequestParam(name = "format", defaultValue = "csv")
            String format
    ) {
        if (request.accessions() == null
                || request.accessions().isEmpty()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "At least one UniProt accession is required"
            );
        }

        if (request.accessions().size() > MAX_ACCESSIONS) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "At most " + MAX_ACCESSIONS
                            + " accessions per request"
            );
        }

        AnnotationReport report = annotate(request.accessions());

        return switch (format.toLowerCase(Locale.ROOT)) {
            case "csv" -> download(
                    "top_hits_annotation.csv",
                    "text/csv",
                    AnnotationCsvRenderer.render(report)
            );
            case "md", "markdown" -> download(
                    "top_hits_annotation.md",
                    "text/markdown",
                    AnnotationMarkdownRenderer.render(report)
            );
            default -> throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported format: " + format
            );
        };
    }

    private AnnotationReport annotate(List<String> accessions) {
        try {
            return annotationService.annotateTopHits(accessions);
        } catch (UniProtException exception) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "UniProt request failed: " + exception.getMessage(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Annotation request interrupted",
                    exception
            );
        }
    }

    private static ResponseEntity<byte[]> download(
            String filename,
            String contentType,
            String content
    ) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(MediaType.parseMediaType(contentType))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    public record AnnotationRequest(List<String> accessions) {
    }
}
