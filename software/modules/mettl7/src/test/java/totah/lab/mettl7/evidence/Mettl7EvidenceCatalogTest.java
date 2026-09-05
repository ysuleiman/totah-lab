package totah.lab.mettl7.evidence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7EvidenceCatalogTest {
    private final Mettl7EvidenceCatalog catalog = new Mettl7EvidenceCatalog();

    @Test
    void preservesOpposedFunctionalAnchorsWithoutChangingTriageRules() {
        assertThat(catalog.find("2,3-dichloro-alpha-methylbenzylamine (DCMB)"))
                .get().extracting(Mettl7CompoundEvidence::currentClassification)
                .isEqualTo("EXPERIMENTALLY_A_SELECTIVE_INHIBITOR");
        assertThat(catalog.find("netarsudil")).get().satisfies(evidence -> {
            assertThat(evidence.currentClassification()).isEqualTo("EXPERIMENTALLY_B_SELECTIVE_INHIBITOR");
            assertThat(evidence.supersededClassifications()).containsExactly("B_COMPATIBLE_ONLY");
            assertThat(evidence.experimentalB()).contains("20 uM");
        });
    }

    @Test
    void preservesSharedProductiveSubstratesAndExactAssayConcentrations() {
        assertThat(catalog.find("7alpha-thiospironolactone (TSL)"))
                .get().extracting(Mettl7CompoundEvidence::experimentalA)
                .asString().contains("125 uM");
        assertThat(catalog.find("captopril"))
                .get().extracting(Mettl7CompoundEvidence::experimentalB)
                .asString().contains("500 uM");
    }

    @Test
    void preservesMutationNetworkRatherThanMasterSwitchClaim() {
        assertThat(catalog.find("2,3-dichloro-alpha-methylbenzylamine (DCMB)"))
                .get().extracting(Mettl7CompoundEvidence::mutationalEvidence)
                .asString().contains("Y47S", "F199G", "F43L", "S47Y", "does not confer");
    }
}
