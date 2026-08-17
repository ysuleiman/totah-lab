package totah.lab.athena.tmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ParameterizationProvenanceTest {

    @Test
    void preservesSeparateChemicalStateAndParameterEvidence() {
        var provenance = new ParameterizationProvenance(
                "7alpha-thiospironolactone",
                "RS_MINUS",
                "canonical/source_geometry.sdf",
                -1,
                "AM1-BCC",
                "GAFF2",
                "parmchk2 candidate",
                "GAFF2 candidate",
                ParameterValidationStatus.CANDIDATE_REVIEW_REQUIRED,
                "a".repeat(64));

        assertEquals(-1, provenance.formalCharge());
        assertEquals("RS_MINUS", provenance.protonationState());
        assertEquals(ParameterValidationStatus.CANDIDATE_REVIEW_REQUIRED, provenance.validationStatus());
    }

    @Test
    void rejectsUnverifiableChecksum() {
        assertThrows(IllegalArgumentException.class, () -> new ParameterizationProvenance(
                "SAM", "sulfonium", "sam.sdf", 1, "AM1-BCC", "GAFF2",
                "GAFF2", "GAFF2", ParameterValidationStatus.VALIDATED, "not-a-checksum"));
    }
}
