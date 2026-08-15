package totah.lab.prometheus.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import totah.lab.prometheus.store.CanonicalEvidenceStore;
import totah.lab.prometheus.store.EvidenceImportDescriptor;
import totah.lab.prometheus.store.SourceTreeFingerprint;

/**
 * One-time compiler from the historical TSL archive into Prometheus canonical
 * evidence JSON. Later application startup should call
 * {@link CanonicalEvidenceStore#loadCurrent(Path)} instead.
 */
public final class EvidenceStoreCompiler {

    /**
     * Version 2 makes the authoritative raw-artifact reconstruction pass a
     * prerequisite of the historical archive import.  Bumping the importer
     * version deliberately creates a fresh immutable canonical generation.
     */
    private static final String IMPORTER_VERSION = "2.0.1-authoritative-enrichment";

    private EvidenceStoreCompiler() {
    }

    public static CanonicalEvidenceStore.CompilationResult compile(
            Path archiveRoot,
            Path canonicalStoreRoot) throws IOException {

        String sourceFingerprint = SourceTreeFingerprint.calculate(archiveRoot, path -> {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.contains("sha256sums")
                    || name.endsWith("manifest.json")
                    || name.endsWith("manifest.csv");
        });
        EvidenceImportDescriptor descriptor = new EvidenceImportDescriptor(
                "mettl7-phase2-tsl-rsh",
                sourceFingerprint,
                LegacyPhase2ArchiveIngester.class.getName(),
                IMPORTER_VERSION,
                CanonicalEvidenceStore.SCHEMA_VERSION);
        return new CanonicalEvidenceStore().compileOrLoad(
                archiveRoot,
                canonicalStoreRoot,
                descriptor,
                new ArchiveEvidenceImporter());
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "usage: EvidenceStoreCompiler <archive-root> <canonical-store-root>");
        }
        CanonicalEvidenceStore.CompilationResult result = compile(
                Path.of(arguments[0]), Path.of(arguments[1]));
        System.out.printf(
                "status=%s generation=%s quantum=%d classical=%d total=%d%n",
                result.status(),
                result.manifest().importDescriptor().generationId(),
                result.manifest().quantumCount(),
                result.manifest().classicalCount(),
                result.index().size());
    }
}
