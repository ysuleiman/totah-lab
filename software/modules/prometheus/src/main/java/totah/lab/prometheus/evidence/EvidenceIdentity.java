package totah.lab.prometheus.evidence;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;

/**
 * Identity of a single piece of evidence: which molecule, atom map, geometry,
 * charge/multiplicity, calculation type and protocol produced it, plus any
 * constraints and requested outputs.
 *
 * <p>The same coordinates evaluated under a different QM method is NOT equivalent
 * evidence: {@link #sameGeometryDifferentProtocol(EvidenceIdentity)} is true in
 * that case, while {@link #isExactDuplicateOf(EvidenceIdentity)} is false.
 */
public record EvidenceIdentity(
        MoleculeIdentity molecule,
        String atomMapHash,
        GeometryIdentity geometry,
        int formalCharge,
        int multiplicity,
        CalculationType calculationType,
        QmProtocol protocol,
        List<String> constraints,
        List<String> requestedOutputs) {

    public EvidenceIdentity {
        Objects.requireNonNull(molecule, "molecule");
        Objects.requireNonNull(atomMapHash, "atomMapHash");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(calculationType, "calculationType");
        Objects.requireNonNull(protocol, "protocol");
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        requestedOutputs = List.copyOf(Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
    }

    /**
     * SHA-256 over the canonical serialization of every field: molecule id,
     * atom map hash, geometry hash and atom count, formal charge, multiplicity,
     * calculation type, protocol key, and the constraint / requested-output
     * lines, joined with {@code '\n'}.
     */
    public String evidenceHash() {
        StringBuilder sb = new StringBuilder();
        sb.append("molecule=").append(escapeLine(molecule.moleculeId()))
                .append('\n').append("atomMapHash=").append(escapeLine(atomMapHash))
                .append('\n').append("geometry=").append(escapeLine(geometry.sha256()))
                .append('\n').append("atomCount=").append(geometry.atomCount())
                .append('\n').append("formalCharge=").append(formalCharge)
                .append('\n').append("multiplicity=").append(multiplicity)
                .append('\n').append("calculationType=").append(calculationType.name())
                .append('\n').append("protocol=").append(escapeLine(protocol.protocolKey()))
                .append('\n').append("constraints=").append(escapedList(constraints))
                .append('\n').append("requestedOutputs=").append(escapedList(requestedOutputs));
        return CanonicalHashing.sha256Hex(sb.toString());
    }

    private static String escapedList(List<String> values) {
        return values.stream().map(value -> escapeLine(value).replace(",", "\\,"))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String escapeLine(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** True when the two identities hash identically, i.e. every field matches. */
    public boolean isExactDuplicateOf(EvidenceIdentity other) {
        Objects.requireNonNull(other, "other");
        return evidenceHash().equals(other.evidenceHash());
    }

    /**
     * True when both identities describe the same molecule, atom map, geometry,
     * formal charge, multiplicity and calculation type, but were computed under
     * different protocols. Same coordinates under a different QM method is not
     * equivalent evidence.
     */
    public boolean sameGeometryDifferentProtocol(EvidenceIdentity other) {
        Objects.requireNonNull(other, "other");
        return molecule.equals(other.molecule)
                && atomMapHash.equals(other.atomMapHash)
                && geometry.equals(other.geometry)
                && formalCharge == other.formalCharge
                && multiplicity == other.multiplicity
                && calculationType == other.calculationType
                && !protocol.protocolKey().equals(other.protocol.protocolKey());
    }
}
