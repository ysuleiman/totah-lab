package totah.lab.hermes.file.mmcif;

import java.util.List;
import java.util.Objects;

/** One experimentally observed non-polymer residue in an entry or assembly. */
public record BoundComponentOccurrence(
        String pdbId,
        SourceKind sourceKind,
        String assemblyId,
        int modelNumber,
        String componentId,
        String asymId,
        String sequenceId,
        String authAsymId,
        String authSequenceId,
        String insertionCode,
        List<BoundComponentAtom> atoms
) {
    public BoundComponentOccurrence {
        Objects.requireNonNull(pdbId, "pdbId");
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(asymId, "asymId");
        if (modelNumber < 1) {
            throw new IllegalArgumentException("modelNumber must be positive");
        }
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
    }

    public enum SourceKind {
        ENTRY,
        ASSEMBLY
    }
}
