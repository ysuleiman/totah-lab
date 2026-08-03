package totah.lab.proteus.protein.mutation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.structure.ResidueId;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MutationSetTest {

    @Test
    void rejectsEmptyMutationList() {
        assertThatThrownBy(() -> new MutationSet("set", "METTL7B",
                List.of(), MutationPurpose.CYSTEINE_MECHANISM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateTargets() {
        Mutation first = new Mutation(new ResidueId("A", 47, null), "SER", "TYR");
        Mutation duplicate = new Mutation(new ResidueId("A", 47, null), "SER", "PHE");

        assertThatThrownBy(() -> new MutationSet("set", "METTL7B",
                List.of(first, duplicate), MutationPurpose.SELECTIVITY_VALIDATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate mutation target");
    }

    @Test
    void rejectsBlankIdAndParentTarget() {
        Mutation mutation = new Mutation(new ResidueId("A", 47, null), "SER", "TYR");

        assertThatThrownBy(() -> new MutationSet(" ", "METTL7B",
                List.of(mutation), MutationPurpose.SELECTIVITY_VALIDATION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MutationSet("set", "",
                List.of(mutation), MutationPurpose.SELECTIVITY_VALIDATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsDistinctTargetsAndCopiesTheList() {
        Mutation first = new Mutation(new ResidueId("A", 47, null), "SER", "TYR");
        Mutation second = new Mutation(new ResidueId("A", 199, 'A'), "GLY", "PHE");
        var mutations = new ArrayList<>(List.of(first, second));

        MutationSet set = new MutationSet("set", "METTL7B",
                mutations, MutationPurpose.METTL7A_CONVERSION);
        mutations.clear();

        assertThat(set.mutations()).containsExactly(first, second);
        assertThat(set.purpose()).isEqualTo(MutationPurpose.METTL7A_CONVERSION);
    }
}
