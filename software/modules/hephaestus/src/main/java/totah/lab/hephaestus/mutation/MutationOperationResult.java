package totah.lab.hephaestus.mutation;

import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.validation.ValidationReport;

import java.util.Objects;
import java.util.Optional;

public record MutationOperationResult(Structure structure,
                                      Optional<AppliedMutation> appliedMutation,
                                      ValidationReport validation) {
    public MutationOperationResult {
        Objects.requireNonNull(structure, "structure");
        appliedMutation = appliedMutation == null ? Optional.empty() : appliedMutation;
        Objects.requireNonNull(validation, "validation");
    }

    public boolean successful() { return appliedMutation.isPresent() && validation.valid(); }
}
