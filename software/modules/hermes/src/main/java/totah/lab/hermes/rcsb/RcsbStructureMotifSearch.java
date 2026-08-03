package totah.lab.hermes.rcsb;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Searches for the geometry formed by reference residues in a PDB entry. */
public record RcsbStructureMotifSearch(
        String referencePdbId,
        List<RcsbResidue> residues,
        double rmsdCutoff
) implements RcsbSearchCriteria {

    public RcsbStructureMotifSearch {
        Objects.requireNonNull(referencePdbId, "referencePdbId");
        referencePdbId = referencePdbId.trim().toUpperCase(Locale.ROOT);
        if (!referencePdbId.matches("[0-9][A-Z0-9]{3}")) {
            throw new IllegalArgumentException("Invalid reference PDB ID: " + referencePdbId);
        }
        residues = List.copyOf(Objects.requireNonNull(residues, "residues"));
        if (residues.size() < 2) {
            throw new IllegalArgumentException("At least two motif residues are required");
        }
        if (!Double.isFinite(rmsdCutoff) || rmsdCutoff < 0.0) {
            throw new IllegalArgumentException("rmsdCutoff must be finite and non-negative");
        }
    }
}
