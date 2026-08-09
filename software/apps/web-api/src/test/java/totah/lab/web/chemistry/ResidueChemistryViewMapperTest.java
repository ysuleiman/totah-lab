package totah.lab.web.chemistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResidueChemistryViewMapperTest {

    @Test
    void preservesOverlappingGaiaCategoriesAndSelectsDisplayCategory() {
        ResidueChemistryView phenylalanine =
                ResidueChemistryViewMapper.map("PHE");

        assertThat(phenylalanine.categories())
                .containsExactly("AROMATIC", "HYDROPHOBIC");
        assertThat(phenylalanine.primaryCategory()).isEqualTo("AROMATIC");
        assertThat(phenylalanine.primaryLabel()).isEqualTo("Aromatic");
        assertThat(phenylalanine.colorKey()).isEqualTo("AROMATIC");
    }

    @Test
    void keepsHistidineChargeWhileUsingAromaticDisplayPrecedence() {
        ResidueChemistryView histidine =
                ResidueChemistryViewMapper.map("HIS");

        assertThat(histidine.categories())
                .containsExactly("AROMATIC", "POSITIVELY_CHARGED");
        assertThat(histidine.primaryCategory()).isEqualTo("AROMATIC");
    }

    @Test
    void leavesUnknownResidueWithoutAPrimaryDisplayCategory() {
        ResidueChemistryView unknown =
                ResidueChemistryViewMapper.map("UNK");

        assertThat(unknown.categories()).containsExactly("OTHER");
        assertThat(unknown.primaryCategory()).isNull();
        assertThat(unknown.primaryLabel()).isNull();
        assertThat(unknown.colorKey()).isNull();
    }

    @Test
    void representsMissingResidueIdentityWithoutInventingChemistry() {
        ResidueChemistryView missing = ResidueChemistryViewMapper.map(null);

        assertThat(missing.categories()).isEmpty();
        assertThat(missing.primaryCategory()).isNull();
        assertThat(missing.primaryLabel()).isNull();
        assertThat(missing.colorKey()).isNull();
    }
}
