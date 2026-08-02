package totah.lab.gaia.molecule;

import totah.lab.gaia.structure.Structure;

import java.util.Objects;
import java.util.Optional;

public final class Protein implements Molecule {

    private final String id;
    private final String uniProtId;
    private final String name;
    private final String gene;
    private final String organism;
    private final String function;
    private final Structure structure;

    public Protein(
            String id,
            String uniProtId,
            String name,
            String gene,
            String organism,
            String function,
            Structure structure) {

        this.id = requireNonBlank(id, "id");
        this.uniProtId = normalizeNullable(uniProtId);
        this.name = requireNonBlank(name, "name");
        this.gene = normalizeNullable(gene);
        this.organism = normalizeNullable(organism);
        this.function = normalizeNullable(function);
        this.structure = Objects.requireNonNull(
                structure,
                "structure");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Structure structure() {
        return structure;
    }

    public Optional<String> uniProtId() {
        return Optional.ofNullable(uniProtId);
    }

    public Optional<String> gene() {
        return Optional.ofNullable(gene);
    }

    public Optional<String> organism() {
        return Optional.ofNullable(organism);
    }

    public Optional<String> function() {
        return Optional.ofNullable(function);
    }

    private static String requireNonBlank(
            String value,
            String fieldName) {

        Objects.requireNonNull(value, fieldName);

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank.");
        }

        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}
