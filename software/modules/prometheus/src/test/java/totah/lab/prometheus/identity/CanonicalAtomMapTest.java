package totah.lab.prometheus.identity;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

class CanonicalAtomMapTest {

    @Test
    void duplicateCanonicalIndexIsRejected() {
        assertThatThrownBy(() -> new CanonicalAtomMap(TslFixtures.TSL, List.of(
                new CanonicalAtomId(9, "C8", "C"),
                new CanonicalAtomId(9, "C9", "C"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate canonicalIndex");
    }

    @Test
    void duplicateLabelIsRejected() {
        assertThatThrownBy(() -> new CanonicalAtomMap(TslFixtures.TSL, List.of(
                new CanonicalAtomId(9, "C8", "C"),
                new CanonicalAtomId(10, "C8", "C"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate label");
    }

    @Test
    void lookupByIndexAndLabelWorks() {
        CanonicalAtomMap map = TslFixtures.canonicalMap();

        assertThat(map.size()).isEqualTo(5);
        assertThat(map.molecule()).isEqualTo(TslFixtures.TSL);

        assertThat(map.byIndex(10)).isPresent();
        assertThat(map.byIndex(10).orElseThrow().label()).isEqualTo("C9");
        assertThat(map.byLabel("S26")).isPresent();
        assertThat(map.byLabel("S26").orElseThrow().canonicalIndex()).isEqualTo(26);

        assertThat(map.byIndex(1)).isEmpty();
        assertThat(map.byLabel("Cl1")).isEmpty();
    }

    @Test
    void atomsAreInCanonicalOrderAndHashIsStable() {
        CanonicalAtomMap map = TslFixtures.canonicalMap();

        assertThat(map.atoms())
                .extracting(CanonicalAtomId::canonicalIndex)
                .containsExactly(9, 10, 11, 26, 56);

        String hash = map.canonicalHash();
        assertThat(hash).hasSize(64);
        assertThat(TslFixtures.canonicalMap().canonicalHash()).isEqualTo(hash);
    }
}
