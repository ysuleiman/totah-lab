package totah.lab.prometheus.numerics;

import java.util.Objects;

/** Hash of every input that determines a numerical intermediate's validity. */
public record NumericalStateIdentity(String sha256) {
    public NumericalStateIdentity {
        Objects.requireNonNull(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("state identity must be lowercase SHA-256");
        }
    }
}
