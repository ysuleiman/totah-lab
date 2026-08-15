package totah.lab.prometheus.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.candidate.EvidenceClass;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EnergyDecomposition;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.identity.CanonicalAtomId;
import totah.lab.prometheus.identity.CanonicalAtomMap;
import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;
import totah.lab.prometheus.ingest.LegacyPhase2ArchiveIngester.IngestionResult;

class ArchiveIngestionReportTest {

    private static final MoleculeIdentity MOLECULE =
            new MoleculeIdentity("TSL-RSH", "neutral TSL thiol", "C3HS");
    private static final CanonicalAtomMap ATOMS = new CanonicalAtomMap(MOLECULE, List.of(
            new CanonicalAtomId(10, "C9", "C"),
            new CanonicalAtomId(26, "S26", "S"),
            new CanonicalAtomId(56, "H56", "H")));
    private static final QmProtocol PBE =
            new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "gas", false, "PySCF", "2.14.0");
    private static final QmProtocol GAFF2 =
            new QmProtocol("GAFF2", "none", "none", "none", false, "AmberTools", "unknown");

    private final IngestionResult result = buildResult();
    private final ArchiveIngestionReport report = new ArchiveIngestionReport(result);

    @Test
    void computesSummaryCounts() {
        assertThat(report.totalEvidence()).isEqualTo(3);
        assertThat(report.acceptedCount()).isEqualTo(1);
        assertThat(report.byAcceptanceState())
                .containsEntry(EvidenceAcceptanceState.ACCEPTED, 1)
                .containsEntry(EvidenceAcceptanceState.PENDING, 1)
                .containsEntry(EvidenceAcceptanceState.GEOMETRY_INVALID, 1);
        assertThat(report.byCalculationType())
                .containsEntry(CalculationType.OPTIMIZATION, 1)
                .containsEntry(CalculationType.HESSIAN, 1)
                .containsEntry(CalculationType.ENERGY_DECOMPOSITION, 1);
        assertThat(report.byProtocolKey())
                .containsEntry(PBE.protocolKey(), 2)
                .containsEntry(GAFF2.protocolKey(), 1);
        assertThat(report.uniqueGeometries()).isEqualTo(2);
        assertThat(report.failedBranchCount()).isEqualTo(1);
        assertThat(report.issueCount()).isEqualTo(2);
    }

    @Test
    void rendersCsvWithOneRowPerEvidence() {
        String csv = report.toCsv();

        String[] lines = csv.split("\n");
        assertThat(lines[0]).isEqualTo(
                "evidenceHash,calculationType,protocolKey,geometrySha256Short,acceptance,convergence,energy,sourcePath");
        assertThat(lines).hasSize(4);
        assertThat(csv).contains("OPTIMIZATION");
        assertThat(csv).contains("ENERGY_DECOMPOSITION");
        assertThat(csv).contains("-1.5");
        String shortSha = CanonicalHashing.sha256Hex("geometry-sha-one").substring(0, 12);
        assertThat(csv).contains(shortSha);
        // protocol keys contain '|' but not commas, so rows keep 8 fields
        assertThat(lines[1].split(",", -1)).hasSize(8);
    }

    @Test
    void rendersMarkdownSummary() {
        String md = report.toMarkdown();

        assertThat(md).contains("# TSL-RSH archive ingestion report");
        assertThat(md).contains("- total evidence: 3");
        assertThat(md).contains("- accepted: 1");
        assertThat(md).contains("## Evidence by acceptance state");
        assertThat(md).contains("## Evidence by protocol");
        assertThat(md).contains("OFFCENTER_FIXED_CHARGE_MODEL_INSUFFICIENT");
        assertThat(md).contains("FAILED_CANDIDATE");
        assertThat(md).contains("[note]");
    }

    private static IngestionResult buildResult() {
        EvidenceBundle bundle = new EvidenceBundle();
        GeometryIdentity geometryOne = new GeometryIdentity(
                CanonicalHashing.sha256Hex("geometry-sha-one"), 3);
        GeometryIdentity geometryTwo = new GeometryIdentity(
                CanonicalHashing.sha256Hex("geometry-sha-two"), 3);

        bundle.add(new QuantumEvidence(
                identity(geometryOne, CalculationType.OPTIMIZATION, PBE),
                provenance("execution-unit-05O/qm-native-minima/MIN01/result.json"),
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED,
                Optional.of(-1.5),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                "status=CONVERGED_ACCEPTED"));
        bundle.add(new QuantumEvidence(
                identity(geometryOne, CalculationType.HESSIAN, PBE),
                provenance("execution-unit-05O/hessians/MIN01/result.json"),
                ConvergenceStatus.UNKNOWN,
                EvidenceAcceptanceState.PENDING,
                Optional.of(-1.5),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                ""));
        bundle.add(new ClassicalEvidence(
                identity(geometryTwo, CalculationType.ENERGY_DECOMPOSITION, GAFF2),
                "GAFF2",
                "execution-unit-05O/final-19-point-force-field-diagnostic",
                new EnergyDecomposition(-1.42, 0.0, 0.0, 0.0, 0.0, 0.0, -1.61, 0.19, -1.42),
                provenance("execution-unit-05O/final-19-point-force-field-diagnostic/CLASSICAL_ENERGY_DECOMPOSITION.csv"),
                EvidenceAcceptanceState.GEOMETRY_INVALID));

        return new IngestionResult(
                bundle,
                ATOMS,
                List.of(new FailedCandidateRecord(
                        "offcenter-thiol-three-parameter",
                        "OFFCENTER_FIXED_CHARGE_MODEL_INSUFFICIENT",
                        EvidenceClass.FAILED_CANDIDATE,
                        "execution-unit-05O/offcenter-thiol-three-parameter/THREE_PARAMETER_DEVELOPMENT_DECISION.json",
                        List.of(),
                        "summary")),
                List.of(
                        IngestionIssue.note("execution-unit-05O/qm-native-minima/MIN02/raw_combined.log",
                                "raw_combined.log is empty (disclosed logging defect)"),
                        IngestionIssue.error("execution-unit-05O/hessians/MIN01/result.json",
                                "checksum mismatch")),
                Map.of(PBE.protocolKey(), "PBE-D3(BJ)/def2-SVP density-fitted gas phase",
                        GAFF2.protocolKey(), "GAFF2 fixed-geometry classical evaluation"));
    }

    private static EvidenceIdentity identity(
            GeometryIdentity geometry, CalculationType type, QmProtocol protocol) {
        return new EvidenceIdentity(
                MOLECULE, ATOMS.canonicalHash(), geometry, 0, 1, type, protocol,
                List.of(), List.of("energy"));
    }

    private static EvidenceProvenance provenance(String sourcePath) {
        return new EvidenceProvenance(
                sourcePath, "deadbeef", Instant.parse("2026-08-14T00:00:00Z"), List.of(), "");
    }
}
