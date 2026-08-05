package totah.lab.athena.pocket.pocketmatch;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PocketMatchCategoriesTest {

    @Test
    void generatesFifteenUnorderedGroupPairs() {
        Set<List<Integer>> pairs = new HashSet<>();
        PocketMatchResidueGroup[] groups =
                PocketMatchResidueGroup.values();

        for (PocketMatchResidueGroup first : groups) {
            for (PocketMatchResidueGroup second : groups) {
                pairs.add(List.of(
                        Math.min(first.ordinal(), second.ordinal()),
                        Math.max(first.ordinal(), second.ordinal())
                ));
            }
        }

        assertThat(pairs).hasSize(PocketMatchCategories.GROUP_PAIR_COUNT);
        assertThat(PocketMatchCategories.GROUP_PAIR_COUNT).isEqualTo(15);
    }

    @Test
    void generatesSixUnorderedPointTypePairs() {
        Set<List<Integer>> pairs = new HashSet<>();
        PocketMatchPointType[] types = PocketMatchPointType.values();

        for (PocketMatchPointType first : types) {
            for (PocketMatchPointType second : types) {
                pairs.add(List.of(
                        Math.min(first.ordinal(), second.ordinal()),
                        Math.max(first.ordinal(), second.ordinal())
                ));
            }
        }

        assertThat(pairs)
                .hasSize(PocketMatchCategories.POINT_TYPE_PAIR_COUNT);
        assertThat(PocketMatchCategories.POINT_TYPE_PAIR_COUNT)
                .isEqualTo(6);
    }

    @Test
    void generatesNinetyTotalCategoriesWithoutGaps() {
        assertThat(PocketMatchCategories.CATEGORY_COUNT).isEqualTo(90);
        assertThat(PocketMatchCategories.all()).hasSize(90);

        Set<Integer> indices = new HashSet<>();
        for (PocketMatchCategory category : PocketMatchCategories.all()) {
            indices.add(PocketMatchCategories.indexOf(category));
        }
        assertThat(indices).hasSize(90);
        for (int index = 0; index < 90; index++) {
            assertThat(indices).contains(index);
            assertThat(PocketMatchCategories.categoryAt(index))
                    .isEqualTo(PocketMatchCategories.all().get(index));
        }
    }

    @Test
    void canonicalizesUnorderedCategoryKeys() {
        PocketMatchCategory forward = new PocketMatchCategory(
                PocketMatchResidueGroup.POSITIVE,
                PocketMatchResidueGroup.AROMATIC,
                PocketMatchPointType.CB,
                PocketMatchPointType.CA
        );

        assertThat(forward.firstGroup())
                .isEqualTo(PocketMatchResidueGroup.POSITIVE);
        assertThat(forward.secondGroup())
                .isEqualTo(PocketMatchResidueGroup.AROMATIC);
        assertThat(forward.firstPointType())
                .isEqualTo(PocketMatchPointType.CA);
        assertThat(forward.secondPointType())
                .isEqualTo(PocketMatchPointType.CB);

        PocketMatchCategory reversed = new PocketMatchCategory(
                PocketMatchResidueGroup.AROMATIC,
                PocketMatchResidueGroup.POSITIVE,
                PocketMatchPointType.CA,
                PocketMatchPointType.CB
        );

        assertThat(forward).isEqualTo(reversed);
        assertThat(PocketMatchCategories.indexOf(forward))
                .isEqualTo(PocketMatchCategories.indexOf(reversed));
    }

    @Test
    void unorderedPairIndexIsOrderIndependentAndDistinct() {
        Set<Integer> seen = new HashSet<>();
        for (int first = 0; first < 5; first++) {
            for (int second = 0; second < 5; second++) {
                int index = PocketMatchCategories
                        .unorderedPairIndex(5, first, second);
                assertThat(index)
                        .isEqualTo(PocketMatchCategories
                                .unorderedPairIndex(5, second, first));
                seen.add(index);
            }
        }
        assertThat(seen).hasSize(15);
    }
}
