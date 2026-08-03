package totah.lab.proteus.protein.mutation;

import totah.lab.gaia.structure.ResidueId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An ordered, non-empty set of substitutions against one parent target.
 * Mutations apply in list order; duplicate targets (same {@link ResidueId})
 * are rejected.
 */
public record MutationSet(
        String id,
        String parentTarget,
        List<Mutation> mutations,
        MutationPurpose purpose) {

    public MutationSet {
        id = text(id, "id");
        parentTarget = text(parentTarget, "parentTarget");
        mutations = List.copyOf(Objects.requireNonNull(mutations, "mutations"));
        if (mutations.isEmpty()) {
            throw new IllegalArgumentException("mutations must not be empty");
        }
        Set<ResidueId> targets = new HashSet<>();
        for (Mutation mutation : mutations) {
            if (!targets.add(mutation.target())) {
                throw new IllegalArgumentException(
                        "Duplicate mutation target: " + mutation.target());
            }
        }
        Objects.requireNonNull(purpose, "purpose");
    }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
