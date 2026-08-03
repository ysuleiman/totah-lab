package totah.lab.hermes.structure;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Normalized reference to an available protein structure.
 */
public record ResolvedProteinStructure(
        String uniProtAccession,
        StructureSource source,
        StructureKind kind,
        String identifier,
        URI coordinateUri,
        StructureFormat format
) {

    public ResolvedProteinStructure {
        uniProtAccession = normalizeRequired(
                uniProtAccession,
                "uniProtAccession"
        ).toUpperCase(Locale.ROOT);

        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(kind, "kind");

        identifier = normalizeRequired(identifier, "identifier");
        Objects.requireNonNull(coordinateUri, "coordinateUri");
        Objects.requireNonNull(format, "format");

        if (!coordinateUri.isAbsolute()) {
            throw new IllegalArgumentException(
                    "coordinateUri must be absolute"
            );
        }

        if (!coordinateUri.getScheme().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException(
                    "coordinateUri must use HTTPS"
            );
        }
    }

    public boolean experimental() {
        return kind == StructureKind.EXPERIMENTAL;
    }

    public boolean predicted() {
        return kind == StructureKind.PREDICTED;
    }

    private static String normalizeRequired(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);

        String normalized = value.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return normalized;
    }
}