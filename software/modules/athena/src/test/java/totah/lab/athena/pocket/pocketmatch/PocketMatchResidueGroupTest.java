package totah.lab.athena.pocket.pocketmatch;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PocketMatchResidueGroupTest {

    @Test
    void definesExactlyFiveGroups() {
        assertThat(PocketMatchResidueGroup.values()).hasSize(5);
    }

    @Test
    void mapsEveryGroupedResidueToItsPublishedGroup() {
        assertGroup(PocketMatchResidueGroup.ALIPHATIC_SPECIAL,
                "ALA", "VAL", "ILE", "LEU", "GLY", "PRO");
        assertGroup(PocketMatchResidueGroup.POSITIVE,
                "LYS", "ARG", "HIS");
        assertGroup(PocketMatchResidueGroup.ACIDIC_AMIDE,
                "ASP", "GLU", "GLN", "ASN");
        assertGroup(PocketMatchResidueGroup.AROMATIC,
                "TYR", "PHE", "TRP");
        assertGroup(PocketMatchResidueGroup.CYSTEINE_POLAR,
                "CYS", "SER", "THR");
    }

    @Test
    void groupsHistidineWithThePositiveResidues() {
        assertThat(PocketMatchResidueGroup.classify("HIS"))
                .contains(PocketMatchResidueGroup.POSITIVE);
    }

    @Test
    void classifiesCaseInsensitively() {
        assertThat(PocketMatchResidueGroup.classify("his"))
                .contains(PocketMatchResidueGroup.POSITIVE);
        assertThat(PocketMatchResidueGroup.classify(" gly "))
                .contains(PocketMatchResidueGroup.ALIPHATIC_SPECIAL);
    }

    @Test
    void rejectsUnknownResidueNames() {
        assertThat(PocketMatchResidueGroup.classify("MSE"))
                .isEqualTo(Optional.empty());
        assertThat(PocketMatchResidueGroup.classify("UNK"))
                .isEqualTo(Optional.empty());
        assertThat(PocketMatchResidueGroup.classify(null))
                .isEqualTo(Optional.empty());
        assertThat(PocketMatchResidueGroup.classify("  "))
                .isEqualTo(Optional.empty());
    }

    @Test
    void coversNineteenResiduesAndLeavesMethionineUnclassified() {
        // the published grouping assigns 19 of the 20 standard
        // residues; methionine has no PocketMatch group
        int total = 0;
        for (PocketMatchResidueGroup group :
                PocketMatchResidueGroup.values()) {
            total += group.residueNames().size();
        }
        assertThat(total).isEqualTo(19);
        assertThat(PocketMatchResidueGroup.classify("MET"))
                .isEqualTo(Optional.empty());
    }

    private static void assertGroup(
            PocketMatchResidueGroup group,
            String... residueNames
    ) {
        assertThat(group.residueNames())
                .containsExactlyInAnyOrder(residueNames);
        for (String residueName : residueNames) {
            assertThat(PocketMatchResidueGroup.classify(residueName))
                    .as("classify(%s)", residueName)
                    .contains(group);
        }
    }
}
