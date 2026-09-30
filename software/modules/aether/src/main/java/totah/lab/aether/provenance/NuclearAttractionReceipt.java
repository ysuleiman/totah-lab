package totah.lab.aether.provenance;

/** Identity of a nuclear-attraction operator matrix in hartree, including its nuclei. */
public record NuclearAttractionReceipt(String implementation, String protocol, String basisGeometryHash,
                                      String nuclearCentersHash, String calculationHash, String resultHash,
                                      ScientificStatus status, String reason, String receiptHash) {
    public NuclearAttractionReceipt {
        ReceiptValidation.check(implementation, protocol, basisGeometryHash, calculationHash,
                resultHash, status, reason, receiptHash);
        ReceiptValidation.hash(nuclearCentersHash);
    }
}
