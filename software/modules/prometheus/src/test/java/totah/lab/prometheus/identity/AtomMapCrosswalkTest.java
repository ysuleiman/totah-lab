package totah.lab.prometheus.identity;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

/**
 * Regression test for the TSL S26/C9/C10 mapping bug: the number inside a label
 * is NOT the canonical serial. C9 is serial 10, C10 is serial 11.
 */
class AtomMapCrosswalkTest {

    @Test
    void fileOrderCrosswalkResolvesCanonicalSerialsCorrectly() {
        // file order: [H56, C10, C9, S26, C8] at positions 0..4
        EvidenceAtomMap evidenceMap = TslFixtures.evidenceMapReordered();

        // position -> canonical index
        assertThat(evidenceMap.canonicalIndexAt(0)).isEqualTo(56); // H56
        assertThat(evidenceMap.canonicalIndexAt(1)).isEqualTo(11); // C10
        assertThat(evidenceMap.canonicalIndexAt(2)).isEqualTo(10); // C9
        assertThat(evidenceMap.canonicalIndexAt(3)).isEqualTo(26); // S26
        assertThat(evidenceMap.canonicalIndexAt(4)).isEqualTo(9);  // C8

        // canonical index -> position (reverse direction)
        assertThat(evidenceMap.filePositionOf(10)).isEqualTo(2);   // C9
        assertThat(evidenceMap.filePositionOf(11)).isEqualTo(1);   // C10
        assertThat(evidenceMap.filePositionOf(26)).isEqualTo(3);   // S26
        assertThat(evidenceMap.filePositionOf(9)).isEqualTo(4);    // C8
        assertThat(evidenceMap.filePositionOf(56)).isEqualTo(0);   // H56

        // C9 (serial 10) and C10 (serial 11) must never be confused
        assertThat(evidenceMap.canonical().byLabel("C9").orElseThrow().canonicalIndex()).isEqualTo(10);
        assertThat(evidenceMap.canonical().byLabel("C10").orElseThrow().canonicalIndex()).isEqualTo(11);
    }

    @Test
    void crosswalkResolvesForceFieldTypeAtFilePosition() {
        AtomMapCrosswalk crosswalk = new AtomMapCrosswalk(
                TslFixtures.evidenceMapReordered(), TslFixtures.forceFieldMapGaff2());

        // position 2 holds C9 (serial 10) -> "c6"; position 1 holds C10 (serial 11) -> "c6"
        assertThat(crosswalk.forceFieldTypeAt(2)).isEqualTo("c6");
        assertThat(crosswalk.forceFieldTypeAt(1)).isEqualTo("c6");
        assertThat(crosswalk.forceFieldTypeAt(0)).isEqualTo("hs"); // H56
        assertThat(crosswalk.forceFieldTypeAt(3)).isEqualTo("sh"); // S26
        assertThat(crosswalk.forceFieldTypeAt(4)).isEqualTo("c6"); // C8
    }

    @Test
    void mismatchedCanonicalMapsAreRejected() {
        CanonicalAtomMap other = new CanonicalAtomMap(
                new MoleculeIdentity("OTHER", "other molecule", "CH4"),
                List.of(new CanonicalAtomId(1, "C1", "C")));
        EvidenceAtomMap evidenceMap = new EvidenceAtomMap(other, List.of(1));

        assertThatThrownBy(() -> new AtomMapCrosswalk(evidenceMap, TslFixtures.forceFieldMapGaff2()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different canonical atom maps");
    }

    @Test
    void nonPermutationEvidenceOrderIsRejected() {
        assertThatThrownBy(() -> new EvidenceAtomMap(
                TslFixtures.canonicalMap(), List.of(56, 11, 10, 26, 26)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permutation");
    }
}
