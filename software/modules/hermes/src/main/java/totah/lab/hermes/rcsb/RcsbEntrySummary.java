package totah.lab.hermes.rcsb;

import java.util.List;
import java.util.Objects;

/**
 * Structure-quality summary of one RCSB PDB entry: experimental method,
 * resolution, bound non-polymer components (ligands, ions, cofactors),
 * chain and biological assembly counts.
 */
public record RcsbEntrySummary(
        String pdbId,
        String title,
        String experimentalMethod,
        List<Double> resolutions,
        List<String> ligandComponentIds,
        int polymerEntityCount,
        int chainCount,
        int assemblyCount
) {
    public RcsbEntrySummary {
        Objects.requireNonNull(pdbId, "pdbId");
        resolutions = resolutions == null ? List.of() : List.copyOf(resolutions);
        ligandComponentIds = ligandComponentIds == null
                ? List.of() : List.copyOf(ligandComponentIds);
    }
}
