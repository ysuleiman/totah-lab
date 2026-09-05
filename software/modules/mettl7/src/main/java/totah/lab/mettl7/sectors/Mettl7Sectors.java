package totah.lab.mettl7.sectors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Frozen METTL7A sector definitions. Pure configuration data consumed by
 * campaign code; contains no geometry or analysis logic.
 *
 * <p>Residue numbers use the METTL7A numbering scheme. Mapping these sectors
 * onto METTL7B homolog positions is explicitly out of scope for this class.
 * Generic athena geometry/interaction code must never hardcode these numbers;
 * they flow into generic code as configuration from here.
 */
public record Mettl7Sectors(Map<String, Set<Integer>> sectors) {
    public Mettl7Sectors {
        var copy = new LinkedHashMap<String, Set<Integer>>();
        sectors.forEach((name, residues) -> copy.put(name, Set.copyOf(residues)));
        sectors = Map.copyOf(copy);
    }

    /** Returns the residues of a named sector. */
    public Set<Integer> sector(String name) {
        var residues = sectors.get(name);
        if (residues == null) throw new IllegalArgumentException("unknown sector: " + name);
        return residues;
    }

    /** Returns whether the residue (METTL7A numbering) belongs to the named sector. */
    public boolean contains(String sectorName, int residueNumber) {
        return sector(sectorName).contains(residueNumber);
    }

    /** The frozen METTL7A sectors: 39-47, 144-175, 195-207, 228-237 (inclusive). */
    public static Mettl7Sectors mettl7aDefaults() {
        return new Mettl7Sectors(Map.of(
                "39-47", range(39, 47),
                "144-175", range(144, 175),
                "195-207", range(195, 207),
                "228-237", range(228, 237)));
    }

    private static Set<Integer> range(int firstInclusive, int lastInclusive) {
        return IntStream.rangeClosed(firstInclusive, lastInclusive)
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }
}
