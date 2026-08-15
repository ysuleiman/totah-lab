package totah.lab.prometheus.evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plain collection of evidence keyed by evidence hash.
 *
 * <p>Adding an exact duplicate (same hash, same record) is idempotent and reports
 * "not new"; adding a DIFFERENT evidence record under an already-present hash is a
 * collision and throws {@link IllegalArgumentException}. Comparability-checked
 * merging across bundles is a later wave and intentionally not provided here.
 */
public final class EvidenceBundle {

    private final Map<String, QuantumEvidence> quantumByHash = new LinkedHashMap<>();
    private final Map<String, ClassicalEvidence> classicalByHash = new LinkedHashMap<>();

    /**
     * Adds quantum evidence. Returns true when the hash was new, false when an
     * identical record was already stored under that hash.
     *
     * @throws IllegalArgumentException if a different record already occupies the hash
     */
    public boolean add(QuantumEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        String hash = evidence.identity().evidenceHash();
        QuantumEvidence existing = quantumByHash.get(hash);
        if (existing != null) {
            if (!existing.equals(evidence)) {
                throw new IllegalArgumentException(
                        "evidence hash collision: different quantum evidence under hash " + hash);
            }
            return false;
        }
        quantumByHash.put(hash, evidence);
        return true;
    }

    /**
     * Adds classical evidence. Returns true when the hash was new, false when an
     * identical record was already stored under that hash.
     *
     * @throws IllegalArgumentException if a different record already occupies the hash
     */
    public boolean add(ClassicalEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        String hash = evidence.identity().evidenceHash();
        ClassicalEvidence existing = classicalByHash.get(hash);
        if (existing != null) {
            if (!existing.equals(evidence)) {
                throw new IllegalArgumentException(
                        "evidence hash collision: different classical evidence under hash " + hash);
            }
            return false;
        }
        classicalByHash.put(hash, evidence);
        return true;
    }

    public List<QuantumEvidence> quantum() {
        return List.copyOf(quantumByHash.values());
    }

    public List<ClassicalEvidence> classical() {
        return List.copyOf(classicalByHash.values());
    }

    /** All quantum evidence of the given calculation type. */
    public List<QuantumEvidence> byType(CalculationType type) {
        Objects.requireNonNull(type, "type");
        List<QuantumEvidence> result = new ArrayList<>();
        for (QuantumEvidence evidence : quantumByHash.values()) {
            if (evidence.identity().calculationType() == type) {
                result.add(evidence);
            }
        }
        return List.copyOf(result);
    }

    /** All quantum evidence computed under the given protocol key. */
    public List<QuantumEvidence> byProtocolKey(String protocolKey) {
        Objects.requireNonNull(protocolKey, "protocolKey");
        List<QuantumEvidence> result = new ArrayList<>();
        for (QuantumEvidence evidence : quantumByHash.values()) {
            if (evidence.identity().protocol().protocolKey().equals(protocolKey)) {
                result.add(evidence);
            }
        }
        return List.copyOf(result);
    }

    /** All quantum evidence in {@link EvidenceAcceptanceState#ACCEPTED} state. */
    public List<QuantumEvidence> accepted() {
        List<QuantumEvidence> result = new ArrayList<>();
        for (QuantumEvidence evidence : quantumByHash.values()) {
            if (evidence.acceptance() == EvidenceAcceptanceState.ACCEPTED) {
                result.add(evidence);
            }
        }
        return List.copyOf(result);
    }

    /** All stored quantum evidence whose identity is an exact duplicate of {@code identity}. */
    public List<QuantumEvidence> findExactDuplicates(EvidenceIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        List<QuantumEvidence> result = new ArrayList<>();
        QuantumEvidence match = quantumByHash.get(identity.evidenceHash());
        if (match != null) {
            result.add(match);
        }
        return List.copyOf(result);
    }

    /** Total number of stored evidence records (quantum + classical). */
    public int size() {
        return quantumByHash.size() + classicalByHash.size();
    }
}
