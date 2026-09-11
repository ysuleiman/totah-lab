package totah.lab.athena.recognition;

import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;
import java.util.Optional;

/** A typed node backed by an existing ligand feature or environment identity. */
public record RecognitionNode(Kind kind, String id, Optional<ResidueId> residue) {
    public enum Kind { LIGAND_FEATURE, PROTEIN_ENVIRONMENT, SAM_FEATURE }

    public RecognitionNode {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        residue = Objects.requireNonNull(residue, "residue");
        if (kind == Kind.LIGAND_FEATURE && residue.isPresent()) {
            throw new IllegalArgumentException("ligand feature must not carry a protein residue");
        }
        if (kind != Kind.LIGAND_FEATURE && residue.isEmpty()) {
            throw new IllegalArgumentException("environment node requires a residue");
        }
    }

    public static RecognitionNode ligand(String id) {
        return new RecognitionNode(Kind.LIGAND_FEATURE, id, Optional.empty());
    }

    public static RecognitionNode environment(ResidueId residue, boolean sam) {
        Objects.requireNonNull(residue, "residue");
        return new RecognitionNode(sam ? Kind.SAM_FEATURE : Kind.PROTEIN_ENVIRONMENT,
                residue.toString(), Optional.of(residue));
    }
}
