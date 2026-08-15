package totah.lab.prometheus.ingest;

import java.io.IOException;
import java.nio.file.Path;

import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.recovery.authoritative.AuthoritativeEvidenceEnricher;
import totah.lab.prometheus.store.EvidenceImporter;

/** Adapter keeping TSL archive knowledge outside the generic canonical store. */
public final class ArchiveEvidenceImporter implements EvidenceImporter {

    private final LegacyPhase2ArchiveIngester ingester;

    public ArchiveEvidenceImporter() {
        this(new LegacyPhase2ArchiveIngester());
    }

    ArchiveEvidenceImporter(LegacyPhase2ArchiveIngester ingester) {
        this.ingester = ingester;
    }

    @Override
    public EvidenceBundle importEvidence(Path sourceRoot) throws IOException {
        EvidenceBundle extracted = ingester.ingest(sourceRoot).bundle();
        return new AuthoritativeEvidenceEnricher().enrich(sourceRoot, extracted);
    }
}
