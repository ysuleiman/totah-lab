package totah.lab.hermes.rcsb;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable metadata for one RCSB PDB entry. */
public record RcsbEntry(
        String pdbId,
        String title,
        List<String> experimentalMethods,
        List<Double> resolutions,
        String keywords,
        List<String> authors,
        Instant initialReleaseDate,
        int proteinEntityCount,
        int depositedAtomCount
) {
    public RcsbEntry {
        Objects.requireNonNull(pdbId, "pdbId");
        experimentalMethods = experimentalMethods == null
                ? List.of() : List.copyOf(experimentalMethods);
        resolutions = resolutions == null ? List.of() : List.copyOf(resolutions);
        authors = authors == null ? List.of() : List.copyOf(authors);
    }
}
