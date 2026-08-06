package totah.lab.athena.pocket.compare.residue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResidueMatchTypeOfTest {

    @Test
    void identicalNamesAreIdentical() {
        assertThat(ResidueCorrespondenceCalculator
                .matchTypeOf("ASP", "ASP"))
                .isEqualTo(MatchType.IDENTICAL);
        assertThat(ResidueCorrespondenceCalculator
                .matchTypeOf("gly", "GLY"))
                .isEqualTo(MatchType.IDENTICAL);
    }

    @Test
    void conservativeSetsAreConservative() {
        assertThat(ResidueCorrespondenceCalculator
                .matchTypeOf("LEU", "MET"))
                .isEqualTo(MatchType.CONSERVATIVE);
        assertThat(ResidueCorrespondenceCalculator
                .matchTypeOf("ASP", "GLU"))
                .isEqualTo(MatchType.CONSERVATIVE);
    }

    @Test
    void sameBroadChemistryIsChemistryCompatible() {
        // ALA and PRO are both hydrophobic but share no
        // conservative set
        assertThat(ResidueCorrespondenceCalculator
                .matchTypeOf("ALA", "PRO"))
                .isEqualTo(MatchType.CHEMISTRY_COMPATIBLE);
    }

    @Test
    void cysteineAndGlycineAreNeverCompatibleWithOthers() {
        assertThat(ResidueCorrespondenceCalculator
                .matchTypeOf("CYS", "SER"))
                .isEqualTo(MatchType.DIFFERENT);
        assertThat(ResidueCorrespondenceCalculator
                .matchTypeOf("GLY", "ALA"))
                .isEqualTo(MatchType.DIFFERENT);
    }

    @Test
    void unrelatedChemistriesAreDifferent() {
        assertThat(ResidueCorrespondenceCalculator
                .matchTypeOf("ASP", "LYS"))
                .isEqualTo(MatchType.DIFFERENT);
    }
}
