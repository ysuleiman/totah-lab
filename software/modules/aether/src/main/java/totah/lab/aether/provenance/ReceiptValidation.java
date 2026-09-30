package totah.lab.aether.provenance;

import java.util.Objects;

final class ReceiptValidation {
    private ReceiptValidation() {}

    static void check(String implementation, String protocol, String basisGeometryHash,
                      String calculationHash, String resultHash, ScientificStatus status,
                      String reason, String receiptHash) {
        Objects.requireNonNull(implementation);
        Objects.requireNonNull(protocol);
        Objects.requireNonNull(status);
        Objects.requireNonNull(reason);
        for (String hash : new String[]{basisGeometryHash, calculationHash, resultHash, receiptHash}) {
            hash(hash);
        }
    }

    static void hash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected SHA-256 hash");
        }
    }
}
