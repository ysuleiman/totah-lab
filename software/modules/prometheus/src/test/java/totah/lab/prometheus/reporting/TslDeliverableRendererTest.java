package totah.lab.prometheus.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.comparability.EnergyTarget;
import totah.lab.prometheus.diagnosis.DiagnosisReport;
import totah.lab.prometheus.diagnosis.FunctionalFormClassification;
import totah.lab.prometheus.diagnosis.FunctionalFormDiagnostic;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;
import totah.lab.prometheus.inventory.EvidenceInventoryService;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;
import totah.lab.prometheus.planning.EvidenceGenerationPlan;
import totah.lab.prometheus.planning.EvidenceRequirement;
import totah.lab.prometheus.planning.PlanDecision;
import totah.lab.prometheus.planning.RequirementResolution;
import totah.lab.prometheus.store.EvidenceMemoryIndex;

class ReviewDeliverableRendererTest {

    private static final MoleculeIdentity TSL =
            new MoleculeIdentity("TSL-RSH", "neutral TSL", "C27H45ClO3S");
    private static final QmProtocol PROTOCOL =
            new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false, "ORCA", "5.0.4");

    @Test
    void writesEveryRequiredDeliverableAndChecksumManifest(@TempDir Path temporary) throws Exception {
        Path output = temporary.resolve("package");

        new ReviewDeliverableRenderer().write(output, input());

        assertThat(ReviewDeliverableRenderer.DELIVERABLE_FILENAMES)
                .allSatisfy(filename -> assertThat(output.resolve(filename)).isRegularFile());
        assertThat(output.resolve("SHA256SUMS")).isRegularFile();
        assertThat(Files.readAllLines(output.resolve("SHA256SUMS")))
                .hasSize(ReviewDeliverableRenderer.DELIVERABLE_FILENAMES.size())
                .allSatisfy(line -> assertThat(line).matches("[0-9a-f]{64}  .+"));
    }

    @Test
    void renderingIsByteDeterministic(@TempDir Path temporary) throws Exception {
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");
        ReviewDeliverableRenderer renderer = new ReviewDeliverableRenderer();

        renderer.write(first, input());
        renderer.write(second, input());

        for (String filename : ReviewDeliverableRenderer.DELIVERABLE_FILENAMES) {
            assertThat(Files.readAllBytes(first.resolve(filename)))
                    .containsExactly(Files.readAllBytes(second.resolve(filename)));
        }
        assertThat(Files.readAllBytes(first.resolve("SHA256SUMS")))
                .containsExactly(Files.readAllBytes(second.resolve("SHA256SUMS")));
    }

    @Test
    void reportsOnlyCallerSuppliedScientificConclusionsAndValues(@TempDir Path temporary)
            throws Exception {
        Path output = temporary.resolve("package");

        new ReviewDeliverableRenderer().write(output, input());

        String diagnosis = Files.readString(output.resolve("PROMETHEUS_TSL_MODEL_DIAGNOSIS.md"));
        String strategy = Files.readString(output.resolve("PROMETHEUS_TSL_STRATEGY_COMPARISON.md"));
        String cost = Files.readString(output.resolve("PROMETHEUS_TSL_COST_ESTIMATE.md"));
        String decision = Files.readString(output.resolve("PROMETHEUS_TSL_EXECUTION_DECISION.json"));
        assertThat(diagnosis)
                .contains("HARMONIC_FORM_INSUFFICIENT", "caller-supplied diagnostic")
                .doesNotContain("ANGLE_LJ_COUPLED_DEFECT_SUPPORTED");
        assertThat(strategy).contains("CALLER_READINESS", "caller-supplied strategy reason");
        assertThat(cost).contains(
                "Jobs: 2",
                "Estimated remote cost USD: 4.5",
                "archived QUBEKit feasibility range: 8–12 CPU hours");
        assertThat(decision).contains("REVIEW_REQUIRED", "human review has not occurred");
    }

    @Test
    void inventoryCsvPreservesEvidenceIdentityAndRawProvenance(@TempDir Path temporary)
            throws Exception {
        Path output = temporary.resolve("package");
        ReviewDeliverableInput input = input();

        new ReviewDeliverableRenderer().write(output, input);

        QuantumEvidence evidence = input.evidence().quantum().getFirst();
        String csv = Files.readString(output.resolve("PROMETHEUS_TSL_EVIDENCE_INVENTORY.csv"));
        assertThat(csv)
                .contains(evidence.identity().evidenceHash())
                .contains(PROTOCOL.protocolKey())
                .contains("/raw/min01.out")
                .contains("source-checksum");
    }

    private static ReviewDeliverableInput input() {
        EvidenceBundle bundle = new EvidenceBundle();
        QuantumEvidence evidence = evidence();
        bundle.add(evidence);
        EvidenceMemoryIndex index = new EvidenceMemoryIndex(bundle);
        EvidenceRequirement requirement = new EvidenceRequirement(
                CalculationType.HESSIAN,
                PROTOCOL,
                EnergyTarget.FORCE_CONSTANT,
                "caller-supplied missing Hessian",
                DatasetRole.DEVELOPMENT,
                true,
                TSL,
                new GeometryIdentity("geometry-min01", 56));
        RequirementResolution resolution = new RequirementResolution(
                requirement,
                PlanDecision.BLOCKED,
                List.of(),
                "caller-supplied block reason");
        EvidenceGenerationPlan plan = new EvidenceGenerationPlan(
                List.of(resolution),
                List.of(),
                new CostEstimate(2, 3.0, 4.0, 5.0, 4.5));
        FunctionalFormDiagnostic diagnostic = new FunctionalFormDiagnostic(
                FunctionalFormClassification.HARMONIC_FORM_INSUFFICIENT,
                List.of("caller-supplied diagnostic"),
                List.of(evidence.identity().evidenceHash()),
                "diagnostic-v1");
        return new ReviewDeliverableInput(
                index,
                new EvidenceInventoryService(index).snapshot(),
                List.of(new ProtocolGroupRow(
                        "conformation",
                        PROTOCOL.protocolKey(),
                        List.of(CalculationType.OPTIMIZATION.name()),
                        List.of(evidence.identity().evidenceHash()),
                        "caller-supplied comparability statement")),
                new DiagnosisReport(
                        TSL, List.of(diagnostic), Instant.parse("2026-08-14T00:00:00Z")),
                List.of(new StrategyComparisonRow(
                        "qubekit",
                        "QUBEKit",
                        "CALLER_READINESS",
                        List.of(evidence.identity().evidenceHash()),
                        List.of("missing forces"),
                        List.of("caller-supplied strategy reason"))),
                plan,
                List.of("archived QUBEKit feasibility range: 8–12 CPU hours"),
                new ExecutionDecisionRecord(
                        "decision-1",
                        "REVIEW_REQUIRED",
                        false,
                        List.of("human review has not occurred"),
                        List.of(evidence.identity().evidenceHash())));
    }

    private static QuantumEvidence evidence() {
        EvidenceIdentity identity = new EvidenceIdentity(
                TSL,
                "canonical-map-hash",
                new GeometryIdentity("geometry-min01", 56),
                0,
                1,
                CalculationType.OPTIMIZATION,
                PROTOCOL,
                List.of(),
                List.of("energy", "coordinates"));
        return new QuantumEvidence(
                identity,
                new EvidenceProvenance(
                        "/raw/min01.out",
                        "source-checksum",
                        Instant.parse("2026-08-14T00:00:00Z"),
                        List.of(),
                        "raw accepted optimization"),
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED,
                Optional.of(-123.456),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                "converged");
    }
}
