package totah.lab.aether.provenance;

import java.util.Objects;

/** System-bound Hcore identity, retaining the unmodified source T and V receipts. */
public record CoreHamiltonianReceipt(String implementation, String protocol, String basisGeometryHash,
                                     String systemHash, KineticReceipt kineticReceipt,
                                     NuclearAttractionReceipt nuclearAttractionReceipt,
                                     String calculationHash, String resultHash, ScientificStatus status,
                                     String reason, String receiptHash) {
    public CoreHamiltonianReceipt {
        ReceiptValidation.check(implementation, protocol, basisGeometryHash, calculationHash,
                resultHash, status, reason, receiptHash);
        ReceiptValidation.hash(systemHash);
        Objects.requireNonNull(kineticReceipt);
        Objects.requireNonNull(nuclearAttractionReceipt);
        if (!basisGeometryHash.equals(kineticReceipt.basisGeometryHash())
                || !basisGeometryHash.equals(nuclearAttractionReceipt.basisGeometryHash())) {
            throw new IllegalArgumentException("Incompatible source basis provenance");
        }
        if (status != ScientificStatus.SCREENING_ONLY) {
            throw new IllegalArgumentException("Core Hamiltonian results must remain SCREENING_ONLY");
        }
    }
}
