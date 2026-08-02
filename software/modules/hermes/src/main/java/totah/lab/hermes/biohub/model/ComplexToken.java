package totah.lab.hermes.biohub.model;

import java.util.List;
import java.util.Objects;

public record ComplexToken(
        int index,
        String chain,
        int chainPosition,
        String residueName,
        double confidence,
        List<AtomComplex> atoms
) {

    public ComplexToken {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        chain = requireText(chain, "chain");
        if (chainPosition < 1) {
            throw new IllegalArgumentException(
                    "chainPosition must be positive"
            );
        }
        residueName = requireText(residueName, "residueName");
        if (!Double.isFinite(confidence)) {
            throw new IllegalArgumentException(
                    "confidence must be finite"
            );
        }
        atoms = List.copyOf(atoms);
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("atoms must not be empty");
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
