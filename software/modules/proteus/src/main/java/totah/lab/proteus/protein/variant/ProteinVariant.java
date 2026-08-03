package totah.lab.proteus.protein.variant;

import totah.lab.gaia.structure.Structure;
import totah.lab.proteus.protein.mutation.MutationSet;

import java.util.Objects;

/** One mutated structure derived from a parent, with full provenance. */
public record ProteinVariant(
        String id,
        String parentStructureId,
        MutationSet mutationSet,
        Structure structure,
        VariantProvenance provenance) {

    public ProteinVariant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parentStructureId, "parentStructureId");
        Objects.requireNonNull(mutationSet, "mutationSet");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(provenance, "provenance");
    }
}
