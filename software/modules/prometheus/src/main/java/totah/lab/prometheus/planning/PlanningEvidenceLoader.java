package totah.lab.prometheus.planning;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.store.CanonicalEvidenceStore;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;
import totah.lab.prometheus.store.GeneratedEvidenceRole;
import totah.lab.prometheus.store.GeneratedEvidenceStatus;

/**
 * Builds the evidence view used by planning from immutable canonical evidence
 * plus durable generated evidence. Only accepted primary results participate in
 * reuse; auxiliary validation and failed/rejected attempts remain registered but
 * cannot silently satisfy a development requirement.
 */
public final class PlanningEvidenceLoader {

    public EvidenceBundle load(Path canonicalStoreRoot, Path generatedEvidenceDirectory) throws IOException {
        Objects.requireNonNull(canonicalStoreRoot, "canonicalStoreRoot");
        Objects.requireNonNull(generatedEvidenceDirectory, "generatedEvidenceDirectory");

        var canonical = new CanonicalEvidenceStore().loadCurrent(canonicalStoreRoot).index();
        EvidenceBundle combined = new EvidenceBundle();
        canonical.quantum().forEach(combined::add);
        canonical.classical().forEach(combined::add);

        GeneratedEvidenceRegistry generated = new GeneratedEvidenceRegistry(generatedEvidenceDirectory);
        generated.entries().stream()
                .filter(entry -> entry.role() == GeneratedEvidenceRole.PRIMARY)
                .filter(entry -> entry.status() == GeneratedEvidenceStatus.ACCEPTED)
                .flatMap(entry -> entry.evidence().stream())
                .forEach(combined::add);
        return combined;
    }
}
