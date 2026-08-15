package totah.lab.prometheus.store;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;

/** Immutable in-memory view loaded from canonical Prometheus files. */
public final class EvidenceMemoryIndex {

    private final Map<String, QuantumEvidence> quantum;
    private final Map<String, ClassicalEvidence> classical;

    public EvidenceMemoryIndex(EvidenceBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        Map<String, QuantumEvidence> quantumIndex = new LinkedHashMap<>();
        bundle.quantum().forEach(value -> quantumIndex.put(value.identity().evidenceHash(), value));
        Map<String, ClassicalEvidence> classicalIndex = new LinkedHashMap<>();
        bundle.classical().forEach(value -> classicalIndex.put(value.identity().evidenceHash(), value));
        quantum = Map.copyOf(quantumIndex);
        classical = Map.copyOf(classicalIndex);
    }

    public Optional<QuantumEvidence> quantum(String evidenceHash) {
        return Optional.ofNullable(quantum.get(Objects.requireNonNull(evidenceHash, "evidenceHash")));
    }

    public Optional<ClassicalEvidence> classical(String evidenceHash) {
        return Optional.ofNullable(classical.get(Objects.requireNonNull(evidenceHash, "evidenceHash")));
    }

    public List<QuantumEvidence> quantum() {
        return List.copyOf(quantum.values());
    }

    public List<ClassicalEvidence> classical() {
        return List.copyOf(classical.values());
    }

    public int size() {
        return quantum.size() + classical.size();
    }
}
