package totah.lab.prometheus.strategy;

import java.util.Objects;

/** Software capability needed to execute a method; it is not a scientific-validity verdict. */
public record ExternalDependencyDescriptor(
        String capability,
        String role,
        boolean required,
        String interchangeableImplementations) {

    public ExternalDependencyDescriptor {
        capability = requireNonBlank(capability, "capability");
        role = requireNonBlank(role, "role");
        interchangeableImplementations =
                requireNonBlank(interchangeableImplementations, "interchangeableImplementations");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
