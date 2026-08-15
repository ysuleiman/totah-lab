package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;

/**
 * A frozen, fully-specified scientific calculation: the exact molecule,
 * geometry, electronic state, protocol, constraints, calculation type,
 * required outputs and acceptance gates an executor must honor.
 *
 * <p>This object is deeply immutable (record components plus defensively copied
 * lists). Executors receive this frozen object and MUST NOT alter it or any
 * derived copy of its scientific content: an executor performs the specified
 * calculation and nothing else.
 *
 * <p>{@link #checksum()} is the scientific content hash: SHA-256 over the
 * canonical serialization of every field EXCEPT {@code specificationId}. Two
 * specifications that differ only in their id are scientifically identical and
 * therefore hash equally; any difference in method, basis, geometry, state,
 * constraints, outputs, gates or cost changes the checksum.
 *
 * <p>{@code acceptanceGates} must be non-empty — every calculation carries the
 * gates its results will be judged against; a calculation without gates can
 * never be accepted and must not be specified.
 */
public record CalculationSpecification(
        String specificationId,
        String scientificPurpose,
        MoleculeIdentity molecule,
        GeometryIdentity geometry,
        int formalCharge,
        int multiplicity,
        QmProtocol protocol,
        List<String> constraints,
        CalculationType calculationType,
        List<String> requiredOutputs,
        List<String> acceptanceGates,
        DatasetRole role,
        CostEstimate estimatedCost) {

    public CalculationSpecification {
        Objects.requireNonNull(specificationId, "specificationId");
        if (specificationId.isBlank()) {
            throw new IllegalArgumentException("specificationId must be non-blank");
        }
        Objects.requireNonNull(scientificPurpose, "scientificPurpose");
        if (scientificPurpose.isBlank()) {
            throw new IllegalArgumentException("scientificPurpose must be non-blank");
        }
        Objects.requireNonNull(molecule, "molecule");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(protocol, "protocol");
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        Objects.requireNonNull(calculationType, "calculationType");
        requiredOutputs = List.copyOf(Objects.requireNonNull(requiredOutputs, "requiredOutputs"));
        acceptanceGates = List.copyOf(Objects.requireNonNull(acceptanceGates, "acceptanceGates"));
        if (acceptanceGates.isEmpty()) {
            throw new IllegalArgumentException(
                    "acceptanceGates must be non-empty: every calculation carries its acceptance gates");
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
    }

    /**
     * Scientific content hash: SHA-256 over the canonical serialization of all
     * fields except {@code specificationId}. Two specs differing only in id are
     * scientifically identical and share a checksum.
     */
    public String checksum() {
        StringBuilder sb = new StringBuilder();
        sb.append("scientificPurpose=").append(scientificPurpose)
                .append('\n').append("molecule=").append(molecule.moleculeId())
                .append('\n').append("geometry=").append(geometry.sha256())
                .append('\n').append("atomCount=").append(geometry.atomCount())
                .append('\n').append("formalCharge=").append(formalCharge)
                .append('\n').append("multiplicity=").append(multiplicity)
                .append('\n').append("protocol=").append(protocol.protocolKey())
                .append('\n').append("constraints=").append(String.join(",", constraints))
                .append('\n').append("calculationType=").append(calculationType.name())
                .append('\n').append("requiredOutputs=").append(String.join(",", requiredOutputs))
                .append('\n').append("acceptanceGates=").append(String.join(",", acceptanceGates))
                .append('\n').append("role=").append(role.name())
                .append('\n').append("jobCount=").append(estimatedCost.jobCount())
                .append('\n').append("cpuHoursPerJob=")
                .append(CanonicalHashing.format(estimatedCost.cpuHoursPerJob()))
                .append('\n').append("expectedWallHours=")
                .append(CanonicalHashing.format(estimatedCost.expectedWallHours()))
                .append('\n').append("expectedLocalRuntimeHours=")
                .append(CanonicalHashing.format(estimatedCost.expectedLocalRuntimeHours()))
                .append('\n').append("estimatedRemoteCostUsd=")
                .append(CanonicalHashing.format(estimatedCost.estimatedRemoteCostUsd()));
        return CanonicalHashing.sha256Hex(sb.toString());
    }
}
