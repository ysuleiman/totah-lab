package totah.lab.athena.design.grammar;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutableScaffoldGrammarValidatorTest {
    @Test void protectedBondRequiresMatchingOrderAromaticityAndStereo() {
        assertThat(validate(bond("DOUBLE", false, "E")).valid()).isTrue();
        assertThat(validate(bond("SINGLE", false, "E")).valid()).isFalse();
        assertThat(validate(bond("DOUBLE", true, "E")).valid()).isFalse();
        assertThat(validate(bond("DOUBLE", false, "Z")).valid()).isFalse();
    }

    private static ExecutableScaffoldGrammarValidator.Validation validate(
            ExecutableScaffoldGrammar.IndexedBond protectedBond) {
        var parentBond = bond("DOUBLE", false, "E");
        var graph = new ExecutableScaffoldGrammar.IndexedGraph(List.of(
                new ExecutableScaffoldGrammar.IndexedAtom("a", 0, "C", false, 0, "NONE"),
                new ExecutableScaffoldGrammar.IndexedAtom("b", 1, "C", false, 0, "NONE")),
                List.of(parentBond));
        var grammar = new ExecutableScaffoldGrammar("id", "1", "parent", "hash", graph,
                Set.of(), List.of(protectedBond), List.of(), List.of(), Map.of());
        return new ExecutableScaffoldGrammarValidator().validate(grammar);
    }

    private static ExecutableScaffoldGrammar.IndexedBond bond(
            String order, boolean aromatic, String stereo) {
        return new ExecutableScaffoldGrammar.IndexedBond("a", "b", order, aromatic, stereo);
    }
}
