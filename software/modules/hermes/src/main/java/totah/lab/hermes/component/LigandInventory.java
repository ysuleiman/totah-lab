package totah.lab.hermes.component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Top-level inventory of every non-polymer component found in a set of
 * structure files, plus summary statistics.
 */
public record LigandInventory(
        Map<String, ComponentInventory> components,
        int totalOccurrences,
        Map<LigandClassification, Integer> countsByClassification
) {
    public LigandInventory {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(countsByClassification, "countsByClassification");
        components = Collections.unmodifiableMap(new TreeMap<>(components));
        EnumMap<LigandClassification, Integer> counts =
                new EnumMap<>(LigandClassification.class);
        counts.putAll(countsByClassification);
        countsByClassification = Collections.unmodifiableMap(counts);
    }

    /** Number of distinct component IDs in this inventory. */
    public int totalComponents() {
        return components.size();
    }
}
