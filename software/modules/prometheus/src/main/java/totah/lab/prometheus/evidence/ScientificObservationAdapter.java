package totah.lab.prometheus.evidence;

import totah.lab.mnemosyne.Observation;
import totah.lab.mnemosyne.ScientificReference;

import java.util.List;

import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** Read-only scalar projection. Does not register, execute, accept, retry or make evidence reusable. */
public final class ScientificObservationAdapter {
    private ScientificObservationAdapter() { }

    /**
     * Existing result and receipt remain authoritative. Caller supplies actual run and observation IDs;
     * the scientific specification hash never becomes either identity. Availability is not acceptance.
     */
    public static Observation energy(QuantumEvidence evidence, ScientificReference observation, ScientificReference run) {
        var identity = evidence.identity();
        var source = evidence.provenance();
        var value = evidence.energyHartree().map(v -> new Observation.Scalar(Double.toString(v), "hartree"));
        var availability = value.isPresent() ? Observation.Availability.PRESENT
                : evidence.convergence() == ConvergenceStatus.CONVERGED ? Observation.Availability.UNAVAILABLE
                : Observation.Availability.FAILED_INVALID;
        // Preserve declared historical checksums, including synthetic fixture placeholders, without claiming verification.
        String checksumNamespace = source.sha256().matches("[0-9a-f]{64}") ? "sha256" : "prometheus-declared-checksum";
        var provenance = new Observation.Provenance(
                ref(SOURCE, "prometheus-source", source.sourcePath(), source.sha256()),
                ref(ARTIFACT, checksumNamespace, source.sha256(), "1"),
                ref(RECEIPT, "prometheus-quantum-evidence", identity.evidenceHash(), source.sha256()),
                ref(METHOD, "prometheus-projection", "QuantumEvidence.energyHartree", "1"),
                source.sourcePath() + "#energyHartree",
                source.derivedFromEvidenceHashes().stream()
                        .map(hash -> ref(RECEIPT, "prometheus-evidence-specification", hash, "1")).toList());
        return new Observation(observation,
                ref(SUBJECT, "prometheus-molecular-state", identity.molecule().moleculeId(),
                        identity.geometry().sha256() + ":" + identity.atomMapHash() + ":" + identity.formalCharge() + ":" + identity.multiplicity()),
                ref(ENDPOINT, "prometheus-endpoint", "energyHartree:" + identity.calculationType(), "1"),
                ref(METHOD, "prometheus-calculation-specification", identity.evidenceHash(), "1"),
                ref(CONTEXT, "prometheus-calculation-context", identity.evidenceHash(), "1"), run,
                provenance, availability, value,
                new Observation.Unknown("QuantumEvidence contains no uncertainty estimate; point precision is not physical certainty"),
                List.of("Domain convergence=" + evidence.convergence(), "Domain acceptance=" + evidence.acceptance(),
                        "Convergence note=" + evidence.convergenceNote(), "Source note=" + source.note(),
                        "Source ingestedAt=" + source.ingestedAt(),
                        "Double.toString projection; original lexical representation, if available, remains in the source artifact",
                        "Projection confers no PRIMARY/AUXILIARY role, training eligibility or registry reuse permission"));
    }
    private static ScientificReference ref(ScientificReference.Kind kind, String namespace, String id, String version) {
        return new ScientificReference(kind, namespace, id, version);
    }
}
