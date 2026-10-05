package totah.lab.athena.system.rules;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Structural relation witnesses, not claims that an arbitrary spiro graph is aromatic. */
class AromaticSystemRelationTest {
    @Test void sharedAtomAloneDoesNotFuseCycles() {
        assertEquals(Optional.of("SHARED_ATOM_ONLY"), AromaticSystemRules.relationKind(Set.of("junction"), Set.of()));
    }
    @Test void sharedBondRequiresTwoEndpoints() {
        assertEquals(Optional.of("SHARED_BOND"), AromaticSystemRules.relationKind(Set.of("a", "b"), Set.of("ab")));
        assertThrows(IllegalArgumentException.class, () -> AromaticSystemRules.relationKind(Set.of("a"), Set.of("ab")));
    }
    @Test void DisjointCyclesHaveNoRelation() {
        assertEquals(Optional.empty(), AromaticSystemRules.relationKind(Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> AromaticSystemRules.relationKind(Set.of(), Set.of("ab")));
    }
}
