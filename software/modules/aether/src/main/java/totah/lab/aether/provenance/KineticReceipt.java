package totah.lab.aether.provenance;

/** Hashes identify a kinetic integral matrix in hartree, not a total electronic energy. */
public record KineticReceipt(String implementation, String protocol, String basisGeometryHash,
                             String calculationHash, String resultHash, ScientificStatus status,
                             String reason, String receiptHash) {
    public KineticReceipt {
        ReceiptValidation.check(implementation, protocol, basisGeometryHash, calculationHash,
                resultHash, status, reason, receiptHash);
    }
}
