package totah.lab.prometheus.inventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.store.EvidenceMemoryIndex;

/** Read-only, molecule-agnostic inventory over canonical in-memory evidence. */
public final class EvidenceInventoryService {

    private final List<QuantumEvidence> quantum;
    private final List<ClassicalEvidence> classical;

    public EvidenceInventoryService(EvidenceMemoryIndex index) {
        Objects.requireNonNull(index, "index");
        quantum = List.copyOf(index.quantum());
        classical = List.copyOf(index.classical());
    }

    public EvidenceInventoryService(EvidenceBundle bundle) {
        this(new EvidenceMemoryIndex(Objects.requireNonNull(bundle, "bundle")));
    }

    public EvidenceInventorySnapshot snapshot() {
        return new EvidenceInventorySnapshot(
                summarizeQuantum(quantum),
                summarizeClassical(classical),
                provenanceGaps());
    }

    public List<QuantumEvidence> queryQuantum(EvidenceInventoryQuery query) {
        Objects.requireNonNull(query, "query");
        return quantum.stream()
                .filter(item -> matches(item.identity(), item.acceptance(), query))
                .toList();
    }

    public List<ClassicalEvidence> queryClassical(EvidenceInventoryQuery query) {
        Objects.requireNonNull(query, "query");
        return classical.stream()
                .filter(item -> matches(item.identity(), item.acceptance(), query))
                .toList();
    }

    private EvidenceDimensionSummary summarizeQuantum(List<QuantumEvidence> values) {
        return summarize(
                values.stream().map(QuantumEvidence::identity).toList(),
                values.stream().map(QuantumEvidence::acceptance).toList());
    }

    private EvidenceDimensionSummary summarizeClassical(List<ClassicalEvidence> values) {
        return summarize(
                values.stream().map(ClassicalEvidence::identity).toList(),
                values.stream().map(ClassicalEvidence::acceptance).toList());
    }

    private EvidenceDimensionSummary summarize(
            List<EvidenceIdentity> identities,
            List<EvidenceAcceptanceState> acceptances) {

        Map<String, Long> byMolecule = count(identities,
                identity -> identity.molecule().moleculeId());
        Map<String, Long> byProtocol = count(identities,
                identity -> identity.protocol().protocolKey());
        Map<CalculationType, Long> byCalculation = new EnumMap<>(CalculationType.class);
        for (EvidenceIdentity identity : identities) {
            byCalculation.merge(identity.calculationType(), 1L, Long::sum);
        }
        Map<EvidenceAcceptanceState, Long> byAcceptance =
                new EnumMap<>(EvidenceAcceptanceState.class);
        for (EvidenceAcceptanceState acceptance : acceptances) {
            byAcceptance.merge(acceptance, 1L, Long::sum);
        }
        return new EvidenceDimensionSummary(
                identities.size(), byMolecule, byProtocol, byCalculation, byAcceptance);
    }

    private static <T> Map<String, Long> count(List<T> values, Function<T, String> classifier) {
        return values.stream().collect(Collectors.groupingBy(
                classifier, LinkedHashMap::new, Collectors.counting()));
    }

    private List<ProvenanceGap> provenanceGaps() {
        Set<String> presentHashes = new HashSet<>();
        quantum.forEach(item -> presentHashes.add(item.identity().evidenceHash()));
        classical.forEach(item -> presentHashes.add(item.identity().evidenceHash()));
        List<ProvenanceGap> gaps = new ArrayList<>();
        for (QuantumEvidence item : quantum) {
            inspectCommon(EvidenceDimension.QUANTUM, item.identity(), item.provenance(),
                    presentHashes, gaps);
        }
        for (ClassicalEvidence item : classical) {
            inspectCommon(EvidenceDimension.CLASSICAL, item.identity(), item.provenance(),
                    presentHashes, gaps);
            if (item.topologyReference().isBlank()) {
                addGap(EvidenceDimension.CLASSICAL, item.identity(),
                        ProvenanceGapType.TOPOLOGY_REFERENCE_MISSING,
                        "classical topology reference is blank", gaps);
            }
        }
        return List.copyOf(gaps);
    }

    private void inspectCommon(
            EvidenceDimension dimension,
            EvidenceIdentity identity,
            EvidenceProvenance provenance,
            Set<String> presentHashes,
            List<ProvenanceGap> gaps) {

        if (provenance.sourcePath().isBlank()) {
            addGap(dimension, identity, ProvenanceGapType.SOURCE_PATH_MISSING,
                    "source artifact path is blank", gaps);
        }
        if (provenance.sha256().isBlank()) {
            addGap(dimension, identity, ProvenanceGapType.SOURCE_CHECKSUM_MISSING,
                    "source artifact SHA-256 is blank", gaps);
        }
        if (unresolved(identity.protocol().method())) {
            addGap(dimension, identity, ProvenanceGapType.PROTOCOL_METHOD_MISSING,
                    "protocol method is unresolved", gaps);
        }
        if (unresolved(identity.protocol().software())) {
            addGap(dimension, identity, ProvenanceGapType.PROTOCOL_SOFTWARE_MISSING,
                    "protocol software is unresolved", gaps);
        }
        if (unresolved(identity.protocol().softwareVersion())) {
            addGap(dimension, identity, ProvenanceGapType.SOFTWARE_VERSION_MISSING,
                    "protocol software version is unresolved", gaps);
        }
        for (String derivedHash : provenance.derivedFromEvidenceHashes()) {
            if (!presentHashes.contains(derivedHash)) {
                addGap(dimension, identity, ProvenanceGapType.DERIVED_EVIDENCE_NOT_IN_INVENTORY,
                        "derived-from evidence is absent from this inventory: " + derivedHash,
                        gaps);
            }
        }
    }

    private static boolean unresolved(String value) {
        return value.isBlank()
                || value.equalsIgnoreCase("unknown")
                || value.equalsIgnoreCase("n/a")
                || value.equalsIgnoreCase("na");
    }

    private void addGap(
            EvidenceDimension dimension,
            EvidenceIdentity identity,
            ProvenanceGapType type,
            String detail,
            List<ProvenanceGap> gaps) {

        gaps.add(new ProvenanceGap(
                dimension,
                identity.evidenceHash(),
                identity.molecule().moleculeId(),
                type,
                detail));
    }

    private boolean matches(
            EvidenceIdentity identity,
            EvidenceAcceptanceState acceptance,
            EvidenceInventoryQuery query) {

        return query.moleculeId().map(value -> value.equals(identity.molecule().moleculeId())).orElse(true)
                && query.protocolKey().map(value -> value.equals(identity.protocol().protocolKey())).orElse(true)
                && query.calculationType().map(value -> value == identity.calculationType()).orElse(true)
                && query.acceptance().map(value -> value == acceptance).orElse(true);
    }
}
