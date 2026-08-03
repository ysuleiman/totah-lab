package totah.lab.proteus.protein.variant;

import totah.lab.proteus.protein.mutation.AppliedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** How a {@link ProteinVariant} was produced. */
public record VariantProvenance(
        String parentStructureId,
        List<AppliedMutation> appliedMutations,
        String rotamerMethod,
        String softwareVersion,
        Instant timestamp,
        List<String> warnings) {

    public VariantProvenance {
        Objects.requireNonNull(parentStructureId, "parentStructureId");
        appliedMutations = List.copyOf(Objects.requireNonNull(appliedMutations, "appliedMutations"));
        Objects.requireNonNull(rotamerMethod, "rotamerMethod");
        Objects.requireNonNull(softwareVersion, "softwareVersion");
        Objects.requireNonNull(timestamp, "timestamp");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
