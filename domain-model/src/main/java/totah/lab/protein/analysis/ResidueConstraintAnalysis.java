package totah.lab.protein.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ResidueConstraintAnalysis(
        String provider,
        String model,
        String sequence,
        Instant generatedAt,
        List<ResidueConstraintEvidence> residues
) {

    public ResidueConstraintAnalysis {
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        sequence = requireText(sequence, "sequence");
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        residues = List.copyOf(
                Objects.requireNonNull(residues, "residues")
        );
        if (residues.size() != sequence.length()) {
            throw new IllegalArgumentException(
                    "residue count must match sequence length"
            );
        }
        for (int index = 0; index < residues.size(); index++) {
            ResidueConstraintEvidence residue = residues.get(index);
            int expectedPosition = index + 1;
            if (residue.position() != expectedPosition) {
                throw new IllegalArgumentException(
                        "residues must be ordered by contiguous position"
                );
            }
            if (residue.wildType() != sequence.charAt(index)) {
                throw new IllegalArgumentException(
                        "residue wild type does not match sequence at position "
                                + expectedPosition
                );
            }
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
