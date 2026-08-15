package totah.lab.prometheus.strategy;

import java.util.Objects;

/** A methodology-level requirement, independent of any execution package. */
public record ScientificRequirementDescriptor(
        ScientificEvidenceKind evidenceKind,
        String scientificConstraint,
        boolean exactProtocolMatchRequired,
        boolean derivableFromAuthoritativeArtifact,
        boolean validationOnly,
        boolean optional,
        String purpose) {

    public ScientificRequirementDescriptor {
        Objects.requireNonNull(evidenceKind, "evidenceKind");
        scientificConstraint = requireNonBlank(scientificConstraint, "scientificConstraint");
        purpose = requireNonBlank(purpose, "purpose");
        if (validationOnly && optional) {
            throw new IllegalArgumentException("a validation-only requirement cannot be optional");
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
