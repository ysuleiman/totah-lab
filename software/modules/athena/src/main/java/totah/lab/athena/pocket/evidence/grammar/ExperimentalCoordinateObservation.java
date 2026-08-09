package totah.lab.athena.pocket.evidence.grammar;

import java.util.Map;
import java.util.Objects;

/** Coordinates of mapped UniProt positions in one experimental polymer chain. */
public record ExperimentalCoordinateObservation(
        String observationId,
        Map<Integer, ExperimentalResidueCoordinate> residues) {
    public ExperimentalCoordinateObservation {
        Objects.requireNonNull(observationId);
        residues = Map.copyOf(residues);
    }
}
