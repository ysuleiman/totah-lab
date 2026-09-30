package totah.lab.aether.provenance;

import java.util.Objects;

/** Shared deterministic identity of typed J/K matrices and their supplied density/ERI lineage. */
public record JkReceipt(String implementation, String protocol, String basisGeometryHash, String systemHash,
                        String densityHash, ElectronRepulsionReceipt eriReceipt, String coulombResultHash,
                        String exchangeResultHash, String calculationHash, String resultHash,
                        ScientificStatus status, String reason, String receiptHash) {
    public JkReceipt {
        ReceiptValidation.check(implementation, protocol, basisGeometryHash, calculationHash,
                resultHash, status, reason, receiptHash);
        for (String hash : new String[]{systemHash, densityHash, coulombResultHash, exchangeResultHash}) {
            ReceiptValidation.hash(hash);
        }
        Objects.requireNonNull(eriReceipt);
        if (!basisGeometryHash.equals(eriReceipt.basisGeometryHash()) || !systemHash.equals(eriReceipt.systemHash())) {
            throw new IllegalArgumentException("Incompatible ERI provenance");
        }
        if (status != ScientificStatus.SCREENING_ONLY) throw new IllegalArgumentException("J/K results must remain SCREENING_ONLY");
    }
}
