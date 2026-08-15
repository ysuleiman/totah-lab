package totah.lab.prometheus.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EnergyDecomposition;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;
import totah.lab.prometheus.store.EvidenceMemoryIndex;

class EvidenceInventoryServiceTest {

    private static final MoleculeIdentity TSL =
            new MoleculeIdentity("TSL-RSH", "neutral TSL", "C27H45ClO3S");
    private static final MoleculeIdentity SAM =
            new MoleculeIdentity("SAM", "S-adenosylmethionine", "C15H22N6O5S+");
    private static final QmProtocol QM =
            new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false, "ORCA", "5.0.4");
    private static final QmProtocol MM =
            new QmProtocol("GAFF2", "none", "none", "vacuum", false, "AmberTools", "24");

    @Test
    void snapshotKeepsDimensionsSeparateAndCountsExactMetadata() {
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(quantum(TSL, "geom-1", CalculationType.OPTIMIZATION,
                EvidenceAcceptanceState.ACCEPTED, completeProvenance("/raw/tsl-opt.out")));
        bundle.add(quantum(TSL, "geom-2", CalculationType.HESSIAN,
                EvidenceAcceptanceState.FAILED_NUMERICALLY, completeProvenance("/raw/tsl-hess.out")));
        bundle.add(quantum(SAM, "geom-3", CalculationType.OPTIMIZATION,
                EvidenceAcceptanceState.ACCEPTED, completeProvenance("/raw/sam-opt.out")));
        bundle.add(classical(TSL, "geom-1", EvidenceAcceptanceState.ACCEPTED,
                completeProvenance("/raw/tsl-mm.json"), "/models/tsl.prmtop"));

        EvidenceInventorySnapshot snapshot = new EvidenceInventoryService(bundle).snapshot();

        assertThat(snapshot.quantum().totalCount()).isEqualTo(3);
        assertThat(snapshot.quantum().countsByMolecule())
                .containsEntry("TSL-RSH", 2L)
                .containsEntry("SAM", 1L);
        assertThat(snapshot.quantum().countsByProtocol()).containsEntry(QM.protocolKey(), 3L);
        assertThat(snapshot.quantum().countsByCalculationType())
                .containsEntry(CalculationType.OPTIMIZATION, 2L)
                .containsEntry(CalculationType.HESSIAN, 1L);
        assertThat(snapshot.quantum().countsByAcceptance())
                .containsEntry(EvidenceAcceptanceState.ACCEPTED, 2L)
                .containsEntry(EvidenceAcceptanceState.FAILED_NUMERICALLY, 1L);

        assertThat(snapshot.classical().totalCount()).isEqualTo(1);
        assertThat(snapshot.classical().countsByProtocol()).containsEntry(MM.protocolKey(), 1L);
        assertThat(snapshot.classical().countsByAcceptance())
                .containsEntry(EvidenceAcceptanceState.ACCEPTED, 1L);
        assertThat(snapshot.provenanceGaps()).isEmpty();
    }

    @Test
    void exactQueriesDoNotInventRanksOrMergeQuantumAndClassicalResults() {
        EvidenceBundle bundle = new EvidenceBundle();
        QuantumEvidence tsl = quantum(TSL, "geom-1", CalculationType.ESP,
                EvidenceAcceptanceState.ACCEPTED, completeProvenance("/raw/tsl-esp.out"));
        QuantumEvidence sam = quantum(SAM, "geom-2", CalculationType.ESP,
                EvidenceAcceptanceState.ACCEPTED, completeProvenance("/raw/sam-esp.out"));
        ClassicalEvidence mm = classical(TSL, "geom-1", EvidenceAcceptanceState.ACCEPTED,
                completeProvenance("/raw/tsl-mm.json"), "/models/tsl.prmtop");
        bundle.add(tsl);
        bundle.add(sam);
        bundle.add(mm);
        EvidenceInventoryService service = new EvidenceInventoryService(new EvidenceMemoryIndex(bundle));
        EvidenceInventoryQuery query = new EvidenceInventoryQuery(
                Optional.of("TSL-RSH"),
                Optional.of(QM.protocolKey()),
                Optional.of(CalculationType.ESP),
                Optional.of(EvidenceAcceptanceState.ACCEPTED));

        assertThat(service.queryQuantum(query)).containsExactly(tsl);
        assertThat(service.queryClassical(query)).isEmpty();
        assertThat(service.queryQuantum(EvidenceInventoryQuery.all())).containsExactlyInAnyOrder(tsl, sam);
        assertThat(service.queryClassical(EvidenceInventoryQuery.all())).containsExactly(mm);
    }

    @Test
    void provenanceAuditReportsOnlyExplicitMetadataGaps() {
        EvidenceBundle bundle = new EvidenceBundle();
        EvidenceProvenance incomplete = new EvidenceProvenance(
                "",
                "",
                Instant.parse("2026-08-14T00:00:00Z"),
                List.of("missing-parent-hash"),
                "intentionally incomplete fixture");
        QmProtocol versionless =
                new QmProtocol("HF", "6-31G(d)", "none", "none", false, "Gaussian", "");
        EvidenceIdentity identity = identity(
                TSL, "geom-gap", CalculationType.ESP, versionless, List.of("energy", "esp"));
        bundle.add(new QuantumEvidence(
                identity,
                incomplete,
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.PROTOCOL_INCOMPLETE,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                "metadata incomplete"));
        bundle.add(classical(TSL, "geom-mm-gap", EvidenceAcceptanceState.PROTOCOL_INCOMPLETE,
                completeProvenance("/raw/mm.json"), ""));

        List<ProvenanceGap> gaps = new EvidenceInventoryService(bundle).snapshot().provenanceGaps();

        assertThat(gaps).extracting(ProvenanceGap::type).containsExactlyInAnyOrder(
                ProvenanceGapType.SOURCE_PATH_MISSING,
                ProvenanceGapType.SOURCE_CHECKSUM_MISSING,
                ProvenanceGapType.SOFTWARE_VERSION_MISSING,
                ProvenanceGapType.DERIVED_EVIDENCE_NOT_IN_INVENTORY,
                ProvenanceGapType.TOPOLOGY_REFERENCE_MISSING);
        assertThat(gaps).allSatisfy(gap -> {
            assertThat(gap.evidenceHash()).isNotBlank();
            assertThat(gap.detail()).isNotBlank();
        });
    }

    @Test
    void placeholderProtocolMetadataIsReportedAsUnresolved() {
        EvidenceBundle bundle = new EvidenceBundle();
        QmProtocol unresolved = new QmProtocol(
                "unknown", "none", "none", "none", false, "unknown", "unknown");
        bundle.add(new QuantumEvidence(
                identity(TSL, "geom-unknown", CalculationType.SINGLE_POINT, unresolved,
                        List.of("energy")),
                completeProvenance("/raw/unknown.out"),
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.EXCLUDED_BY_PROTOCOL,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                "protocol unresolved"));

        assertThat(new EvidenceInventoryService(bundle).snapshot().provenanceGaps())
                .extracting(ProvenanceGap::type)
                .containsExactlyInAnyOrder(
                        ProvenanceGapType.PROTOCOL_METHOD_MISSING,
                        ProvenanceGapType.PROTOCOL_SOFTWARE_MISSING,
                        ProvenanceGapType.SOFTWARE_VERSION_MISSING);
    }

    private static QuantumEvidence quantum(
            MoleculeIdentity molecule,
            String geometry,
            CalculationType type,
            EvidenceAcceptanceState acceptance,
            EvidenceProvenance provenance) {
        ConvergenceStatus convergence = acceptance == EvidenceAcceptanceState.FAILED_NUMERICALLY
                ? ConvergenceStatus.FAILED : ConvergenceStatus.CONVERGED;
        return new QuantumEvidence(
                identity(molecule, geometry, type, QM, List.of("energy")),
                provenance,
                convergence,
                acceptance,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                convergence.name());
    }

    private static ClassicalEvidence classical(
            MoleculeIdentity molecule,
            String geometry,
            EvidenceAcceptanceState acceptance,
            EvidenceProvenance provenance,
            String topologyReference) {
        return new ClassicalEvidence(
                identity(molecule, geometry, CalculationType.ENERGY_DECOMPOSITION, MM,
                        List.of("energy", "decomposition")),
                "GAFF2",
                topologyReference,
                new EnergyDecomposition(0.0, null, null, null, null, null, null, null, null),
                provenance,
                acceptance);
    }

    private static EvidenceIdentity identity(
            MoleculeIdentity molecule,
            String geometry,
            CalculationType type,
            QmProtocol protocol,
            List<String> outputs) {
        return new EvidenceIdentity(
                molecule,
                "atom-map-" + molecule.moleculeId(),
                new GeometryIdentity(geometry, 3),
                0,
                1,
                type,
                protocol,
                List.of(),
                outputs);
    }

    private static EvidenceProvenance completeProvenance(String path) {
        return new EvidenceProvenance(
                path,
                "sha256",
                Instant.parse("2026-08-14T00:00:00Z"),
                List.of(),
                "complete fixture");
    }
}
