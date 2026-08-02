package totah.lab.hermes.biohub.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record MolecularComplexPrediction(
        String provider,
        String model,
        String ligandCcd,
        Instant generatedAt,
        Double ptm,
        Double interfacePtm,
        List<ComplexToken> tokens
) {

    public MolecularComplexPrediction {
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        ligandCcd = requireText(ligandCcd, "ligandCcd");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        requireUnitInterval(ptm, "ptm");
        requireUnitInterval(interfacePtm, "interfacePtm");
        tokens = List.copyOf(tokens);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("tokens must not be empty");
        }
    }

    private static void requireUnitInterval(Double value, String name) {
        if (value != null
                && (!Double.isFinite(value) || value < 0.0 || value > 1.0)) {
            throw new IllegalArgumentException(
                    name + " must be between zero and one"
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
