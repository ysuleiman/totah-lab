package totah.lab.hermes.rcsb;

import java.util.Objects;
import java.util.Locale;
import java.util.Optional;

/** Identifier and relevance score returned by an RCSB search. */
public record RcsbSearchHit(String identifier, double score) {

    public RcsbSearchHit {
        Objects.requireNonNull(identifier, "identifier");
    }

    /** Returns the owning four-character PDB ID when this is an experimental PDB hit. */
    public Optional<String> pdbId() {
        if (identifier.length() < 4) {
            return Optional.empty();
        }
        String candidate = identifier.substring(0, 4).toUpperCase(Locale.ROOT);
        return candidate.matches("[0-9][A-Z0-9]{3}")
                ? Optional.of(candidate) : Optional.empty();
    }
}
