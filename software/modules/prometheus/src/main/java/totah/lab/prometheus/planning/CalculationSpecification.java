package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

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
 * <p>{@code constraints}, {@code requiredOutputs}, and {@code acceptanceGates}
 * are sets of independent scientific clauses, not execution sequences. Their
 * encounter order and duplicate presentation have no scientific meaning, so
 * the constructor stores each as a unique lexicographically sorted list. This
 * decision is part of the identity contract; executors that need procedural
 * ordering must encode that ordering inside a single clause or introduce a
 * separately specified ordered field.
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
        constraints = canonicalClauses(constraints, "constraints");
        Objects.requireNonNull(calculationType, "calculationType");
        requiredOutputs = canonicalClauses(requiredOutputs, "requiredOutputs");
        acceptanceGates = canonicalClauses(acceptanceGates, "acceptanceGates");
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
        return CanonicalHashing.sha256Hex(CanonicalHashing.sequence(List.of(
                scientificPurpose, molecule.moleculeId(), geometry.sha256(), Integer.toString(geometry.atomCount()),
                Integer.toString(formalCharge), Integer.toString(multiplicity), protocol.protocolKey(),
                CanonicalHashing.sequence(constraints), calculationType.name(),
                CanonicalHashing.sequence(requiredOutputs), CanonicalHashing.sequence(acceptanceGates), role.name(),
                Integer.toString(estimatedCost.jobCount()), CanonicalHashing.format(estimatedCost.cpuHoursPerJob()),
                CanonicalHashing.format(estimatedCost.expectedWallHours()),
                CanonicalHashing.format(estimatedCost.expectedLocalRuntimeHours()),
                CanonicalHashing.format(estimatedCost.estimatedRemoteCostUsd()))));
    }

    private static List<String> canonicalClauses(List<String> clauses, String label) {
        Objects.requireNonNull(clauses, label);
        TreeSet<String> canonical = new TreeSet<>();
        for (String clause : clauses) {
            Objects.requireNonNull(clause, label + " item");
            if (clause.isBlank()) throw new IllegalArgumentException(label + " contains a blank item");
            canonical.add(clause);
        }
        return List.copyOf(canonical);
    }
}
