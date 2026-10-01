package totah.lab.prometheus.evidence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.store.*;
import totah.lab.prometheus.execution.RawArtifact;
import totah.lab.mnemosyne.EvidenceHistory;
import totah.lab.mnemosyne.Observation;
import totah.lab.mnemosyne.ScientificReference;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class ScientificObservationProjectionTest {
    private static ScientificReference ref(ScientificReference.Kind kind, String id) {
        return new ScientificReference(kind, "fixture", id, "1");
    }
    @TempDir Path temporary;
    /** Frozen GeneratedEvidenceRegistryTest.fixture PRIMARY result, materialized only in JUnit's temporary directory. */
    private QuantumEvidence fixture() throws Exception {
        Path raw = temporary.resolve("result.json");
        Files.writeString(raw, "{\"energy\":-1.0}\n");
        var identity = new EvidenceIdentity(TslFixtures.TSL, TslFixtures.canonicalMap().canonicalHash(),
                TslFixtures.geometryIdentityA(), 0, 1, CalculationType.FORCE_EVALUATION,
                new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "gas", false, "PySCF", "2.14.0"),
                List.of(), List.of("energy", "gradient", "forces"));
        return new QuantumEvidence(identity, new EvidenceProvenance(raw.toString(), ArtifactChecksums.sha256(raw), Instant.EPOCH, List.of(), "generated"),
                ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED, Optional.of(-1.0),
                Optional.of(List.of(0.0, 0.0, 0.0)), Optional.empty(), Optional.empty(), Optional.empty(), "converged");
    }
    @Test void existingResultProjectsWithoutChangingItsDomainIdentityAndUnknownUncertainty() throws Exception {
        var evidence = fixture(); var before = evidence.toString(); var hash = evidence.identity().evidenceHash();
        var o = ScientificObservationAdapter.energy(evidence, ref(OBSERVATION, "o"), ref(ACTIVITY, "run"));
        assertEquals("-1.0", o.value().orElseThrow().text()); assertEquals("hartree", o.value().orElseThrow().unit());
        assertEquals(hash, o.method().id()); assertEquals(hash, o.provenance().receipt().id());
        assertEquals(evidence.provenance().sourcePath(), o.provenance().source().id());
        assertEquals(ArtifactChecksums.sha256(temporary.resolve("result.json")), o.provenance().artifact().id());
        assertEquals("sha256", o.provenance().artifact().namespace());
        assertEquals("{\"energy\":-1.0}\n", Files.readString(Path.of(o.provenance().source().id())));
        assertInstanceOf(Observation.Unknown.class, o.uncertainty()); assertEquals(before, evidence.toString());
        assertEquals(hash, evidence.identity().evidenceHash());
        assertTrue(o.limitations().stream().anyMatch(s -> s.contains("no PRIMARY/AUXILIARY")));
    }
    @Test void sameSpecificationAndValueCanDescribeIndependentRunObservationsWithoutChangingReuseIdentity() throws Exception {
        var evidence = fixture();
        var a = ScientificObservationAdapter.energy(evidence, ref(OBSERVATION, "a"), ref(ACTIVITY, "run-a"));
        var b = ScientificObservationAdapter.energy(evidence, ref(OBSERVATION, "b"), ref(ACTIVITY, "run-b"));
        assertEquals(a.method(), b.method()); assertEquals(a.value(), b.value()); assertNotEquals(a.activity(), b.activity());
        var history = new EvidenceHistory().append(a).append(b);
        assertEquals(2, history.observations().size());
        assertSame(history, history.append(ScientificObservationAdapter.energy(evidence, a.reference(), a.activity())));
        assertThrows(IllegalArgumentException.class, () -> history.append(ScientificObservationAdapter.energy(evidence, a.reference(), b.activity())));
    }
    @Test void projectingPrimaryAndAuxiliaryDoesNotChangeRegistryBytesOrReuseEligibility() throws Exception {
        var evidence = fixture();
        var registry = new GeneratedEvidenceRegistry(temporary.resolve("registry"));
        var artifact = new RawArtifact("result.json", evidence.provenance().sha256(), "result_json");
        registry.register("frozen-spec", evidence, GeneratedEvidenceRole.VALIDATION_AUXILIARY, temporary, List.of(artifact), "fixture only");
        byte[] before = Files.readAllBytes(registry.registryFile());
        ScientificObservationAdapter.energy(evidence, ref(OBSERVATION, "aux-projection"), ref(ACTIVITY, "run"));
        assertArrayEquals(before, Files.readAllBytes(registry.registryFile()));
        assertTrue(registry.reusable(evidence.identity().evidenceHash()).isEmpty());
        registry.register("frozen-spec", evidence, GeneratedEvidenceRole.PRIMARY, temporary, List.of(artifact), "fixture only");
        before = Files.readAllBytes(registry.registryFile());
        ScientificObservationAdapter.energy(evidence, ref(OBSERVATION, "primary-projection"), ref(ACTIVITY, "run"));
        assertArrayEquals(before, Files.readAllBytes(registry.registryFile()));
        assertEquals(evidence, registry.reusable(evidence.identity().evidenceHash()).orElseThrow());
    }
    @Test void acceptanceIsNotSilentlyPromotedAndUnavailableEnergyIsNotZero() throws Exception {
        var f = fixture();
        var failed = new QuantumEvidence(f.identity(), f.provenance(), ConvergenceStatus.NOT_CONVERGED,
                EvidenceAcceptanceState.FAILED_NUMERICALLY, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), "Not converged");
        var o = ScientificObservationAdapter.energy(failed, ref(OBSERVATION, "failed"), ref(ACTIVITY, "run"));
        assertEquals(Observation.Availability.FAILED_INVALID, o.availability()); assertTrue(o.value().isEmpty());
        assertTrue(o.limitations().contains("Domain acceptance=FAILED_NUMERICALLY"));
        assertEquals(EvidenceAcceptanceState.FAILED_NUMERICALLY, failed.acceptance());
        assertThrows(IllegalArgumentException.class, () -> ScientificObservationAdapter.energy(failed, ref(OBSERVATION, "o"), ref(METHOD, "spec-is-not-run")));
    }
    @Test void exchangedProjectionResolvesSpecResultArtifactsAndRoleWithoutChangingRegistry() throws Exception {
        var evidence = fixture(); var directory = temporary.resolve("registry");
        var registry = new GeneratedEvidenceRegistry(directory);
        var artifact = new RawArtifact("result.json", evidence.provenance().sha256(), "result_json");
        registry.register("frozen-spec", evidence, GeneratedEvidenceRole.PRIMARY, temporary, List.of(artifact), "fixture only");
        registry.register("frozen-spec", evidence, GeneratedEvidenceRole.VALIDATION_AUXILIARY, temporary, List.of(artifact), "fixture only");
        byte[] registryBefore = Files.readAllBytes(registry.registryFile());
        var original = ScientificObservationAdapter.energy(evidence, ref(OBSERVATION, "exchange-o"), ref(ACTIVITY, "run"));
        var exchange = new totah.lab.mnemosyne.EvidenceExchange();
        var snapshot = exchange.snapshot(ref(SNAPSHOT, "snapshot"), ref(ACTIVITY, "export"), Instant.parse("2026-10-01T00:00:00Z"),
                Optional.empty(), new EvidenceHistory().append(original));
        var file = temporary.resolve("exchange.json"); exchange.write(file, snapshot);
        var loaded = exchange.read(file); assertEquals(snapshot, loaded);
        var o = loaded.history().observations().get(original.reference());
        for (var role : GeneratedEvidenceRole.values()) {
            var resolver = new totah.lab.mnemosyne.ReferenceResolver<>(new ScientificReferenceResolution(directory, o, role));
            for (var reference : List.of(o.provenance().source(), o.provenance().receipt(), o.provenance().artifact(), o.method(), o.context())) {
                var result = resolver.resolve(reference);
                assertEquals(totah.lab.mnemosyne.ReferenceResolver.Status.RESOLVED, result.status(), result.reason());
                var entry = result.record().orElseThrow();
                assertEquals(evidence, entry.evidence().orElseThrow()); assertEquals(role, entry.role());
                assertEquals(evidence.identity(), entry.evidence().orElseThrow().identity());
                assertFalse(entry.lifecycle().isEmpty());
                assertEquals(List.of(o.provenance().artifact()), result.verification().orElseThrow().verifiedArtifacts());
            }
        }
        assertArrayEquals(registryBefore, Files.readAllBytes(registry.registryFile()));
        assertEquals(evidence, new GeneratedEvidenceRegistry(directory).reusable(evidence.identity().evidenceHash()).orElseThrow());
    }
    @Test void rawArtifactTamperingAndUnavailableRegistryFailClosedWithoutCreatingAStore() throws Exception {
        var evidence = fixture(); var directory = temporary.resolve("registry");
        var registry = new GeneratedEvidenceRegistry(directory);
        registry.register("frozen-spec", evidence, GeneratedEvidenceRole.PRIMARY, temporary,
                List.of(new RawArtifact("result.json", evidence.provenance().sha256(), "result_json")), "fixture only");
        var o = ScientificObservationAdapter.energy(evidence, ref(OBSERVATION, "o"), ref(ACTIVITY, "run"));
        var resolver = new totah.lab.mnemosyne.ReferenceResolver<>(new ScientificReferenceResolution(directory, o, GeneratedEvidenceRole.PRIMARY));
        Files.writeString(temporary.resolve("result.json"), "tampered");
        assertEquals(totah.lab.mnemosyne.ReferenceResolver.Status.CONFLICTING, resolver.resolve(o.provenance().artifact()).status());
        var missingDirectory = temporary.resolve("not-created");
        var missing = new totah.lab.mnemosyne.ReferenceResolver<>(new ScientificReferenceResolution(missingDirectory, o, GeneratedEvidenceRole.PRIMARY));
        assertEquals(totah.lab.mnemosyne.ReferenceResolver.Status.UNAVAILABLE, missing.resolve(o.method()).status());
        assertFalse(Files.exists(missingDirectory));
        var method = o.method();
        assertEquals(totah.lab.mnemosyne.ReferenceResolver.Status.UNSUPPORTED,
                resolver.resolve(new ScientificReference(method.kind(), method.namespace(), method.id(), "2")).status());
    }

}
