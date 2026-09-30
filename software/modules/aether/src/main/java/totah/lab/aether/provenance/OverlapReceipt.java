package totah.lab.aether.provenance;

/** Hashes identify an overlap calculation, not an electronic system or an energy. */
public record OverlapReceipt(String implementation, String protocol, String basisGeometryHash,
                             String calculationHash, String resultHash, ScientificStatus status,
                             String reason, String receiptHash) {
    public OverlapReceipt {
        ReceiptValidation.check(implementation, protocol, basisGeometryHash, calculationHash,
                resultHash, status, reason, receiptHash);
    }
}
