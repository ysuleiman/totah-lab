package totah.lab.prometheus.fixtures;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.identity.GeometryIdentity;

/** Shared QM protocols and evidence identities used across the Prometheus tests. */
public final class EvidenceFixtures {

    /** PBE-D3(BJ)/def2-SVP — TSL conformational level of theory. */
    public static final QmProtocol PBE_DEF2_SVP =
            new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false, "ORCA", "5.0.4");

    /** PBE0-D3(BJ)/def2-TZVP — TSL counterpoise interaction level of theory. */
    public static final QmProtocol PBE0_DEF2_TZVP =
            new QmProtocol("PBE0", "def2-TZVP", "D3(BJ)", "none", true, "ORCA", "5.0.4");

    /** HF/6-31G(d) — TSL ESP level of theory. */
    public static final QmProtocol HF_631Gd =
            new QmProtocol("HF", "6-31G(d)", "none", "none", false, "Gaussian", "16");

    private EvidenceFixtures() {
    }

    public static EvidenceIdentity identity(
            CalculationType type,
            QmProtocol protocol,
            GeometryIdentity geometry) {

        return new EvidenceIdentity(
                TslFixtures.TSL,
                TslFixtures.canonicalMap().canonicalHash(),
                geometry,
                0,
                1,
                type,
                protocol,
                List.of(),
                List.of("energy"));
    }

    public static EvidenceProvenance provenance(String sourcePath) {
        return new EvidenceProvenance(
                sourcePath,
                "deadbeef",
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of(),
                "");
    }

    public static QuantumEvidence acceptedQuantum(EvidenceIdentity identity, double energyHartree) {
        return new QuantumEvidence(
                identity,
                provenance("/archive/tsl/evidence.log"),
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED,
                Optional.of(energyHartree),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "converged normally");
    }
}
