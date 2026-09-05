package totah.lab.mettl7.triage;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.screening.ChemicalLiabilityGate;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7LigandTriageServiceTest {
    private final Mettl7LigandTriageService service = new Mettl7LigandTriageService();

    @Test
    void recognizesProductiveThiolOnlyWhenAllReactionStateDimensionsAgree() {
        Mettl7TriageResult result = service.assess(input("TSL",
                chemistry(true, true, true, false, false), contacts(), ExperimentalFeatures.none(), List.of()));
        assertThat(result.productiveStatePlausibility().level()).isEqualTo(AssessmentLevel.HIGH);
        assertThat(result.nextAction()).isEqualTo(NextAction.TEST_PRODUCTIVE_TURNOVER_A_B);
    }

    @Test
    void sulfurAloneIsNotSufficient() {
        Mettl7TriageResult result = service.assess(input("4-nitrobenzenethiol",
                chemistry(true, false, false, false, true), contacts(), ExperimentalFeatures.none(), List.of()));
        assertThat(result.productiveStatePlausibility().level()).isEqualTo(AssessmentLevel.LOW);
        assertThat(result.nextAction()).isEqualTo(NextAction.KEEP_AS_NEGATIVE_CONTROL);
    }

    @Test
    void preservesDcmbAsExperimentalAAnchorWithoutUniversalizingItsWall() {
        RecognitionFeatures recognition = contacts(Set.of("F43", "Y47", "F199", "S149", "K151"), Set.of());
        Mettl7TriageResult result = service.assess(input("DCMB",
                chemistry(false, false, true, false, false), recognition,
                new ExperimentalFeatures(false, true, false, false, true), List.of()));
        assertThat(result.aSelectivityPrior().level()).isEqualTo(AssessmentLevel.HIGH);
        assertThat(result.nextAction()).isEqualTo(NextAction.TEST_AS_A_SELECTIVE_PROSPECT);
    }

    @Test
    void suppressesWeakArylamineFalsePositive() {
        Mettl7TriageResult result = service.assess(input("benzylamine",
                chemistry(false, false, true, true, true), contacts(Set.of("K151"), Set.of("M40")),
                ExperimentalFeatures.none(), List.of()));
        assertThat(result.productiveStatePlausibility().level()).isEqualTo(AssessmentLevel.LOW);
        assertThat(result.nextAction()).isEqualTo(NextAction.KEEP_AS_NEGATIVE_CONTROL);
    }

    @Test
    void netarsudilRemainsBCompatibleOnly() {
        Mettl7TriageResult result = service.assess(input("netarsudil",
                chemistry(false, false, true, false, false), contacts(Set.of(), Set.of("M40", "L145", "W195")),
                new ExperimentalFeatures(false, false, false, true, false), List.of()));
        assertThat(result.bSelectivityPrior().level()).isEqualTo(AssessmentLevel.MODERATE);
        assertThat(result.bSelectivityPrior().reasons()).anyMatch(v -> v.contains("compatibility only"));
    }

    @Test
    void gatesElectrophileThroughCovalencyTest() {
        var finding = new ChemicalLiabilityGate.Finding(
                ChemicalLiabilityGate.Liability.REACTIVE_MICHAEL_ACCEPTOR, "reactive warhead");
        Mettl7TriageResult result = service.assess(input("electrophile",
                chemistry(false, false, true, false, false), contacts(), ExperimentalFeatures.none(), List.of(finding)));
        assertThat(result.nextAction()).isEqualTo(NextAction.TEST_COVALENCY_FIRST);
    }

    @Test
    void recognizesBProspectWithoutCallingItEstablished() {
        Mettl7TriageResult result = service.assess(input("BRICS-0003",
                chemistry(false, false, true, false, false),
                new RecognitionFeatures(Set.of(), Set.of("F36", "M40", "L145", "W195"), true, true, true, false),
                ExperimentalFeatures.none(), List.of()));
        assertThat(result.bSelectivityPrior().level()).isEqualTo(AssessmentLevel.MODERATE);
        assertThat(result.nextAction()).isEqualTo(NextAction.TEST_AS_B_SELECTIVE_PROSPECT);
    }

    @Test
    void recommendsUnmeasuredCofactorStates() {
        Mettl7TriageInput base = input("state-probe", chemistry(false, false, true, false, false),
                contacts(), ExperimentalFeatures.none(), List.of());
        Mettl7TriageInput stateInput = new Mettl7TriageInput(base.identifier(), base.smiles(), base.chemistry(),
                base.recognition(), base.experimental(), new CofactorEvidence(true, false, false, true),
                base.liabilities(), base.evidence());
        assertThat(service.assess(stateInput).cofactorStatePriority().priority())
                .containsExactly(CofactorState.SAM, CofactorState.SAH);
    }

    @Test
    void preservesEvidenceClassesAsSeparateObservations() {
        EvidenceObservation direct = evidence(EvidenceClass.DIRECT_EXPERIMENTAL, "paper");
        EvidenceObservation computed = evidence(EvidenceClass.COMPUTATIONAL, "matrix");
        Mettl7TriageInput input = input("mixed", chemistry(false, false, true, false, false),
                contacts(), ExperimentalFeatures.none(), List.of());
        input = new Mettl7TriageInput(input.identifier(), input.smiles(), input.chemistry(), input.recognition(),
                input.experimental(), input.cofactorEvidence(), input.liabilities(), List.of(direct, computed));
        assertThat(service.assess(input).preservedEvidence()).containsExactly(direct, computed);
    }

    @Test
    void outputIsDeterministic() throws IOException {
        Mettl7TriageInput input = input("repeat", chemistry(false, false, true, false, false),
                contacts(Set.of(), Set.of("M40", "L145")), ExperimentalFeatures.none(), List.of());
        Mettl7TriageJsonCodec codec = new Mettl7TriageJsonCodec();
        assertThat(codec.writeResult(service.assess(input)))
                .isEqualTo(codec.writeResult(service.assess(input)));
    }

    @Test
    void resultSerializesAndDeserializesWithoutEvidenceLoss() throws IOException {
        Mettl7TriageResult result = service.assess(input("roundtrip",
                chemistry(false, false, true, false, false), contacts(), ExperimentalFeatures.none(), List.of()));
        Mettl7TriageJsonCodec codec = new Mettl7TriageJsonCodec();
        assertThat(codec.readResult(codec.writeResult(result))).isEqualTo(result);
    }

    private static Mettl7TriageInput input(String id, ChemistryFeatures chemistry,
                                           RecognitionFeatures recognition,
                                           ExperimentalFeatures experimental,
                                           List<ChemicalLiabilityGate.Finding> liabilities) {
        return new Mettl7TriageInput(id, "C", chemistry, recognition, experimental,
                CofactorEvidence.none(), liabilities,
                List.of(evidence(EvidenceClass.STRUCTURAL_INFERENCE, "test fixture")));
    }

    private static ChemistryFeatures chemistry(boolean sulfur, boolean approach, boolean topology,
                                                boolean arylamine, boolean negative) {
        return new ChemistryFeatures(arylamine ? "ARYLAMINE" : sulfur ? "THIOL" : "OTHER",
                sulfur || arylamine, sulfur, approach, topology, arylamine, negative);
    }

    private static RecognitionFeatures contacts() { return contacts(Set.of(), Set.of()); }
    private static RecognitionFeatures contacts(Set<String> a, Set<String> b) {
        return new RecognitionFeatures(a, b, false, false, false, false);
    }

    private static EvidenceObservation evidence(EvidenceClass type, String source) {
        return new EvidenceObservation("fixture", source, Confidence.MODERATE, type,
                EvidenceTiming.RETROSPECTIVE, "fixture:path");
    }
}
