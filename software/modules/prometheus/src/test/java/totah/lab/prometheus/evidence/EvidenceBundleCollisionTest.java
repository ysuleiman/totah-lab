package totah.lab.prometheus.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

/**
 * Fail-closed collision and replay semantics of {@link EvidenceBundle}: a replay
 * that differs only in the ingestion timestamp is idempotent; a replay that differs
 * in ANY scientific or provenance field under the same evidence hash is a collision
 * and must throw. Each test below mutates exactly one field of a fixed base record
 * so the branch under test is unambiguous.
 */
class EvidenceBundleCollisionTest {

    private static final Instant T1 = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T00:00:00Z");

    // BUNDLE-C-01: classical replay idempotence — only ingestedAt differs.
    @Test
    void classicalReplayWithOnlyNewIngestionTimestampIsIdempotent() {
        EvidenceBundle bundle = new EvidenceBundle();
        assertThat(bundle.add(classical(T1, -5.25))).isTrue();
        assertThat(bundle.add(classical(T2, -5.25))).isFalse();
        assertThat(bundle.size()).isOne();
        assertThat(bundle.classical()).hasSize(1);
    }

    // BUNDLE-C-02: classical payload collision (different energy decomposition) fails closed.
    @Test
    void classicalPayloadCollisionFailsClosed() {
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(classical(T1, -5.25));

        assertThatThrownBy(() -> bundle.add(classical(T2, -6.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collision")
                .hasMessageContaining("classical");
        assertThat(bundle.size()).isOne();
    }

    // BUNDLE-C-03: quantum payload collision (different gradient) fails closed.
    @Test
    void quantumPayloadCollisionFailsClosed() {
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(baseQuantum(T1));

        assertThatThrownBy(() -> bundle.add(quantum(T2, "abc", List.of("parent-hash"), "note-v1",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.4)),
                Optional.of(List.of(1.0)), Optional.of(List.of(0.5)),
                Optional.of(-1.5), "converged")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collision")
                .hasMessageContaining("quantum");
        assertThat(bundle.size()).isOne();
    }

    // BUNDLE-C-04: replay differing only in the provenance note is a collision, not a replay.
    @Test
    void replayDiffersOnlyInProvenanceNoteFailsClosed() {
        assertSingleFieldCollision(quantum(T2, "abc", List.of("parent-hash"), "note-v2",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.3)),
                Optional.of(List.of(1.0)), Optional.of(List.of(0.5)),
                Optional.of(-1.5), "converged"));
    }

    // BUNDLE-C-05: replay differing only in derivedFrom hashes is a collision.
    @Test
    void replayDiffersOnlyInDerivedFromHashesFailsClosed() {
        assertSingleFieldCollision(quantum(T2, "abc", List.of("other-parent-hash"), "note-v1",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.3)),
                Optional.of(List.of(1.0)), Optional.of(List.of(0.5)),
                Optional.of(-1.5), "converged"));
    }

    // BUNDLE-C-06: replay differing only in the source checksum is a collision.
    @Test
    void replayDiffersOnlyInSourceChecksumFailsClosed() {
        assertSingleFieldCollision(quantum(T2, "xyz", List.of("parent-hash"), "note-v1",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.3)),
                Optional.of(List.of(1.0)), Optional.of(List.of(0.5)),
                Optional.of(-1.5), "converged"));
    }

    // BUNDLE-C-07: replay differing only in hessian values is a collision.
    @Test
    void replayDiffersOnlyInHessianValuesFailsClosed() {
        assertSingleFieldCollision(quantum(T2, "abc", List.of("parent-hash"), "note-v1",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.3)),
                Optional.of(List.of(2.0)), Optional.of(List.of(0.5)),
                Optional.of(-1.5), "converged"));
    }

    // BUNDLE-C-08: replay differing only in dipole values is a collision.
    @Test
    void replayDiffersOnlyInDipoleValuesFailsClosed() {
        assertSingleFieldCollision(quantum(T2, "abc", List.of("parent-hash"), "note-v1",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.3)),
                Optional.of(List.of(1.0)), Optional.of(List.of(0.6)),
                Optional.of(-1.5), "converged"));
    }

    // BUNDLE-C-09: replay differing only in interaction energy is a collision.
    @Test
    void replayDiffersOnlyInInteractionEnergyFailsClosed() {
        assertSingleFieldCollision(quantum(T2, "abc", List.of("parent-hash"), "note-v1",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.3)),
                Optional.of(List.of(1.0)), Optional.of(List.of(0.5)),
                Optional.of(-2.0), "converged"));
    }

    // BUNDLE-C-10: replay differing only in the convergence note is a collision.
    @Test
    void replayDiffersOnlyInConvergenceNoteFailsClosed() {
        assertSingleFieldCollision(quantum(T2, "abc", List.of("parent-hash"), "note-v1",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.3)),
                Optional.of(List.of(1.0)), Optional.of(List.of(0.5)),
                Optional.of(-1.5), "converged with different note"));
    }

    // BUNDLE-C-11: quantum and classical channels are separate namespaces —
    // the same evidence hash may exist once per channel without colliding.
    @Test
    void sameEvidenceHashCoexistsAcrossQuantumAndClassicalChannels() {
        EvidenceBundle bundle = new EvidenceBundle();
        QuantumEvidence quantum = baseQuantum(T1);
        ClassicalEvidence classical = new ClassicalEvidence(quantum.identity(), "GAFF2",
                "topology-hash",
                new EnergyDecomposition(-5.25, null, null, null, null, null, null, null, null),
                new EvidenceProvenance("/archive/mm.log", "def", T1, List.of(), "mm"),
                EvidenceAcceptanceState.ACCEPTED);

        assertThat(bundle.add(quantum)).isTrue();
        assertThat(bundle.add(classical)).isTrue();
        assertThat(bundle.size()).isEqualTo(2);
        assertThat(bundle.quantum()).containsExactly(quantum);
        assertThat(bundle.classical()).containsExactly(classical);
    }

    // BUNDLE-C-12: null evidence is rejected on both channels.
    @Test
    void nullEvidenceIsRejectedOnBothChannels() {
        EvidenceBundle bundle = new EvidenceBundle();
        assertThatThrownBy(() -> bundle.add((QuantumEvidence) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> bundle.add((ClassicalEvidence) null))
                .isInstanceOf(NullPointerException.class);
        assertThat(bundle.size()).isZero();
    }

    /** Adds the base record, then asserts the single-field-mutated replay collides. */
    private static void assertSingleFieldCollision(QuantumEvidence mutatedReplay) {
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(baseQuantum(T1));

        assertThatThrownBy(() -> bundle.add(mutatedReplay))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collision");
        assertThat(bundle.size()).isOne();
    }

    private static QuantumEvidence baseQuantum(Instant ingestedAt) {
        return quantum(ingestedAt, "abc", List.of("parent-hash"), "note-v1",
                Optional.of(-10.0), Optional.of(List.of(0.1, 0.2, 0.3)),
                Optional.of(List.of(1.0)), Optional.of(List.of(0.5)),
                Optional.of(-1.5), "converged");
    }

    private static QuantumEvidence quantum(Instant ingestedAt, String sha256, List<String> derivedFrom,
            String provenanceNote, Optional<Double> energy, Optional<List<Double>> gradient,
            Optional<List<Double>> hessian, Optional<List<Double>> dipole,
            Optional<Double> interactionEnergy, String convergenceNote) {
        EvidenceIdentity identity = EvidenceFixtures.identity(CalculationType.FORCE_EVALUATION,
                EvidenceFixtures.PBE_DEF2_SVP, TslFixtures.geometryIdentityA());
        return new QuantumEvidence(identity,
                new EvidenceProvenance("/archive/result.json", sha256, ingestedAt, derivedFrom,
                        provenanceNote),
                ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED,
                energy, gradient, hessian, dipole, interactionEnergy, convergenceNote);
    }

    private static ClassicalEvidence classical(Instant ingestedAt, double totalKcalMol) {
        EvidenceIdentity identity = EvidenceFixtures.identity(
                CalculationType.CLASSICAL_FIXED_GEOMETRY_ENERGY,
                new QmProtocol("GAFF2", "none", "none", "none", false, "AmberTools", "23"),
                TslFixtures.geometryIdentityA());
        return new ClassicalEvidence(identity, "GAFF2", "topology-hash",
                new EnergyDecomposition(totalKcalMol, null, null, null, null, null, null, null, null),
                new EvidenceProvenance("/archive/mm.log", "def", ingestedAt, List.of(), "mm"),
                EvidenceAcceptanceState.ACCEPTED);
    }
}
