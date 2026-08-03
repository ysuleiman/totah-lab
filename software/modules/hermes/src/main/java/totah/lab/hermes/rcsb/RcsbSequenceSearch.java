package totah.lab.hermes.rcsb;

import java.util.Locale;
import java.util.Objects;

/** Configurable protein-sequence similarity search. */
public record RcsbSequenceSearch(
        String sequence,
        double identityCutoff,
        double eValueCutoff
) implements RcsbSearchCriteria {

    public RcsbSequenceSearch {
        Objects.requireNonNull(sequence, "sequence");
        sequence = sequence.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (sequence.isEmpty() || !sequence.matches("[A-Z]+")) {
            throw new IllegalArgumentException("sequence must contain amino-acid letters only");
        }
        if (!Double.isFinite(identityCutoff)
                || identityCutoff < 0.0 || identityCutoff > 1.0) {
            throw new IllegalArgumentException("identityCutoff must be between 0 and 1");
        }
        if (!Double.isFinite(eValueCutoff) || eValueCutoff <= 0.0) {
            throw new IllegalArgumentException("eValueCutoff must be positive");
        }
    }
}
