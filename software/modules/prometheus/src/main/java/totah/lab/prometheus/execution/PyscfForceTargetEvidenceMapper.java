package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.store.GeneratedEvidenceCandidate;
import totah.lab.prometheus.store.GeneratedEvidenceRole;

/** Validates one campaign result and maps it to a reusable immutable QM target. */
public final class PyscfForceTargetEvidenceMapper implements GeneratedEvidenceMapper {
    private final EvidenceIdentity identity;
    private final ObjectMapper mapper = new ObjectMapper();
    public PyscfForceTargetEvidenceMapper(EvidenceIdentity identity) { this.identity = identity; }

    @Override public List<GeneratedEvidenceCandidate> validateAndMap(
            RawCalculationResult raw, Path base) throws IOException {
        Path resultPath = base.resolve("result.json");
        JsonNode root = mapper.readTree(resultPath.toFile());
        if (!root.path("scf_converged").asBoolean(false)) throw new IOException("SCF not converged");
        if (!root.path("scientific_identity").asText().equals(identity.evidenceHash())) {
            throw new IOException("scientific identity mismatch");
        }
        if (!root.path("geometry_identity").asText().equals(identity.geometry().sha256())) {
            throw new IOException("geometry identity mismatch");
        }
        if (!root.path("units").path("energy").asText().equals("hartree")
                || !root.path("units").path("gradient").asText().equals("hartree/bohr")
                || !root.path("units").path("force").asText().equals("hartree/bohr")) {
            throw new IOException("force target units mismatch");
        }
        JsonNode gradients = root.path("gradient_hartree_per_bohr");
        JsonNode forces = root.path("force_hartree_per_bohr");
        if (gradients.size() != identity.geometry().atomCount() || forces.size() != gradients.size()) {
            throw new IOException("gradient/force atom count mismatch");
        }
        List<Double> flattened = new ArrayList<>();
        double norm2 = 0.0;
        for (int atom = 0; atom < gradients.size(); atom++) {
            if (gradients.get(atom).size() != 3 || forces.get(atom).size() != 3) {
                throw new IOException("gradient/force must be N x 3");
            }
            for (int component = 0; component < 3; component++) {
                double gradient = gradients.get(atom).get(component).asDouble();
                double force = forces.get(atom).get(component).asDouble();
                if (!Double.isFinite(gradient) || Math.abs(gradient + force) > 1e-12) {
                    throw new IOException("force sign or finiteness validation failed");
                }
                flattened.add(gradient); norm2 += gradient * gradient;
            }
        }
        double energy = root.path("energy_hartree").asDouble(Double.NaN);
        if (!Double.isFinite(energy)) throw new IOException("non-finite energy");
        double declaredNorm = root.path("gradient_norm_hartree_per_bohr").asDouble(Double.NaN);
        if (Math.abs(Math.sqrt(norm2) - declaredNorm) > 1e-10) throw new IOException("gradient norm mismatch");
        QuantumEvidence evidence = new QuantumEvidence(identity,
                new EvidenceProvenance(resultPath.toString(), ArtifactChecksums.sha256(resultPath), Instant.now(),
                        List.of(), "synchronously persisted common-protocol force target"),
                ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED,
                Optional.of(energy), Optional.of(List.copyOf(flattened)), Optional.empty(), Optional.empty(),
                Optional.empty(), "all locked force-target validation gates passed");
        return List.of(new GeneratedEvidenceCandidate(evidence, GeneratedEvidenceRole.PRIMARY, base,
                raw.artifacts(), "immutable ForceBalance-readable QM target"));
    }
}
