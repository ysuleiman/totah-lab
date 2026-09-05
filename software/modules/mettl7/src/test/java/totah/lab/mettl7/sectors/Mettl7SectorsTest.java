package totah.lab.mettl7.sectors;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mettl7SectorsTest {
    private final Mettl7Sectors sectors = Mettl7Sectors.mettl7aDefaults();

    @Test
    void frozenContentsAreExact() {
        assertThat(sectors.sector("39-47")).hasSize(9);
        assertThat(sectors.sector("144-175")).hasSize(32);
        assertThat(sectors.sector("195-207")).hasSize(13);
        assertThat(sectors.sector("228-237")).hasSize(10);
        assertThat(sectors.sectors()).containsOnlyKeys("39-47", "144-175", "195-207", "228-237");
    }

    @Test
    void boundaryResiduesAreIncluded() {
        assertThat(sectors.contains("39-47", 39)).isTrue();
        assertThat(sectors.contains("39-47", 47)).isTrue();
        assertThat(sectors.contains("144-175", 144)).isTrue();
        assertThat(sectors.contains("144-175", 175)).isTrue();
        assertThat(sectors.contains("195-207", 195)).isTrue();
        assertThat(sectors.contains("195-207", 207)).isTrue();
        assertThat(sectors.contains("228-237", 228)).isTrue();
        assertThat(sectors.contains("228-237", 237)).isTrue();
    }

    @Test
    void residuesJustOutsideBoundariesAreExcluded() {
        assertThat(sectors.contains("39-47", 38)).isFalse();
        assertThat(sectors.contains("39-47", 48)).isFalse();
        assertThat(sectors.contains("144-175", 143)).isFalse();
        assertThat(sectors.contains("144-175", 176)).isFalse();
        assertThat(sectors.contains("195-207", 194)).isFalse();
        assertThat(sectors.contains("195-207", 208)).isFalse();
        assertThat(sectors.contains("228-237", 227)).isFalse();
        assertThat(sectors.contains("228-237", 238)).isFalse();
    }

    @Test
    void sectorContentsAreContiguous() {
        assertThat(sectors.sector("39-47")).containsExactlyInAnyOrder(39, 40, 41, 42, 43, 44, 45, 46, 47);
        assertThat(sectors.sector("228-237"))
                .containsExactlyInAnyOrder(228, 229, 230, 231, 232, 233, 234, 235, 236, 237);
    }

    @Test
    void returnedSetsAreImmutable() {
        assertThatThrownBy(() -> sectors.sector("39-47").add(99))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> sectors.sectors().put("99-100", Set.of(99)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorDefensivelyCopiesInput() {
        var residues = new HashSet<Integer>();
        residues.add(39);
        var input = new HashMap<String, Set<Integer>>();
        input.put("39-47", residues);
        var instance = new Mettl7Sectors(input);
        residues.add(40);
        input.put("144-175", Set.of(144));
        assertThat(instance.sector("39-47")).containsExactly(39);
        assertThat(instance.sectors()).containsOnlyKeys("39-47");
    }

    @Test
    void unknownSectorIsRejected() {
        assertThatThrownBy(() -> sectors.sector("1-10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown sector");
        assertThatThrownBy(() -> sectors.contains("1-10", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
