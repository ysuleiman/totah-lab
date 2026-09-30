package totah.lab.prometheus.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

/**
 * Fail-closed falsification of every verification/rejection branch in
 * {@link CanonicalEvidenceStore}: schema gating, descriptor pinning, immutable
 * record conflict, manifest path-escape, record-count reconciliation, per-record
 * SHA-256, and the empty/missing-directory contract. Each test asserts the
 * rejection fires for the stated reason, not just that some exception is thrown.
 */
class CanonicalEvidenceStoreVerificationTest {

    @TempDir
    Path temporary;

    /**
     * TEST_ID: STORE-VERIFY-01 — a descriptor whose schemaVersion differs from
     * SCHEMA_VERSION must be rejected before the importer runs and before any
     * store directory is created.
     */
    @Test
    void unsupportedSchemaVersionIsRejectedBeforeAnyImportOrWrite() {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        EvidenceImportDescriptor invalid = new EvidenceImportDescriptor(
                "publication-archive", "source-sha-1", "generic-test-importer", "1.0.0",
                CanonicalEvidenceStore.SCHEMA_VERSION + 1);
        AtomicInteger importCalls = new AtomicInteger();

        assertThatThrownBy(() -> store.compileOrLoad(
                temporary.resolve("raw"), temporary.resolve("compiled"), invalid,
                ignored -> {
                    importCalls.incrementAndGet();
                    return bundleOf(quantum(-500.123));
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported canonical evidence schema")
                .hasMessageContaining(String.valueOf(CanonicalEvidenceStore.SCHEMA_VERSION + 1));

        assertThat(importCalls).hasValue(0);
        assertThat(temporary.resolve("compiled")).doesNotExist();
    }

    /**
     * TEST_ID: STORE-VERIFY-02 — a generation directory whose manifest names a
     * different import descriptor than the requested one (tampered manifest, same
     * directory name) must be refused on reload, even though every payload
     * checksum still verifies.
     */
    @Test
    void storedGenerationWithMismatchedDescriptorIsRejectedOnReload() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporary.resolve("compiled");
        EvidenceImportDescriptor descriptor = descriptor("source-sha-1");
        var first = store.compileOrLoad(temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(quantum(-500.123)));
        assertThat(first.status()).isEqualTo(
                CanonicalEvidenceStore.CompilationStatus.IMPORTED_NEW_GENERATION);

        Path manifest = compiled.resolve("generations").resolve(descriptor.generationId())
                .resolve("manifest.json");
        String original = Files.readString(manifest);
        assertThat(original).contains("source-sha-1");
        Files.writeString(manifest, original.replace("source-sha-1", "source-sha-tampered"));

        assertThatThrownBy(() -> store.compileOrLoad(
                temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(quantum(-500.123))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("stored generation descriptor does not match requested import");
    }

    /**
     * TEST_ID: STORE-VERIFY-03 — re-import into a generation whose manifest was
     * lost (interrupted first compile) where the importer now yields the same
     * scientific identity with different content must fail closed on the
     * immutable record, never silently overwrite it.
     */
    @Test
    void reImportWithSameIdentityButDifferentContentFailsClosedOnImmutableRecord() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporary.resolve("compiled");
        EvidenceImportDescriptor descriptor = descriptor("source-sha-1");
        QuantumEvidence original = quantum(-500.123);
        store.compileOrLoad(temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(original));

        Path generation = compiled.resolve("generations").resolve(descriptor.generationId());
        Path record = generation.resolve("quantum")
                .resolve(original.identity().evidenceHash() + ".json");
        String originalBytes = Files.readString(record);
        Files.delete(generation.resolve("manifest.json"));

        assertThatThrownBy(() -> store.compileOrLoad(
                temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(quantum(-999.0))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("immutable canonical record already exists with different content");

        assertThat(Files.readString(record)).isEqualTo(originalBytes);
    }

    /**
     * TEST_ID: STORE-VERIFY-03 (complementary branch) — the same interrupted
     * compile resumed with byte-identical content must be accepted as idempotent,
     * not mistaken for a conflict.
     */
    @Test
    void reImportWithIdenticalContentAfterManifestLossIsIdempotent() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporary.resolve("compiled");
        EvidenceImportDescriptor descriptor = descriptor("source-sha-1");
        store.compileOrLoad(temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(quantum(-500.123)));
        Files.delete(compiled.resolve("generations").resolve(descriptor.generationId())
                .resolve("manifest.json"));

        var resumed = store.compileOrLoad(temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(quantum(-500.123)));

        assertThat(resumed.status()).isEqualTo(
                CanonicalEvidenceStore.CompilationStatus.IMPORTED_NEW_GENERATION);
        assertThat(resumed.index().size()).isEqualTo(1);
        assertThat(store.loadCurrent(compiled).index().size()).isEqualTo(1);
    }

    /**
     * TEST_ID: STORE-VERIFY-04 — a manifest entry whose relative path escapes the
     * generation root must be rejected before any payload is trusted.
     */
    @Test
    void manifestEntryEscapingGenerationRootIsRejected() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporary.resolve("compiled");
        EvidenceImportDescriptor descriptor = descriptor("source-sha-1");
        QuantumEvidence quantum = quantum(-500.123);
        store.compileOrLoad(temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(quantum));

        Path generation = compiled.resolve("generations").resolve(descriptor.generationId());
        Path manifest = generation.resolve("manifest.json");
        String key = "quantum/" + quantum.identity().evidenceHash() + ".json";
        String original = Files.readString(manifest);
        assertThat(original).contains(key);
        Files.writeString(manifest, original.replace(key, "../outside.json"));

        assertThatThrownBy(() -> store.loadGeneration(generation))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("canonical evidence manifest path escapes generation")
                .hasMessageContaining("../outside.json");
        assertThatThrownBy(() -> store.loadCurrent(compiled))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("canonical evidence manifest path escapes generation");
    }

    /**
     * TEST_ID: STORE-VERIFY-05 — a manifest whose checksum table verifies in full
     * but whose declared counts cannot be reproduced from the on-disk records
     * (here: the only record was renamed to a non-.json suffix, so the reader
     * legitimately finds zero records) must be rejected as a count mismatch.
     */
    @Test
    void manifestCountMismatchWithOnDiskRecordsIsRejected() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporary.resolve("compiled");
        EvidenceImportDescriptor descriptor = descriptor("source-sha-1");
        QuantumEvidence quantum = quantum(-500.123);
        store.compileOrLoad(temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(quantum));

        Path generation = compiled.resolve("generations").resolve(descriptor.generationId());
        String hash = quantum.identity().evidenceHash();
        Files.move(generation.resolve("quantum").resolve(hash + ".json"),
                generation.resolve("quantum").resolve(hash + ".txt"));
        Path manifest = generation.resolve("manifest.json");
        String original = Files.readString(manifest);
        assertThat(original).contains("quantum/" + hash + ".json");
        Files.writeString(manifest,
                original.replace("quantum/" + hash + ".json", "quantum/" + hash + ".txt"));

        assertThatThrownBy(() -> store.loadGeneration(generation))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("canonical evidence counts do not match manifest");
    }

    /**
     * TEST_ID: STORE-VERIFY-06 — one flipped byte inside a canonical quantum
     * record must be caught by SHA-256 verification on load, and the failure must
     * name the tampered record.
     */
    @Test
    void singleFlippedByteInCanonicalRecordFailsSha256Verification() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporary.resolve("compiled");
        EvidenceImportDescriptor descriptor = descriptor("source-sha-1");
        QuantumEvidence quantum = quantum(-500.123);
        store.compileOrLoad(temporary.resolve("raw"), compiled, descriptor,
                ignored -> bundleOf(quantum));

        String relative = "quantum/" + quantum.identity().evidenceHash() + ".json";
        Path record = compiled.resolve("generations").resolve(descriptor.generationId())
                .resolve(relative);
        byte[] bytes = Files.readAllBytes(record);
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(record, bytes);

        assertThatThrownBy(() -> store.loadCurrent(compiled))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("canonical evidence checksum mismatch")
                .hasMessageContaining(relative);
        assertThatThrownBy(() -> store.loadGeneration(
                compiled.resolve("generations").resolve(descriptor.generationId())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("canonical evidence checksum mismatch")
                .hasMessageContaining(relative);
    }

    /**
     * TEST_ID: STORE-VERIFY-07 — an empty generation (no quantum, no classical
     * evidence) is a valid compiled state, and a missing record directory is
     * treated as empty rather than as an error, per the jsonFiles contract.
     */
    @Test
    void emptyGenerationLoadsAndMissingRecordDirectoriesAreTreatedAsEmpty() throws IOException {
        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path compiled = temporary.resolve("compiled");
        EvidenceImportDescriptor descriptor = descriptor("source-sha-1");

        var result = store.compileOrLoad(temporary.resolve("raw"), compiled, descriptor,
                ignored -> new EvidenceBundle());

        assertThat(result.status()).isEqualTo(
                CanonicalEvidenceStore.CompilationStatus.IMPORTED_NEW_GENERATION);
        assertThat(result.manifest().quantumCount()).isZero();
        assertThat(result.manifest().classicalCount()).isZero();
        assertThat(result.manifest().recordSha256()).isEmpty();
        assertThat(result.index().size()).isZero();
        assertThat(store.loadCurrent(compiled).index().size()).isZero();

        Path generation = compiled.resolve("generations").resolve(descriptor.generationId());
        Files.delete(generation.resolve("quantum"));
        Files.delete(generation.resolve("classical"));
        assertThat(store.loadGeneration(generation).index().size()).isZero();
    }

    private static EvidenceImportDescriptor descriptor(String sourceFingerprint) {
        return new EvidenceImportDescriptor(
                "publication-archive", sourceFingerprint, "generic-test-importer", "1.0.0",
                CanonicalEvidenceStore.SCHEMA_VERSION);
    }

    private static QuantumEvidence quantum(double energyHartree) {
        return EvidenceFixtures.acceptedQuantum(
                EvidenceFixtures.identity(
                        CalculationType.SINGLE_POINT,
                        EvidenceFixtures.PBE_DEF2_SVP,
                        TslFixtures.geometryIdentityA()),
                energyHartree);
    }

    private static EvidenceBundle bundleOf(QuantumEvidence... evidence) {
        EvidenceBundle bundle = new EvidenceBundle();
        for (QuantumEvidence quantum : evidence) {
            bundle.add(quantum);
        }
        return bundle;
    }
}
