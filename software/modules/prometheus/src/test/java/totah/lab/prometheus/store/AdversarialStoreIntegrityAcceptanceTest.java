package totah.lab.prometheus.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.EnergyDecomposition;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

/**
 * TEST_ID: B1, B2 — adversarial acceptance tests for the canonical evidence
 * store integrity contract, written against the specification
 * (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md Layer B), not against the current
 * implementation.
 *
 * <p>B1 invariant: every manifest-listed payload names a file that exists and
 * matches; absence is tampering, not innocence. The failure must name the
 * missing payload and distinguish "missing" from "modified".
 *
 * <p>B2 invariant: one flipped bit anywhere in a payload invalidates the
 * store; load is refused and no partial store is surfaced.
 */
class AdversarialStoreIntegrityAcceptanceTest {

    @TempDir
    Path temporary;

    /**
     * TEST_ID: B1 — a checksum-listed store file is deleted; load must fail
     * naming the missing payload, and the failure must be distinguishable from
     * a content modification (B2's "checksum mismatch").
     */
    @Test
    void deletedManifestListedPayloadFailsLoadNamingTheMissingFile() throws Exception {
        Fixture fixture = compileStore();
        Path deleted = fixture.generationDirectory()
                .resolve("quantum")
                .resolve(fixture.quantum.identity().evidenceHash() + ".json");
        String deletedRelative = "quantum/" + fixture.quantum.identity().evidenceHash() + ".json";
        Files.delete(deleted);

        // loadGeneration must refuse and identify the missing payload as missing.
        assertThatThrownBy(() -> fixture.store().loadGeneration(fixture.generationDirectory()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(deletedRelative)
                .hasMessageContaining("missing");

        // The normal startup path must refuse identically.
        assertThatThrownBy(() -> fixture.store().loadCurrent(fixture.storeRoot()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(deletedRelative)
                .hasMessageContaining("missing");
    }

    /**
     * TEST_ID: B2 — one digit flipped inside a charge value of a classical
     * payload (not the hash field, not whitespace) must produce a checksum
     * mismatch, refuse the load, and surface no partial store.
     */
    @Test
    void flippedChargeDigitInClassicalPayloadFailsChecksum() throws Exception {
        Fixture fixture = compileStore();
        Path payload = fixture.generationDirectory()
                .resolve("classical")
                .resolve(fixture.classical.identity().evidenceHash() + ".json");
        String original = Files.readString(payload, StandardCharsets.UTF_8);
        String marker = "\"formalCharge\" : 0";
        assertThat(original).contains(marker);
        Files.writeString(payload, original.replace(marker, "\"formalCharge\" : 1"),
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> fixture.store().loadGeneration(fixture.generationDirectory()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("checksum mismatch")
                .hasMessageContaining("classical/" + fixture.classical.identity().evidenceHash() + ".json");

        assertThatThrownBy(() -> fixture.store().loadCurrent(fixture.storeRoot()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("checksum mismatch");
    }

    /**
     * TEST_ID: B1 (supporting oracle) — manifest keys are written with '/'
     * separators; the relative-path lookup key must be normalized the same way
     * on every platform, so a deleted-file lookup can never silently miss its
     * manifest entry because of a platform separator.
     */
    @Test
    void portableRelativePathNormalizesBackslashesToForwardSlashes() {
        assertThat(CanonicalEvidenceStore.portableRelativePath(Path.of("quantum", "abc.json")))
                .isEqualTo("quantum/abc.json");
        // A separator arriving as a literal backslash (Windows relativize output)
        // must map to the same manifest key.
        assertThat(CanonicalEvidenceStore.portableRelativePath(Path.of("quantum\\abc.json")))
                .isEqualTo("quantum/abc.json");
    }

    private record Fixture(
            CanonicalEvidenceStore store,
            Path storeRoot,
            Path generationDirectory,
            QuantumEvidence quantum,
            ClassicalEvidence classical) {
    }

    /** Compiles a minimal valid store: one quantum record, one classical record. */
    private Fixture compileStore() throws IOException {
        EvidenceIdentity quantumIdentity = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        QuantumEvidence quantum = new QuantumEvidence(
                quantumIdentity,
                EvidenceFixtures.provenance("/archive/tsl/quantum.log"),
                totah.lab.prometheus.evidence.ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED,
                Optional.of(-100.5),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "converged normally");

        EvidenceIdentity classicalIdentity = EvidenceFixtures.identity(
                CalculationType.CLASSICAL_FIXED_GEOMETRY_ENERGY,
                new totah.lab.prometheus.evidence.QmProtocol(
                        "GAFF2", "none", "none", "none", false, "AmberTools", "23.0"),
                TslFixtures.geometryIdentityA());
        ClassicalEvidence classical = new ClassicalEvidence(
                classicalIdentity,
                "GAFF2",
                "/archive/tsl/topology.prmtop",
                new EnergyDecomposition(-12.5, null, null, null, null, null, null, null, null),
                EvidenceFixtures.provenance("/archive/tsl/classical.log"),
                EvidenceAcceptanceState.ACCEPTED);

        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(quantum);
        bundle.add(classical);

        CanonicalEvidenceStore store = new CanonicalEvidenceStore();
        Path sourceRoot = temporary.resolve("source");
        Path storeRoot = temporary.resolve("store");
        EvidenceImportDescriptor descriptor = new EvidenceImportDescriptor(
                "adversarial-source", "adversarial-fingerprint", "test-importer", "1",
                CanonicalEvidenceStore.SCHEMA_VERSION);
        store.compileOrLoad(sourceRoot, storeRoot, descriptor, ignored -> bundle);
        Path generationDirectory = storeRoot.resolve("generations").resolve(descriptor.generationId());
        return new Fixture(store, storeRoot, generationDirectory, quantum, classical);
    }
}
