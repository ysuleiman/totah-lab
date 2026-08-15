package totah.lab.prometheus.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.identity.GeometryIdentity;

class CanonicalEvidenceStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void importsOnceThenLoadsCanonicalJsonWithoutCallingImporterAgain() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        EvidenceImportDescriptor descriptor = descriptor("source-sha-1");
        AtomicInteger importCalls = new AtomicInteger();
        EvidenceImporter importer = source -> {
            importCalls.incrementAndGet();
            return bundle();
        };

        CanonicalEvidenceStore.CompilationResult first = store.compileOrLoad(
                temporaryDirectory.resolve("raw"), temporaryDirectory.resolve("compiled"), descriptor, importer);
        CanonicalEvidenceStore.CompilationResult second = store.compileOrLoad(
                temporaryDirectory.resolve("raw"), temporaryDirectory.resolve("compiled"), descriptor, importer);

        assertThat(first.status()).isEqualTo(
                CanonicalEvidenceStore.CompilationStatus.IMPORTED_NEW_GENERATION);
        assertThat(second.status()).isEqualTo(
                CanonicalEvidenceStore.CompilationStatus.LOADED_EXISTING);
        assertThat(importCalls).hasValue(1);
        assertThat(second.index().size()).isEqualTo(1);
        assertThat(second.index().quantum()).containsExactlyElementsOf(first.index().quantum());
    }

    @Test
    void normalStartupLoadsCurrentGenerationWithoutAnySourceArchive() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporaryDirectory.resolve("compiled");
        store.compileOrLoad(temporaryDirectory.resolve("raw"), compiled, descriptor("source-sha-1"),
                ignored -> bundle());

        CanonicalEvidenceStore.LoadedEvidence loaded = store.loadCurrent(compiled);

        assertThat(loaded.index().size()).isEqualTo(1);
        assertThat(loaded.manifest().quantumCount()).isEqualTo(1);
        assertThat(loaded.manifest().recordSha256()).hasSize(1);
    }

    @Test
    void changedSourceFingerprintCreatesANewImmutableGeneration() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporaryDirectory.resolve("compiled");
        AtomicInteger imports = new AtomicInteger();
        EvidenceImporter importer = ignored -> {
            imports.incrementAndGet();
            return bundle();
        };

        var first = store.compileOrLoad(temporaryDirectory, compiled, descriptor("source-sha-1"), importer);
        var second = store.compileOrLoad(temporaryDirectory, compiled, descriptor("source-sha-2"), importer);

        assertThat(imports).hasValue(2);
        assertThat(first.manifest().importDescriptor().generationId())
                .isNotEqualTo(second.manifest().importDescriptor().generationId());
        try (var generations = Files.list(compiled.resolve("generations"))) {
            assertThat(generations).hasSize(2);
        }
    }

    @Test
    void refusesToLoadTamperedCanonicalEvidence() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporaryDirectory.resolve("compiled");
        var result = store.compileOrLoad(temporaryDirectory, compiled, descriptor("source-sha-1"),
                ignored -> bundle());
        Path generation = compiled.resolve("generations")
                .resolve(result.manifest().importDescriptor().generationId());
        Path record;
        try (var records = Files.list(generation.resolve("quantum"))) {
            record = records.findFirst().orElseThrow();
        }
        Files.writeString(record, "{}\n");

        assertThatThrownBy(() -> store.loadCurrent(compiled))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("checksum mismatch");
    }

    private static EvidenceImportDescriptor descriptor(String sourceFingerprint) {
        return new EvidenceImportDescriptor(
                "publication-archive", sourceFingerprint, "generic-test-importer", "1.0.0",
                CanonicalEvidenceStore.SCHEMA_VERSION);
    }

    private static EvidenceBundle bundle() {
        EvidenceBundle bundle = new EvidenceBundle();
        GeometryIdentity geometry = new GeometryIdentity("geometry-sha", TslFixtures.canonicalMap().size());
        bundle.add(EvidenceFixtures.acceptedQuantum(
                EvidenceFixtures.identity(
                        totah.lab.prometheus.evidence.CalculationType.SINGLE_POINT,
                        EvidenceFixtures.PBE_DEF2_SVP,
                        geometry),
                -500.123));
        return bundle;
    }
}
