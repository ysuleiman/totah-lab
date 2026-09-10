package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable directional protein-surface comparison. */
public record DifferentialSurfaceMap(
        DifferentialSurfaceMode mode,
        DifferentialSurfaceOptions options,
        List<DifferentialResidueScore> residues,
        Map<ResidueId, Map<ResidueId, Double>> queryNeighborhoods) {
    public DifferentialSurfaceMap {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(options, "options");
        residues = List.copyOf(Objects.requireNonNull(residues, "residues"));
        Objects.requireNonNull(queryNeighborhoods, "queryNeighborhoods");
        LinkedHashMap<ResidueId, Map<ResidueId, Double>> ordered =
                new LinkedHashMap<>();
        queryNeighborhoods.forEach((residue, members) -> ordered.put(residue,
                Collections.unmodifiableMap(new LinkedHashMap<>(members))));
        queryNeighborhoods = Collections.unmodifiableMap(ordered);
    }

    public Optional<DifferentialResidueScore> score(ResidueId residue) {
        return residues.stream().filter(row -> row.queryResidue().equals(residue))
                .findFirst();
    }
}
