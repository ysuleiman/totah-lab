package totah.lab.hermes.rcsb;

import java.util.Objects;
import java.util.List;
import java.util.Locale;

/** One reference residue in an RCSB structure-motif query. */
public record RcsbResidue(
        String chainId,
        int sequencePosition,
        List<String> allowedExchanges
) {

    public RcsbResidue(String chainId, int sequencePosition) {
        this(chainId, sequencePosition, List.of());
    }

    public RcsbResidue {
        Objects.requireNonNull(chainId, "chainId");
        if (chainId.isBlank()) {
            throw new IllegalArgumentException("chainId must not be blank");
        }
        if (sequencePosition < 1) {
            throw new IllegalArgumentException("sequencePosition must be positive");
        }
        allowedExchanges = List.copyOf(
                Objects.requireNonNull(allowedExchanges, "allowedExchanges"))
                .stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
        if (allowedExchanges.stream().anyMatch(value -> !value.matches("[A-Z]{3}"))) {
            throw new IllegalArgumentException(
                    "allowedExchanges must contain three-letter residue codes");
        }
    }
}
