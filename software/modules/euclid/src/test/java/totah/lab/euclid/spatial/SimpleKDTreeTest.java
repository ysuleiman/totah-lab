package totah.lab.euclid.spatial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleKDTreeTest {

    @Test
    void findsValuesWithinRadius() {
        SimpleKDTree<String> tree = new SimpleKDTree<>(2);
        tree.build(
                List.of(
                        new double[]{0.0, 0.0},
                        new double[]{1.0, 1.0},
                        new double[]{5.0, 5.0}),
                List.of("origin", "near", "far"));

        List<String> values = tree.rangeSearch(
                        new double[]{0.0, 0.0},
                        1.5)
                .stream()
                .map(SimpleKDTree.Result::value)
                .sorted()
                .toList();

        assertEquals(List.of("near", "origin"), values);
    }
}
