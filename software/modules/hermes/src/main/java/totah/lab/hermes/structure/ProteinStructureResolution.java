package totah.lab.hermes.structure;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete structure-resolution result for one protein.
 */
public record ProteinStructureResolution(
        String requestedAccession,
        String canonicalAccession,
        List<ResolvedProteinStructure> structures
) {

    public ProteinStructureResolution {
        Objects.requireNonNull(requestedAccession, "requestedAccession");
        Objects.requireNonNull(canonicalAccession, "canonicalAccession");

        structures = List.copyOf(
                Objects.requireNonNull(structures, "structures")
        );
    }

    /**
     * Returns the preferred structure. The resolver places preferred structures first.
     */
    public Optional<ResolvedProteinStructure> preferred() {
        return structures.stream().findFirst();
    }

    public List<ResolvedProteinStructure> experimentalStructures() {
        return structures.stream()
                .filter(ResolvedProteinStructure::experimental)
                .toList();
    }

    public List<ResolvedProteinStructure> predictedStructures() {
        return structures.stream()
                .filter(ResolvedProteinStructure::predicted)
                .toList();
    }

    public boolean hasStructure() {
        return !structures.isEmpty();
    }

    public boolean hasExperimentalStructure() {
        return structures.stream()
                .anyMatch(ResolvedProteinStructure::experimental);
    }

    public boolean hasPredictedStructure() {
        return structures.stream()
                .anyMatch(ResolvedProteinStructure::predicted);
    }
}