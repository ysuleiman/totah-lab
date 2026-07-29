package totah.lab.protein.analysis;

import java.util.Objects;

public record ComplexAtom(
        String name,
        String element,
        boolean hetero,
        double x,
        double y,
        double z
) {

    public ComplexAtom {
        name = requireText(name, "name");
        element = requireText(element, "element");
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)) {
            throw new IllegalArgumentException(
                    "Atom coordinates must be finite"
            );
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
