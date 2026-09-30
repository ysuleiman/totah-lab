package totah.lab.aether.provenance;

import java.util.Objects;

/** Complete supplied-state lineage and intermediate evidence hashes; excludes timings. */
public record OneShotRhfReceipt(String implementation, String protocol, String basisGeometryHash,
                                String systemHash, String densityHash, OverlapReceipt overlapReceipt,
                                CoreHamiltonianReceipt coreReceipt, JkReceipt jkReceipt,
                                String fockHash, String overlapEigenvaluesHash, String orthogonalizationHash,
                                String orthogonalFockHash, String coefficientsHash, String orbitalEnergiesHash,
                                String calculationHash, String resultHash, ScientificStatus status,
                                String reason, String receiptHash) {
    public OneShotRhfReceipt {
        ReceiptValidation.check(implementation, protocol, basisGeometryHash, calculationHash,
                resultHash, status, reason, receiptHash);
        Objects.requireNonNull(overlapReceipt); Objects.requireNonNull(coreReceipt); Objects.requireNonNull(jkReceipt);
        for (String hash : new String[]{systemHash, densityHash, fockHash, overlapEigenvaluesHash,
                orthogonalizationHash, orthogonalFockHash, coefficientsHash, orbitalEnergiesHash}) ReceiptValidation.hash(hash);
        if (!basisGeometryHash.equals(overlapReceipt.basisGeometryHash()) || !basisGeometryHash.equals(coreReceipt.basisGeometryHash())
                || !basisGeometryHash.equals(jkReceipt.basisGeometryHash()) || !systemHash.equals(coreReceipt.systemHash())
                || !systemHash.equals(jkReceipt.systemHash()) || !densityHash.equals(jkReceipt.densityHash())) {
            throw new IllegalArgumentException("Incompatible one-shot provenance");
        }
        if (status != ScientificStatus.SCREENING_ONLY) throw new IllegalArgumentException("One-shot evidence must remain SCREENING_ONLY");
    }
}
