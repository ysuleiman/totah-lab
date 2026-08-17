package totah.lab.athena.ligand.screening;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LigandDiversitySelectorTest {

    @Test
    void lockedPolicyUsesGlobalPointThreeFiveCoverage() {
        LigandDiversitySelector.Policy policy =
                LigandDiversitySelector.Policy.lockedMettl7b();

        assertThat(policy.globalCoverage()).isEqualTo(.35);
        assertThat(policy.withinScaffoldCoverage()).isEqualTo(.35);
        assertThat(policy.rareScaffoldMaximum()).isEqualTo(3);
    }

    @Test
    void keepsCohortsIndependentAndPreservesRareScaffolds() {
        List<LigandDiversitySelector.Candidate> candidates = List.of(
                candidate("d1", LigandDiversitySelector.Cohort.DRUG_LIKE,
                        "D1", "rare-d", Set.of(1, 2), false),
                candidate("f1", LigandDiversitySelector.Cohort.FRAGMENT,
                        "F1", "rare-f", Set.of(1, 2), false));

        LigandDiversitySelector.Result result =
                new LigandDiversitySelector().select(candidates);

        assertThat(result.drugLike().selected())
                .extracting(LigandDiversitySelector.Candidate::identifier)
                .containsExactly("d1");
        assertThat(result.fragment().selected())
                .extracting(LigandDiversitySelector.Candidate::identifier)
                .containsExactly("f1");
    }

    @Test
    void deduplicatesExactStructuresAndAuditsGlobalRepresentation() {
        List<LigandDiversitySelector.Candidate> candidates = List.of(
                candidate("a", LigandDiversitySelector.Cohort.DRUG_LIKE,
                        "same", "common", Set.of(1, 2, 3), false),
                candidate("duplicate", LigandDiversitySelector.Cohort.DRUG_LIKE,
                        "same", "common", Set.of(1, 2, 3), false),
                candidate("b", LigandDiversitySelector.Cohort.DRUG_LIKE,
                        "b", "common", Set.of(1, 2, 4), false),
                candidate("c", LigandDiversitySelector.Cohort.DRUG_LIKE,
                        "c", "common", Set.of(1, 2, 5), false),
                candidate("d", LigandDiversitySelector.Cohort.DRUG_LIKE,
                        "d", "common", Set.of(1, 2, 6), false));

        LigandDiversitySelector.CohortResult result =
                new LigandDiversitySelector().select(candidates).drugLike();

        assertThat(result.exactDuplicates()).containsEntry("duplicate", "a");
        assertThat(result.represented()).allSatisfy(representation ->
                assertThat(representation.ecfp4Tanimoto()).isGreaterThanOrEqualTo(.35));
    }

    @Test
    void protectsLowFsp3PolarHeteroaromaticFragmentInCommonFamily() {
        List<LigandDiversitySelector.Candidate> candidates = List.of(
                candidate("protected", LigandDiversitySelector.Cohort.FRAGMENT,
                        "p", "common", Set.of(1, 2, 3), true),
                candidate("f2", LigandDiversitySelector.Cohort.FRAGMENT,
                        "f2", "common", Set.of(1, 2, 4), false),
                candidate("f3", LigandDiversitySelector.Cohort.FRAGMENT,
                        "f3", "common", Set.of(1, 2, 5), false),
                candidate("f4", LigandDiversitySelector.Cohort.FRAGMENT,
                        "f4", "common", Set.of(1, 2, 6), false));

        LigandDiversitySelector.CohortResult result =
                new LigandDiversitySelector().select(candidates).fragment();

        assertThat(result.selected())
                .extracting(LigandDiversitySelector.Candidate::identifier)
                .contains("protected");
        assertThat(result.protectedStructures()).isEqualTo(1);
    }

    private static LigandDiversitySelector.Candidate candidate(
            String id, LigandDiversitySelector.Cohort cohort,
            String structure, String scaffold, Set<Integer> bits,
            boolean protectedFragment) {
        return new LigandDiversitySelector.Candidate(
                id, cohort, structure, scaffold, bits, protectedFragment);
    }
}
