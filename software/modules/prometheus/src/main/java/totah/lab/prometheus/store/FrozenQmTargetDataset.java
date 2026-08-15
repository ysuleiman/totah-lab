package totah.lab.prometheus.store;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QuantumEvidence;

/**
 * Immutable ForceBalance-facing QM target boundary. It contains accepted
 * primary evidence only and deliberately has no executor or registry mutation
 * capability.
 */
public final class FrozenQmTargetDataset {

    private final Map<String, QuantumEvidence> targets;

    private FrozenQmTargetDataset(Map<String, QuantumEvidence> targets) {
        this.targets = Map.copyOf(targets);
    }

    public static FrozenQmTargetDataset from(GeneratedEvidenceRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        Map<String, QuantumEvidence> selected = new LinkedHashMap<>();
        registry.entries().stream()
                .filter(entry -> entry.role() == GeneratedEvidenceRole.PRIMARY)
                .flatMap(entry -> entry.evidence().stream())
                .filter(evidence -> evidence.acceptance() == EvidenceAcceptanceState.ACCEPTED)
                .forEach(evidence -> selected.putIfAbsent(evidence.identity().evidenceHash(), evidence));
        return new FrozenQmTargetDataset(selected);
    }

    public Optional<QuantumEvidence> target(String scientificIdentityHash) {
        return Optional.ofNullable(targets.get(scientificIdentityHash));
    }

    public List<QuantumEvidence> targets() {
        return List.copyOf(targets.values());
    }

    public int size() {
        return targets.size();
    }
}
