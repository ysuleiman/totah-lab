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
}
