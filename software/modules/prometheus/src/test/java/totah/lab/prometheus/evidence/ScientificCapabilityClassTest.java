package totah.lab.prometheus.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScientificCapabilityClassTest {
    @Test void onlyAbInitioIsEligibleForProductionAbInitioEvidence() {
        assertThat(ScientificCapabilityClass.AB_INITIO.productionAbInitioEvidenceEligible()).isTrue();
        assertThat(ScientificCapabilityClass.REFERENCE_ASSISTED_DIAGNOSTIC.productionAbInitioEvidenceEligible()).isFalse();
        assertThat(ScientificCapabilityClass.SURROGATE.productionAbInitioEvidenceEligible()).isFalse();
    }
}
