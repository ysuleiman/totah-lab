package totah.lab.ligand;

import org.junit.jupiter.api.Test;
import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.ResidueDisposition;
import totah.lab.pipeline.cleanup.ResidueRole;
import totah.lab.protein.Residue;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandSelectionPolicyTest {

    @Test
    void separatesEligibleLigandsFromDefaultOperationalExclusions() {
        LigandSelectionPolicy policy = new LigandSelectionPolicy();

        assertTrue(policy.evaluate(classified("QWE", ResidueRole.LIGAND)).eligible());
        assertEquals(
                LigandSelectionFailure.EXCLUDED_BY_POLICY,
                policy.evaluate(classified("GOL", ResidueRole.LIGAND)).failure());
        assertEquals(
                LigandSelectionFailure.EXCLUDED_BY_POLICY,
                policy.evaluate(classified("SO4", ResidueRole.LIGAND)).failure());
        assertEquals(
                LigandSelectionFailure.UNSUPPORTED_CLASSIFICATION,
                policy.evaluate(classified("UNK", ResidueRole.UNKNOWN)).failure());
    }

    @Test
    void allowsCallersToSupplyAnExplicitExclusionSet() {
        LigandSelectionPolicy policy = new LigandSelectionPolicy(Set.of("SO4"));

        assertTrue(policy.evaluate(classified("GOL", ResidueRole.LIGAND)).eligible());
        assertEquals(Set.of("SO4"), policy.excludedComponents());
    }

    private ClassifiedResidue classified(String name, ResidueRole role) {
        Residue residue = Residue.builder()
                .name(name)
                .chain("A")
                .number(1)
                .insertionCode(' ')
                .atoms(List.of())
                .build();
        return new ClassifiedResidue(
                residue,
                role,
                ResidueDisposition.EXTRACT_AS_LIGAND,
                "test fixture");
    }
}
