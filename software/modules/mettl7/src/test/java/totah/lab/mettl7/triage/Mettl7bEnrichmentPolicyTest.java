package totah.lab.mettl7.triage;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.screening.Mettl7bEnrichmentGate;
import totah.lab.athena.ligand.screening.PhysicochemicalGate;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7bEnrichmentPolicyTest {
    @Test
    void compatibilityFacadeUsesAthenaAlgorithm() {
        var descriptors = new PhysicochemicalGate.Descriptors(
                260, 0, 1, 3, 3, 50, 2.2, 2, 18, .45);
        var evidence = new Mettl7bEnrichmentGate.Evidence(descriptors, 1, true, false);
        assertThat(new Mettl7bEnrichmentPolicy()
                .evaluate(Mettl7bEnrichmentGate.Cohort.DRUG_LIKE, evidence).accepted()).isTrue();
    }
}
