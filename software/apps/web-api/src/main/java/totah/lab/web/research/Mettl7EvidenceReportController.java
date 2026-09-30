package totah.lab.web.research;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/reports")
public final class Mettl7EvidenceReportController {
    private final Mettl7EvidenceReportService service;
    private final Path repositoryRoot;

    public Mettl7EvidenceReportController(Mettl7EvidenceReportService service,
            @Value("${totah.research.root:${user.dir}}") String repositoryRoot) {
        this.service = service;
        this.repositoryRoot = Path.of(repositoryRoot).toAbsolutePath().normalize();
    }

    @GetMapping("/mettl7-rna-activator")
    public Mettl7EvidenceReportService.ReportView report() {
        return service.generate();
    }

    @GetMapping("/mettl7-rna-activator/artifacts/{artifactKey}")
    public ResponseEntity<byte[]> artifact(@PathVariable("artifactKey") String artifactKey)
            throws IOException {
        Path artifact = repositoryRoot.resolve(
                service.artifactRepositoryPath(artifactKey)).normalize();
        if (!artifact.startsWith(repositoryRoot) || !Files.isRegularFile(artifact)) {
            return ResponseEntity.notFound().build();
        }
        String filename = artifact.getFileName().toString();
        MediaType mediaType = filename.endsWith(".csv")
                ? MediaType.parseMediaType("text/csv")
                : MediaType.TEXT_PLAIN;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(Files.readAllBytes(artifact));
    }
}
