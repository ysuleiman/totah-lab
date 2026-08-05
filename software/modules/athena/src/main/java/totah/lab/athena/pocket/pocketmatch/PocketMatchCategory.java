package totah.lab.athena.pocket.pocketmatch;

import java.util.Objects;

/**
 * One PocketMatch distance-list category: an unordered pair of residue
 * chemistry groups combined with an unordered pair of representative
 * point types.
 *
 * <p>The compact constructor canonicalizes ordering, so equivalent
 * unordered pairs always map to the same key:
 * {@code (AROMATIC, POSITIVE)} becomes {@code (POSITIVE, AROMATIC)}
 * because POSITIVE has the lower enum ordinal.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public record PocketMatchCategory(
        PocketMatchResidueGroup firstGroup,
        PocketMatchResidueGroup secondGroup,
        PocketMatchPointType firstPointType,
        PocketMatchPointType secondPointType
) {

    public PocketMatchCategory {
        Objects.requireNonNull(firstGroup, "firstGroup");
        Objects.requireNonNull(secondGroup, "secondGroup");
        Objects.requireNonNull(firstPointType, "firstPointType");
        Objects.requireNonNull(secondPointType, "secondPointType");

        if (secondGroup.ordinal() < firstGroup.ordinal()) {
            PocketMatchResidueGroup swap = firstGroup;
            firstGroup = secondGroup;
            secondGroup = swap;
        }

        if (secondPointType.ordinal() < firstPointType.ordinal()) {
            PocketMatchPointType swap = firstPointType;
            firstPointType = secondPointType;
            secondPointType = swap;
        }
    }
}
