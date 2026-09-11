package totah.lab.athena.design.backend;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Immutable provenance for an Athena-owned graph transaction. */
public record GraphEditReceipt(String editId, String editableVectorId, String transformation,
                               Map<String, String> parentToProductAtomIds,
                               Map<String, String> parentToProductBondIds,
                               Set<String> addedAtomIds, Set<String> deletedAtomIds,
                               Set<String> addedBondIds, Set<String> deletedBondIds,
                               List<String> validations) {
    public GraphEditReceipt {
        parentToProductAtomIds = Map.copyOf(parentToProductAtomIds);
        parentToProductBondIds = Map.copyOf(parentToProductBondIds);
        addedAtomIds = immutableSorted(addedAtomIds);
        deletedAtomIds = immutableSorted(deletedAtomIds);
        addedBondIds = immutableSorted(addedBondIds);
        deletedBondIds = immutableSorted(deletedBondIds);
        validations = List.copyOf(validations);
    }

    private static Set<String> immutableSorted(Set<String> values) {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
    }
}
