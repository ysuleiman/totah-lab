package totah.lab.gaia.classification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidueCategoriesTest {

    @Test
    void recognizesOnlyCanonicalStandardAminoAcidNames() {
        assertTrue(ResidueCategories.isStandardAminoAcid(" ala "));
        assertFalse(ResidueCategories.isStandardAminoAcid("MSE"));
        assertFalse(ResidueCategories.isStandardAminoAcid("HOH"));
    }

    @Test
    void phenylalanineIsHydrophobicAndAromatic() {
        assertEquals(
                java.util.Set.of(
                        ResidueCategory.HYDROPHOBIC,
                        ResidueCategory.AROMATIC),
                ResidueCategories.classify("PHE"));
    }

    @Test
    void cysteineIsPolarAndCysteine() {
        assertEquals(
                java.util.Set.of(
                        ResidueCategory.POLAR,
                        ResidueCategory.CYSTEINE),
                ResidueCategories.classify("CYS"));
    }

    @Test
    void glycineIsOnlyGlycine() {
        assertEquals(
                java.util.Set.of(ResidueCategory.GLYCINE),
                ResidueCategories.classify("GLY"));
    }

    @Test
    void histidineIsPositiveAndAromatic() {
        assertEquals(
                java.util.Set.of(
                        ResidueCategory.POSITIVELY_CHARGED,
                        ResidueCategory.AROMATIC),
                ResidueCategories.classify("HIS"));
    }

    @Test
    void classificationIsCaseInsensitive() {
        assertEquals(
                ResidueCategories.classify("PHE"),
                ResidueCategories.classify(" phe "));
    }

    @Test
    void unknownResidueIsOther() {
        assertEquals(
                java.util.Set.of(ResidueCategory.OTHER),
                ResidueCategories.classify("MSE"));
    }

    @Test
    void resultIsImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> ResidueCategories.classify("ALA")
                        .add(ResidueCategory.OTHER));
    }
}
