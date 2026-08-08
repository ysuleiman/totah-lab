package totah.lab.web.evidence;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/pockets")
public final class PocketEvidenceController {
    private final PocketEvidenceQuery evidenceQuery;

    public PocketEvidenceController(PocketEvidenceQuery evidenceQuery) {
        this.evidenceQuery = evidenceQuery;
    }

    @GetMapping("/{pocketId}/evidence")
    public PocketEvidenceView evidence(@PathVariable long pocketId)
            throws IOException {
        return evidenceQuery.get(pocketId);
    }
}
