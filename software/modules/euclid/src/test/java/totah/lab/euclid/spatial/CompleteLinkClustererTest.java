package totah.lab.euclid.spatial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompleteLinkClustererTest {
    @Test void clustersAtInclusiveBoundaryAndSortsBySize() {
        var matrix = List.of(List.of(0.0, 1.0, 4.0), List.of(1.0, 0.0, 3.0), List.of(4.0, 3.0, 0.0));
        assertThat(new CompleteLinkClusterer().cluster(matrix, 1.0))
                .containsExactly(List.of(0, 1), List.of(2));
    }

    @Test void rejectsNonSquareAndNonFiniteMatrices() {
        assertThatThrownBy(() -> new CompleteLinkClusterer().cluster(List.of(List.of(0.0, 1.0)), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompleteLinkClusterer().cluster(
                List.of(List.of(0.0, Double.NaN), List.of(Double.NaN, 0.0)), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
