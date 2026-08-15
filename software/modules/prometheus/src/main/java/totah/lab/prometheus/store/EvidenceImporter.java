package totah.lab.prometheus.store;

import java.io.IOException;
import java.nio.file.Path;

import totah.lab.prometheus.evidence.EvidenceBundle;

/** Boundary for a source-format-specific, one-time evidence extraction. */
@FunctionalInterface
public interface EvidenceImporter {

    EvidenceBundle importEvidence(Path sourceRoot) throws IOException;
}
