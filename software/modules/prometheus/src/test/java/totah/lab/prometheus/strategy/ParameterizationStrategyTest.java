package totah.lab.prometheus.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.diagnosis.DiagnosisReport;
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
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.EvidenceGenerationPlan;
import totah.lab.prometheus.validation.DevelopmentDataset;

class ParameterizationStrategyTest {

    private static final MoleculeIdentity SAM =
            new MoleculeIdentity("SAM", "S-adenosylmethionine", "C15H22N6O5S+");
    private static final MoleculeIdentity DCMB =
            new MoleculeIdentity("DCMB", "DCMB", "C12H18O2");
    private static final QmProtocol QM =
            new QmProtocol("HF", "6-31G(d)", "none", "none", false, "Gaussian", "16");
    private static final QmProtocol MM =
            new QmProtocol("GAFF2", "none", "none", "vacuum", false, "AmberTools", "24");

    @Test
    void establishedSkeletonsAreMoleculeAgnosticAndFailClosed() {
        StrategyRegistry registry = StrategyRegistry.establishedSkeletons();
        StrategyContext sam = contextFor(SAM);
        StrategyContext dcmb = contextFor(DCMB);

        assertThat(registry.strategies()).hasSize(7);
        for (ParameterizationStrategy strategy : registry.strategies()) {
            StrategyProposal samProposal = strategy.propose(sam);
            StrategyProposal dcmbProposal = strategy.propose(dcmb);

            assertThat(samProposal.readiness())
                    .isEqualTo(StrategyReadiness.EXTERNAL_METHOD_NOT_INTEGRATED);
            assertThat(dcmbProposal.readiness())
                    .isEqualTo(StrategyReadiness.EXTERNAL_METHOD_NOT_INTEGRATED);
            assertThat(samProposal.evidenceRequirements()).isEmpty();
            assertThat(dcmbProposal.evidenceRequirements()).isEmpty();
            assertThat(samProposal.reasons().getFirst()).contains("no evidence requirements");
        }
    }

    @Test
    void developmentViewExcludesOtherMoleculesAndKeepsEvidenceDimensionsSeparate() {
        EvidenceBundle bundle = new EvidenceBundle();
        QuantumEvidence samQuantum = quantum(SAM, "sam-qm", -10.0);
        QuantumEvidence dcmbQuantum = quantum(DCMB, "dcmb-qm", -20.0);
        ClassicalEvidence samClassical = classical(SAM, "sam-mm", 3.0);
        bundle.add(samQuantum);
        bundle.add(dcmbQuantum);
        bundle.add(samClassical);
        DevelopmentDataset development = new DevelopmentDataset(
                "development",
                new LinkedHashSet<>(List.of(
                        samQuantum.identity().evidenceHash(),
                        dcmbQuantum.identity().evidenceHash(),
                        samClassical.identity().evidenceHash())),
                "mixed archive; strategy view must be molecule-scoped");

        DevelopmentEvidenceView view = DevelopmentEvidenceView.from(SAM, development, bundle);

        assertThat(view.quantumEvidence()).containsExactly(samQuantum);
        assertThat(view.classicalEvidence()).containsExactly(samClassical);
        assertThat(view.quantumEvidenceHashes())
                .containsExactly(samQuantum.identity().evidenceHash());
        assertThat(view.classicalEvidenceHashes())
                .containsExactly(samClassical.identity().evidenceHash());
        assertThatThrownBy(() -> view.quantumEvidence().add(samQuantum))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void planAssessmentReportsSupportingDimensionsWithoutInventingCandidateOrValues() {
        StrategyContext context = contextFor(SAM);
        ParameterizationStrategy strategy = new RespStrategy();
        EvidenceGenerationPlan emptyPlan = new EvidenceGenerationPlan(
                List.of(), List.of(), CostEstimate.zero());

        StrategyPlanAssessment assessment = strategy.assessPlan(context, emptyPlan);

        assertThat(assessment.readiness())
                .isEqualTo(StrategyReadiness.EXTERNAL_METHOD_NOT_INTEGRATED);
        assertThat(assessment.quantumEvidenceHashes()).hasSize(1);
        assertThat(assessment.classicalEvidenceHashes()).hasSize(1);
        assertThat(assessment.reasons()).allMatch(reason -> !reason.isBlank());
    }

    @Test
    void registryRejectsDuplicateStrategyIds() {
        assertThatThrownBy(() -> new StrategyRegistry(List.of(
                new RespStrategy(), new RespStrategy())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate strategy id");
    }

    private static StrategyContext contextFor(MoleculeIdentity molecule) {
        EvidenceBundle bundle = new EvidenceBundle();
        QuantumEvidence quantum = quantum(molecule, molecule.moleculeId() + "-qm", -1.0);
        ClassicalEvidence classical = classical(molecule, molecule.moleculeId() + "-mm", 1.0);
        bundle.add(quantum);
        bundle.add(classical);
        DevelopmentDataset development = new DevelopmentDataset(
                molecule.moleculeId() + "-development",
                new LinkedHashSet<>(List.of(
                        quantum.identity().evidenceHash(), classical.identity().evidenceHash())),
                "development evidence");
        return new StrategyContext(
                molecule,
                DevelopmentEvidenceView.from(molecule, development, bundle),
                new DiagnosisReport(molecule, List.of(), Instant.parse("2026-08-14T00:00:00Z")),
                Optional.empty());
    }

    private static QuantumEvidence quantum(MoleculeIdentity molecule, String geometryHash, double energy) {
        EvidenceIdentity identity = identity(
                molecule, geometryHash, CalculationType.ESP, QM, List.of("energy", "esp"));
        return new QuantumEvidence(
                identity,
                provenance("/archive/" + molecule.moleculeId() + "/qm.log"),
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED,
                Optional.of(energy), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                "converged");
    }

    private static ClassicalEvidence classical(
            MoleculeIdentity molecule, String geometryHash, double totalEnergy) {
        EvidenceIdentity identity = identity(
                molecule, geometryHash, CalculationType.ENERGY_DECOMPOSITION, MM,
                List.of("energy", "decomposition"));
        return new ClassicalEvidence(
                identity,
                "GAFF2",
                "/archive/" + molecule.moleculeId() + "/model.prmtop",
                new EnergyDecomposition(totalEnergy, null, null, null, null, null, null, null, null),
                provenance("/archive/" + molecule.moleculeId() + "/mm.json"),
                EvidenceAcceptanceState.ACCEPTED);
    }

    private static EvidenceIdentity identity(
            MoleculeIdentity molecule,
            String geometryHash,
            CalculationType type,
            QmProtocol protocol,
            List<String> outputs) {
        return new EvidenceIdentity(
                molecule,
                "atom-map-" + molecule.moleculeId(),
                new GeometryIdentity(geometryHash, 3),
                0,
                1,
                type,
                protocol,
                List.of(),
                outputs);
    }

    private static EvidenceProvenance provenance(String path) {
        return new EvidenceProvenance(
                path,
                "sha256",
                Instant.parse("2026-08-14T00:00:00Z"),
                List.of(),
                "test evidence");
    }
}
