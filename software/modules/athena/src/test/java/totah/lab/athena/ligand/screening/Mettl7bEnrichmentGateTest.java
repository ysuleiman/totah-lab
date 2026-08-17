package totah.lab.athena.ligand.screening;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7bEnrichmentGateTest {

    private final Mettl7bEnrichmentGate gate = new Mettl7bEnrichmentGate();

    @Test
    void balancedFragmentPreservesPolarSingleRingHeteroaromaticWithLowFsp3() {
        Mettl7bEnrichmentGate.Result result = gate.evaluate(
                Mettl7bEnrichmentGate.Cohort.FRAGMENT,
                evidence(155, 0, 1, 2, 1, 35, 1.2, 1, 11, 0.0,
                        1, true, false));

        assertThat(result.accepted()).isTrue();
        assertThat(result.preferences())
                .containsExactly("FSP3_BELOW_SECONDARY_PREFERENCE_0_20");
    }

    @Test
    void balancedFragmentRejectsMultipleAromaticRingsButNotForFsp3() {
        Mettl7bEnrichmentGate.Result result = gate.evaluate(
                Mettl7bEnrichmentGate.Cohort.FRAGMENT,
                evidence(190, 0, 1, 2, 2, 30, 1.8, 2, 14, 0.0,
                        1, true, false));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).containsExactly("AROMATIC_RINGS_GT_1");
    }

    @Test
    void drugLikePolicyUsesFrozenStage2Thresholds() {
        Mettl7bEnrichmentGate.Result result = gate.evaluate(
                Mettl7bEnrichmentGate.Cohort.DRUG_LIKE,
                evidence(260, 0, 1, 3, 3, 50, 2.2, 2, 18, .45,
                        1, true, false));

        assertThat(result.accepted()).isTrue();
    }

    private static Mettl7bEnrichmentGate.Evidence evidence(
            double mw, int charge, int hbd, int hba, int rotors, double tpsa,
            double logp, int aromaticRings, int heavyAtoms, double fsp3,
            int fused, boolean directional, boolean flatNonpolar) {
        return new Mettl7bEnrichmentGate.Evidence(
                new PhysicochemicalGate.Descriptors(mw, charge, hbd, hba,
                        rotors, tpsa, logp, aromaticRings, heavyAtoms, fsp3),
                fused, directional, flatNonpolar);
    }
}
