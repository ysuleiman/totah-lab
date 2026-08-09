package totah.lab.athena.pocket.evidence.grammar;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.evidence.EvaluationStatus;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentalSiteGrammarFactoryTest {
    private final ExperimentalSiteGrammarFactory factory =
            new ExperimentalSiteGrammarFactory();

    @Test
    void keepsIdentityBlosumChemistryAndContactDimensionsIndependent() {
        var unavailable = unavailable(1);
        var evidence = factory.derive(10, 20, "D", "E",
                ExperimentalContactRole.DIRECT,
                ExperimentalContactRole.NEAR_SHELL, 3, 1, 0, 2,
                unavailable, unavailable(2));
        assertFalse(evidence.identical());
        assertEquals(0.4, evidence.substitutionSimilarity(), 1e-12);
        assertEquals(ResidueChemistry.NEGATIVE, evidence.queryChemistry());
        assertEquals(ResidueChemistry.NEGATIVE,
                evidence.candidateChemistry());
        assertEquals(MatchType.CONSERVATIVE,
                evidence.chemistryRelationship());
        assertEquals(ExperimentalContactRole.DIRECT,
                evidence.queryContactRole());
        assertEquals(ExperimentalContactRole.NEAR_SHELL,
                evidence.candidateContactRole());
        assertTrue(evidence.hasExperimentalSiteEvidence());
    }

    @Test
    void insufficientStructuralEvidenceCarriesNoInventedValue() {
        var unavailable = unavailable(1);
        assertEquals(EvaluationStatus.NOT_EVALUATED, unavailable.status());
        assertTrue(unavailable.caRmsfAngstroms().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new StructuralVariabilityEvidence(
                        EvaluationStatus.NOT_EVALUATED, 1,
                        OptionalDouble.of(0.2), OptionalDouble.empty(),
                        "test", "1", "insufficient"));
    }

    private static StructuralVariabilityEvidence unavailable(int count) {
        return new StructuralVariabilityEvidence(
                EvaluationStatus.NOT_EVALUATED, count,
                OptionalDouble.empty(), OptionalDouble.empty(),
                "EXPERIMENTAL_CA_SUPERPOSITION", "1",
                "INSUFFICIENT_ALIGNED_COORDINATE_OBSERVATIONS");
    }
}
