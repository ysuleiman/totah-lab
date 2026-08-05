package totah.lab.athena.pocket.pocketmatch;

import java.util.List;
import java.util.Objects;

/**
 * Fixed indexing scheme for the ninety PocketMatch distance-list
 * categories: fifteen unordered chemistry-group pairs crossed with six
 * unordered representative-point-type pairs.
 *
 * <p>Signatures store their distance lists in a flat
 * {@code double[CATEGORY_COUNT][]} indexed by {@link #indexOf}; this
 * avoids hash-map lookups in the comparison hot loop.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public final class PocketMatchCategories {

    public static final int GROUP_PAIR_COUNT = 15;
    public static final int POINT_TYPE_PAIR_COUNT = 6;
    public static final int CATEGORY_COUNT =
            GROUP_PAIR_COUNT * POINT_TYPE_PAIR_COUNT;

    private static final PocketMatchResidueGroup[] GROUPS =
            PocketMatchResidueGroup.values();

    private static final PocketMatchPointType[] POINT_TYPES =
            PocketMatchPointType.values();

    private static final PocketMatchCategory[] BY_INDEX = buildIndex();

    private PocketMatchCategories() {
    }

    /**
     * Returns all ninety canonical categories in index order.
     */
    public static List<PocketMatchCategory> all() {
        return List.of(BY_INDEX);
    }

    /**
     * Returns the stable flat index of a category. The category is
     * canonicalized first, so equivalent unordered pairs map to the
     * same index.
     */
    public static int indexOf(PocketMatchCategory category) {
        Objects.requireNonNull(category, "category");
        return categoryIndex(
                category.firstGroup(),
                category.secondGroup(),
                category.firstPointType(),
                category.secondPointType()
        );
    }

    /**
     * Returns the canonical category stored at a flat index.
     */
    public static PocketMatchCategory categoryAt(int index) {
        if (index < 0 || index >= CATEGORY_COUNT) {
            throw new IllegalArgumentException(
                    "index must be within [0, " + CATEGORY_COUNT
                            + "), but was " + index
            );
        }
        return BY_INDEX[index];
    }

    static int categoryIndex(
            PocketMatchResidueGroup firstGroup,
            PocketMatchResidueGroup secondGroup,
            PocketMatchPointType firstPointType,
            PocketMatchPointType secondPointType
    ) {
        int groupPair = unorderedPairIndex(
                GROUPS.length,
                firstGroup.ordinal(),
                secondGroup.ordinal()
        );
        int pointTypePair = unorderedPairIndex(
                POINT_TYPES.length,
                firstPointType.ordinal(),
                secondPointType.ordinal()
        );
        return groupPair * POINT_TYPE_PAIR_COUNT + pointTypePair;
    }

    /**
     * Flat index of an unordered pair (including the identical pair)
     * drawn from {@code elementCount} elements. Order-independent.
     */
    static int unorderedPairIndex(
            int elementCount,
            int firstOrdinal,
            int secondOrdinal
    ) {
        int low = Math.min(firstOrdinal, secondOrdinal);
        int high = Math.max(firstOrdinal, secondOrdinal);
        return low * elementCount - low * (low - 1) / 2 + (high - low);
    }

    private static PocketMatchCategory[] buildIndex() {
        PocketMatchCategory[] index = new PocketMatchCategory[CATEGORY_COUNT];
        for (PocketMatchResidueGroup firstGroup : GROUPS) {
            for (PocketMatchResidueGroup secondGroup : GROUPS) {
                if (secondGroup.ordinal() < firstGroup.ordinal()) {
                    continue;
                }
                for (PocketMatchPointType firstType : POINT_TYPES) {
                    for (PocketMatchPointType secondType : POINT_TYPES) {
                        if (secondType.ordinal() < firstType.ordinal()) {
                            continue;
                        }
                        PocketMatchCategory category = new PocketMatchCategory(
                                firstGroup,
                                secondGroup,
                                firstType,
                                secondType
                        );
                        index[indexOf(category)] = category;
                    }
                }
            }
        }
        for (int position = 0; position < index.length; position++) {
            if (index[position] == null) {
                throw new IllegalStateException(
                        "Category index gap at " + position
                );
            }
        }
        return index;
    }
}
