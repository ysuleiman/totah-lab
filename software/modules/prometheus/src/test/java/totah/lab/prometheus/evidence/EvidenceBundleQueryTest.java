package totah.lab.prometheus.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.identity.GeometryIdentity;

/**
 * Query semantics of {@link EvidenceBundle} over a deliberately mixed bundle:
 * several calculation types, two protocols, accepted and non-accepted records,
 * one exact-duplicate replay, and one classical record.
 */
class EvidenceBundleQueryTest {

    private static final Instant T1 = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T00:00:00Z");

    private final QuantumEvidence forcePbeGeometryA = acceptedQuantum(
            CalculationType.FORCE_EVALUATION, EvidenceFixtures.PBE_DEF2_SVP,
            TslFixtures.geometryIdentityA(), -10.0);
    private final QuantumEvidence failedSinglePointPbe = new QuantumEvidence(
            EvidenceFixtures.identity(CalculationType.SINGLE_POINT, EvidenceFixtures.PBE_DEF2_SVP,
                    TslFixtures.geometryIdentityA()),
            EvidenceFixtures.provenance("/archive/failed.log"),
            ConvergenceStatus.FAILED, EvidenceAcceptanceState.FAILED_NUMERICALLY,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), "scf failed");
    private final QuantumEvidence forcePbe0GeometryA = acceptedQuantum(
            CalculationType.FORCE_EVALUATION, EvidenceFixtures.PBE0_DEF2_TZVP,
            TslFixtures.geometryIdentityA(), -11.0);
    private final QuantumEvidence forcePbeGeometryB = acceptedQuantum(
            CalculationType.FORCE_EVALUATION, EvidenceFixtures.PBE_DEF2_SVP,
            TslFixtures.geometryIdentityB(), -12.0);
    private final ClassicalEvidence classical = new ClassicalEvidence(
            EvidenceFixtures.identity(CalculationType.CLASSICAL_FIXED_GEOMETRY_ENERGY,
                    new QmProtocol("GAFF2", "none", "none", "none", false, "AmberTools", "23"),
                    TslFixtures.geometryIdentityA()),
            "GAFF2", "topology-hash",
            new EnergyDecomposition(-5.25, null, null, null, null, null, null, null, null),
            new EvidenceProvenance("/archive/mm.log", "def", T1, List.of(), "mm"),
            EvidenceAcceptanceState.ACCEPTED);

    // BUNDLE-Q-01: byType filters on calculation type only.
    @Test
    void byTypeReturnsOnlyEvidenceOfThatCalculationType() {
        EvidenceBundle bundle = mixedBundle();

        assertThat(bundle.byType(CalculationType.FORCE_EVALUATION))
                .containsExactlyInAnyOrder(forcePbeGeometryA, forcePbe0GeometryA, forcePbeGeometryB);
        assertThat(bundle.byType(CalculationType.SINGLE_POINT)).containsExactly(failedSinglePointPbe);
        assertThat(bundle.byType(CalculationType.HESSIAN)).isEmpty();
    }

    // BUNDLE-Q-02: byProtocolKey filters on the canonical protocol key only.
    @Test
    void byProtocolKeyReturnsOnlyEvidenceUnderThatProtocol() {
        EvidenceBundle bundle = mixedBundle();

        assertThat(bundle.byProtocolKey(EvidenceFixtures.PBE_DEF2_SVP.protocolKey()))
                .containsExactlyInAnyOrder(forcePbeGeometryA, failedSinglePointPbe, forcePbeGeometryB);
        assertThat(bundle.byProtocolKey(EvidenceFixtures.PBE0_DEF2_TZVP.protocolKey()))
                .containsExactly(forcePbe0GeometryA);
        assertThat(bundle.byProtocolKey(EvidenceFixtures.HF_631Gd.protocolKey())).isEmpty();
    }

    // BUNDLE-Q-03: accepted() excludes non-accepted records.
    @Test
    void acceptedReturnsOnlyAcceptedEvidence() {
        EvidenceBundle bundle = mixedBundle();

        assertThat(bundle.accepted())
                .containsExactlyInAnyOrder(forcePbeGeometryA, forcePbe0GeometryA, forcePbeGeometryB);
    }

    // BUNDLE-Q-04: findExactDuplicates finds the stored record of an identity and
    // stays empty for identities never ingested; an idempotent replay adds nothing.
    @Test
    void findExactDuplicatesFindsStoredRecordAndNothingElse() {
        EvidenceBundle bundle = mixedBundle();

        assertThat(bundle.findExactDuplicates(forcePbeGeometryA.identity()))
                .containsExactly(forcePbeGeometryA);
        assertThat(bundle.findExactDuplicates(EvidenceFixtures.identity(CalculationType.ESP,
                EvidenceFixtures.HF_631Gd, TslFixtures.geometryIdentityA()))).isEmpty();
    }

    // BUNDLE-Q-05: size counts both channels, and an exact-duplicate replay adds nothing.
    @Test
    void sizeCountsBothChannelsWithoutDoubleCountingReplays() {
        EvidenceBundle bundle = mixedBundle();

        assertThat(bundle.size()).isEqualTo(5);
        assertThat(bundle.quantum()).hasSize(4);
        assertThat(bundle.classical()).containsExactly(classical);
    }

    // BUNDLE-Q-06: query arguments reject null.
    @Test
    void queryArgumentsRejectNull() {
        EvidenceBundle bundle = mixedBundle();

        assertThatThrownBy(() -> bundle.byType(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> bundle.byProtocolKey(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> bundle.findExactDuplicates(null))
                .isInstanceOf(NullPointerException.class);
    }

    /** Four distinct quantum records plus one classical, with one replayed duplicate. */
    private EvidenceBundle mixedBundle() {
        EvidenceBundle bundle = new EvidenceBundle();
        assertThat(bundle.add(forcePbeGeometryA)).isTrue();
        assertThat(bundle.add(failedSinglePointPbe)).isTrue();
        assertThat(bundle.add(forcePbe0GeometryA)).isTrue();
        assertThat(bundle.add(forcePbeGeometryB)).isTrue();
        assertThat(bundle.add(classical)).isTrue();
        // Exact duplicate replay of the first record (only ingestedAt differs): idempotent.
        assertThat(bundle.add(new QuantumEvidence(forcePbeGeometryA.identity(),
                new EvidenceProvenance("/archive/evidence.log", "abc",
                        Instant.parse("2027-01-01T00:00:00Z"), List.of(), ""),
                ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED,
                Optional.of(-10.0), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), "converged normally"))).isFalse();
        return bundle;
    }

    private static QuantumEvidence acceptedQuantum(CalculationType type, QmProtocol protocol,
            GeometryIdentity geometry, double energyHartree) {
        return new QuantumEvidence(EvidenceFixtures.identity(type, protocol, geometry),
                new EvidenceProvenance("/archive/evidence.log", "abc", T2, List.of(), ""),
                ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED,
                Optional.of(energyHartree), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), "converged normally");
    }
}
