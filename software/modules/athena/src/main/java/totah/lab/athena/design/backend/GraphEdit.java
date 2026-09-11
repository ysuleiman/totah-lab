package totah.lab.athena.design.backend;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One grammar-authorized graph edit. It contains no target-specific knowledge. */
public record GraphEdit(String editId, String editableVectorId, Type type,
                        Set<String> affectedAtomIds, Set<String> affectedBondIds,
                        String anchorAtomId, MolecularGraph fragment,
                        String replacementElement, MolecularGraph.BondOrder replacementBondOrder,
                        Map<String, String> parameters) {
    public GraphEdit {
        require(editId, "editId");
        require(editableVectorId, "editableVectorId");
        Objects.requireNonNull(type, "type");
        affectedAtomIds = Set.copyOf(affectedAtomIds == null ? Set.of() : affectedAtomIds);
        affectedBondIds = Set.copyOf(affectedBondIds == null ? Set.of() : affectedBondIds);
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
    }

    public enum Type {
        ATOM_SUBSTITUTION,
        SUBSTITUENT_REPLACEMENT,
        FRAGMENT_ATTACHMENT,
        SUBSTITUENT_GROWTH,
        SUBSTITUENT_PRUNING,
        AUTHORIZED_BOND_MODIFICATION
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field);
    }
}
