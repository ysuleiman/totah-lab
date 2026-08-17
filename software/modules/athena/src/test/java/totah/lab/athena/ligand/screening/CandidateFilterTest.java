package totah.lab.athena.ligand.screening;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateFilterTest {

    private final CandidateFilter filter = new CandidateFilter();

    @Test
    void incomingFilterSelectsForDockingWithoutStructuralEvidence() {
        CandidateFilter.IncomingCandidate incoming = new CandidateFilter.IncomingCandidate(
                "MCULE-1", "CC1CCN(C)CC1O",
                new CandidateFilter.LibraryEvidence(true, true, true, true,
                        List.of("MCULE-1"), "purchasable"),
                "neutral tautomer 1",
                DrugLikenessAssessment.Pool.PRIMARY_DRUG_LIKE,
                new PhysicochemicalGate.Descriptors(
                        250.0, 0, 1, 3, 2, 55.0, 2.1, 1, 18, 0.45),
                List.of(), 0.30);

        CandidateFilter.IntakeDisposition result = filter.filterIncoming(incoming);

        assertThat(result.status()).isEqualTo(
                CandidateFilter.IntakeStatus.ELIGIBLE_FOR_DOCKING);
        assertThat(result.reason()).isEqualTo("passes pre-docking intake filters");
    }

    @Test
    void assignsPredictedLabelOnlyForResolvedStrongTslPattern() {
        CandidateDisposition result = filter.evaluate(candidate(
                DrugLikenessAssessment.Pool.PRIMARY_DRUG_LIKE, 0.30,
                selectiveEvidence(), tsl(4, 3, true), tsl(4, 0, true)));

        assertThat(result.status()).isEqualTo(
                CandidateDisposition.Status.PREDICTED_TMT1B_SELECTIVE_CANDIDATE);
        assertThat(result.tslInterference().stronglySupportsTmt1bSelectivity())
                .isTrue();
        assertThat(result.candidate().canonicalSmiles()).isEqualTo("CC1CCN(C)CC1O");
    }

    @Test
    void unresolvedTmt1aProducesProvisionalNotPredictedDisposition() {
        CandidateDisposition result = filter.evaluate(candidate(
                DrugLikenessAssessment.Pool.PRIMARY_DRUG_LIKE, 0.30,
                selectiveEvidence(), tsl(4, 4, true), tsl(0, 0, false)));

        assertThat(result.status()).isEqualTo(
                CandidateDisposition.Status.SELECTIVITY_REQUIRES_7A_TSL_RESOLUTION);
        assertThat(result.reason()).contains("must be resolved");
        assertThat(result.tslInterference().provisionallySupportsTmt1bSelectivity())
                .isTrue();
    }

    @Test
    void dockingScoreDifferenceCannotSupplySelectivityByItself() {
        CandidateDisposition result = filter.evaluate(candidate(
                DrugLikenessAssessment.Pool.PRIMARY_DRUG_LIKE, 0.30,
                new IsoformSelectivityComparator.Evidence(
                        false, false, false, false, false,
                        false, false, false, -5.0),
                tsl(4, 4, true), tsl(4, 0, true)));

        assertThat(result.stage()).isEqualTo(
                CandidateDisposition.Stage.ISOFORM_SELECTIVITY);
        assertThat(result.reason()).contains("docking score alone is insufficient");
    }

    @Test
    void retainsDcmbNeighborhoodAsControl() {
        CandidateDisposition result = filter.evaluate(candidate(
                DrugLikenessAssessment.Pool.PRIMARY_DRUG_LIKE, 0.60,
                selectiveEvidence(), tsl(2, 2, true), tsl(2, 0, true)));

        assertThat(result.status()).isEqualTo(
                CandidateDisposition.Status.DCMB_NEIGHBORHOOD_CONTROL);
    }

    @Test
    void computesCheapEvidenceEvenWhenLibraryRequirementFails() {
        CandidateFilter.Candidate base = candidate(
                DrugLikenessAssessment.Pool.COMPACT_FRAGMENT, 0.20,
                selectiveEvidence(), tsl(2, 2, true), tsl(2, 0, true));
        CandidateFilter.Candidate unavailable = copy(base,
                new CandidateFilter.LibraryEvidence(false, true, true, true,
                        List.of("MCULE-1"), "unavailable"), base.liabilities());

        CandidateDisposition result = filter.evaluate(unavailable);

        assertThat(result.stage()).isEqualTo(
                CandidateDisposition.Stage.LIBRARY_REQUIREMENT);
        assertThat(result.physicochemical()).isNotNull();
        assertThat(result.drugLikeness()).isNotNull();
        assertThat(result.liabilities()).isNotNull();
    }

    @Test
    void preservesEveryMachineReadableLiability() {
        CandidateFilter.Candidate base = candidate(
                DrugLikenessAssessment.Pool.PRIMARY_DRUG_LIKE, 0.20,
                selectiveEvidence(), tsl(2, 2, true), tsl(2, 0, true));
        List<ChemicalLiabilityGate.Finding> findings = List.of(
                new ChemicalLiabilityGate.Finding(
                        ChemicalLiabilityGate.Liability.PAINS, "PAINS motif X"),
                new ChemicalLiabilityGate.Finding(
                        ChemicalLiabilityGate.Liability.STRONG_REDOX_CYCLER,
                        "quinone alert"));

        CandidateDisposition result = filter.evaluate(
                copy(base, base.library(), findings));

        assertThat(result.liabilities().findings()).extracting(
                ChemicalLiabilityGate.Finding::code).containsExactly(
                ChemicalLiabilityGate.Liability.PAINS,
                ChemicalLiabilityGate.Liability.STRONG_REDOX_CYCLER);
    }

    private static CandidateFilter.Candidate candidate(
            DrugLikenessAssessment.Pool pool, double dcmb,
            IsoformSelectivityComparator.Evidence selectivity,
            TslInterferenceClassifier.Evidence bTsl,
            TslInterferenceClassifier.Evidence aTsl) {
        CandidateFilter.DockingEvidence bDocking = new CandidateFilter.DockingEvidence(
                "TMT1B+SAM", "protocol-1", List.of("family-b1"),
                List.of("L43", "G199"), List.of());
        CandidateFilter.DockingEvidence aDocking = new CandidateFilter.DockingEvidence(
                "TMT1A+SAM", "protocol-1", List.of("family-a1"),
                List.of("F43"), List.of());
        return new CandidateFilter.Candidate(
                "candidate-1", "CC1CCN(C)CC1O",
                new CandidateFilter.LibraryEvidence(true, true, true, true,
                        List.of("MCULE-1"), "purchasable"),
                "neutral tautomer 1", pool,
                new PhysicochemicalGate.Descriptors(
                        250.0, 0, 1, 3, 2, 55.0, 2.1, 1, 18, 0.45),
                List.of(), dcmb, bDocking, aDocking,
                new CanonicalPocketGate.Evidence(true, false, false, false),
                new PoseReproducibilityGate.Evidence(3, 2, false),
                new SamCompatibilityGate.Evidence(true, true, false, false, false),
                selectivity, bTsl, aTsl);
    }

    private static CandidateFilter.Candidate copy(
            CandidateFilter.Candidate base,
            CandidateFilter.LibraryEvidence library,
            List<ChemicalLiabilityGate.Finding> findings) {
        return new CandidateFilter.Candidate(base.candidateId(),
                base.canonicalSmiles(), library, base.dockingState(), base.pool(),
                base.descriptors(), findings, base.dcmbTanimoto(),
                base.tmt1bDocking(), base.tmt1aDocking(), base.canonicalPocket(),
                base.poseReproducibility(), base.samCompatibility(),
                base.isoformSelectivity(), base.tmt1bTslInterference(),
                base.tmt1aTslInterference());
    }

    private static IsoformSelectivityComparator.Evidence selectiveEvidence() {
        return new IsoformSelectivityComparator.Evidence(
                true, false, false, false, true,
                true, false, false, -0.2);
    }

    private static TslInterferenceClassifier.Evidence tsl(
            int evaluated, int interfered, boolean resolved) {
        return new TslInterferenceClassifier.Evidence(
                evaluated, interfered, resolved);
    }
}
