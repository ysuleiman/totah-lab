package totah.lab.proteus.protein.mutation;

import totah.lab.gaia.structure.Structure;
import totah.lab.proteus.validation.ValidationReport;

import java.util.Objects;
import java.util.Optional;

public record MutationResult(Structure structure,
                             Optional<AppliedMutation> appliedMutation,
                             ValidationReport validation) {
    public MutationResult {
        Objects.requireNonNull(structure, "structure");
        appliedMutation = appliedMutation == null ? Optional.empty() : appliedMutation;
        Objects.requireNonNull(validation, "validation");
    }

    public boolean successful() { return appliedMutation.isPresent() && validation.valid(); }
}
