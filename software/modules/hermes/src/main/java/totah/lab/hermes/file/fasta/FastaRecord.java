package totah.lab.hermes.file.fasta;

import java.util.Objects;

/** One FASTA record, independent of any sequence data provider. */
public record FastaRecord(
        String identifier,
        String description,
        String sequence
) {
    public FastaRecord {
        identifier = requireNonBlank(identifier, "identifier");
        description = description == null || description.isBlank()
                ? null
                : description.trim();
        sequence = requireNonBlank(sequence, "sequence");
        if (identifier.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("identifier must not contain whitespace");
        }
        if (sequence.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("sequence must not contain whitespace");
        }
    }

    public String header() {
        return description == null ? identifier : identifier + " " + description;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
