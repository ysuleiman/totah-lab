package totah.lab.prometheus.identity;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hook for molecule-specific parameters: several canonical atoms may share one
 * generic GAFF type yet stay distinguishable by canonical index.
 */
class ForceFieldAtomMapTest {

    @Test
    void sharedGenericTypeGroupsSeveralCanonicalAtoms() {
        ForceFieldAtomMap ff = TslFixtures.forceFieldMapGaff2();

        assertThat(ff.forceFieldFamily()).isEqualTo("GAFF2");
        assertThat(ff.typeOf(9)).isEqualTo("c6");
        assertThat(ff.typeOf(10)).isEqualTo("c6");
        assertThat(ff.typeOf(11)).isEqualTo("c6");
        assertThat(ff.typeOf(26)).isEqualTo("sh");
        assertThat(ff.typeOf(56)).isEqualTo("hs");

        // one generic GAFF type maps to multiple canonical atoms that can later
        // receive distinct molecule-specific parameters
        Map<String, List<Integer>> byType = ff.atomsByType();
        assertThat(byType.get("c6")).containsExactly(9, 10, 11);
        assertThat(byType.get("sh")).containsExactly(26);
        assertThat(byType.get("hs")).containsExactly(56);
    }

    @Test
    void missingTypeForAnAtomIsRejected() {
        Map<Integer, String> incomplete = new HashMap<>();
        incomplete.put(9, "c6");
        incomplete.put(10, "c6");
        incomplete.put(11, "c6");
        incomplete.put(26, "sh");
        // H56 (serial 56) missing

        assertThatThrownBy(() -> new ForceFieldAtomMap(
                TslFixtures.canonicalMap(), "GAFF2", incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing force-field type");
    }

    @Test
    void typeForUnknownCanonicalIndexIsRejected() {
        Map<Integer, String> extra = new HashMap<>(Map.of(
                9, "c6", 10, "c6", 11, "c6", 26, "sh", 56, "hs"));
        extra.put(99, "c3");

        assertThatThrownBy(() -> new ForceFieldAtomMap(
                TslFixtures.canonicalMap(), "GAFF2", extra))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown canonical index");
    }
}
