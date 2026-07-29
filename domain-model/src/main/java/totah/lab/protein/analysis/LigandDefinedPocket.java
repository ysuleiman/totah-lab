package totah.lab.protein.analysis;

import java.util.List;
import java.util.Objects;

public record LigandDefinedPocket(
        String proteinChain,
        String ligandChain,
        String ligandCcd,
        double cutoff,
        List<LigandPocketResidue> residues
) {

    public LigandDefinedPocket {
        proteinChain = requireText(proteinChain, "proteinChain");
        ligandChain = requireText(ligandChain, "ligandChain");
        ligandCcd = requireText(ligandCcd, "ligandCcd");
        if (!Double.isFinite(cutoff) || cutoff <= 0.0) {
            throw new IllegalArgumentException("cutoff must be positive");
        }
        residues = List.copyOf(residues);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
