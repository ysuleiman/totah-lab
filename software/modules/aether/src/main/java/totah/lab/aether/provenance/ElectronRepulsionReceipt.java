package totah.lab.aether.provenance;

/** Deterministic identity of an eightfold-symmetry-packed ERI tensor; excludes elapsed time. */
public record ElectronRepulsionReceipt(String implementation, String protocol, String basisGeometryHash,
                                       String systemHash, int basisSize, long symmetryUniqueQuartets,
                                       String calculationHash, String resultHash, ScientificStatus status,
                                       String reason, String receiptHash) {
    public ElectronRepulsionReceipt {
        ReceiptValidation.check(implementation, protocol, basisGeometryHash, calculationHash,
                resultHash, status, reason, receiptHash);
        ReceiptValidation.hash(systemHash);
        long pairs = (long) basisSize * (basisSize + 1L) / 2;
        if (basisSize <= 0 || pairs > 65535 || symmetryUniqueQuartets != pairs * (pairs + 1) / 2) {
            throw new IllegalArgumentException("Invalid packed ERI dimensions");
        }
        if (status != ScientificStatus.SCREENING_ONLY) {
            throw new IllegalArgumentException("ERI results must remain SCREENING_ONLY");
        }
    }
}
