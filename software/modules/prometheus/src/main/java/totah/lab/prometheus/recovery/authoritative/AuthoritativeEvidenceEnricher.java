package totah.lab.prometheus.recovery.authoritative;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveryAuditEntry;
import totah.lab.prometheus.recovery.RecoveryAuditReport;
import totah.lab.prometheus.recovery.RecoveryClassification;
import totah.lab.prometheus.store.EvidenceMemoryIndex;

/** Applies only raw-backed protocol metadata recoveries to an in-memory bundle. */
public final class AuthoritativeEvidenceEnricher {

    public EvidenceBundle enrich(Path archiveRoot, EvidenceBundle original) throws IOException {
        Objects.requireNonNull(archiveRoot, "archiveRoot");
        Objects.requireNonNull(original, "original");
        EvidenceMemoryIndex index = new EvidenceMemoryIndex(original);
        RecoveryAuditReport audit = new AuthoritativeScientificAuditRunner()
                .recoverGaps(archiveRoot, index, "pre-canonical-import");
        Map<String, Map<String, RecoveryAuditEntry>> recovered = recoveredByHash(audit);

        Map<String, EvidenceIdentity> identities = new LinkedHashMap<>();
        original.quantum().forEach(item -> identities.put(item.identity().evidenceHash(),
                enrichIdentity(item.identity(), recovered.get(item.identity().evidenceHash()))));
        original.classical().forEach(item -> identities.put(item.identity().evidenceHash(),
                enrichIdentity(item.identity(), recovered.get(item.identity().evidenceHash()))));
        Map<String, String> remappedHashes = new LinkedHashMap<>();
        identities.forEach((oldHash, identity) -> remappedHashes.put(oldHash, identity.evidenceHash()));

        EvidenceBundle enriched = new EvidenceBundle();
        for (QuantumEvidence item : original.quantum()) {
            EvidenceIdentity identity = identities.get(item.identity().evidenceHash());
            QuantumEvidence replacement = new QuantumEvidence(identity,
                    remapProvenance(item.provenance(), remappedHashes,
                            recovered.get(item.identity().evidenceHash())),
                    item.convergence(), item.acceptance(), item.energyHartree(),
                    item.gradientHartreePerBohr(), item.hessianHartreePerBohr2(), item.dipoleDebye(),
                    item.interactionEnergyKcalMol(), item.convergenceNote());
            if (!enriched.add(replacement)) {
                throw new IOException("authoritative enrichment collapsed distinct quantum records: "
                        + item.identity().evidenceHash());
            }
        }
        for (ClassicalEvidence item : original.classical()) {
            EvidenceIdentity identity = identities.get(item.identity().evidenceHash());
            ClassicalEvidence replacement = new ClassicalEvidence(identity, item.forceFieldId(),
                    item.topologyReference(), item.decomposition(),
                    remapProvenance(item.provenance(), remappedHashes,
                            recovered.get(item.identity().evidenceHash())), item.acceptance());
            if (!enriched.add(replacement)) {
                throw new IOException("authoritative enrichment collapsed distinct classical records: "
                        + item.identity().evidenceHash());
            }
        }
        if (enriched.quantum().size() != original.quantum().size()
                || enriched.classical().size() != original.classical().size()) {
            throw new IOException("authoritative enrichment changed evidence counts");
        }
        return enriched;
    }

    private static Map<String, Map<String, RecoveryAuditEntry>> recoveredByHash(RecoveryAuditReport audit) {
        Map<String, Map<String, RecoveryAuditEntry>> result = new LinkedHashMap<>();
        for (RecoveryAuditEntry entry : audit.entries()) {
            if (entry.recovery().classification() != RecoveryClassification.GENUINELY_UNRECOVERABLE) {
                result.computeIfAbsent(entry.evidenceHash(), ignored -> new LinkedHashMap<>())
                        .put(entry.fieldName(), entry);
            }
        }
        return result;
    }

    private static EvidenceIdentity enrichIdentity(
            EvidenceIdentity identity, Map<String, RecoveryAuditEntry> recovered) {
        if (recovered == null || recovered.isEmpty()) {
            return identity;
        }
        QmProtocol old = identity.protocol();
        QmProtocol protocol = new QmProtocol(
                value(recovered, "PROTOCOL_METHOD_MISSING", old.method()),
                old.basis(), old.dispersion(), old.environment(), old.counterpoise(),
                value(recovered, "PROTOCOL_SOFTWARE_MISSING", old.software()),
                value(recovered, "SOFTWARE_VERSION_MISSING", old.softwareVersion()));
        return new EvidenceIdentity(identity.molecule(), identity.atomMapHash(), identity.geometry(),
                identity.formalCharge(), identity.multiplicity(), identity.calculationType(), protocol,
                identity.constraints(), identity.requestedOutputs());
    }

    private static String value(Map<String, RecoveryAuditEntry> recovered, String field, String fallback) {
        RecoveryAuditEntry entry = recovered.get(field);
        return entry == null ? fallback : entry.recovery().value().orElse(fallback);
    }

    private static EvidenceProvenance remapProvenance(
            EvidenceProvenance provenance,
            Map<String, String> remappedHashes,
            Map<String, RecoveryAuditEntry> recoveries) {
        List<String> parents = provenance.derivedFromEvidenceHashes().stream()
                .map(hash -> remappedHashes.getOrDefault(hash, hash)).toList();
        String note = provenance.note();
        if (recoveries != null && !recoveries.isEmpty()) {
            List<String> receipts = new ArrayList<>();
            recoveries.values().stream().sorted(java.util.Comparator.comparing(RecoveryAuditEntry::fieldName))
                    .forEach(entry -> {
                        FieldSourceProvenance source = entry.recovery().provenance().getFirst();
                        receipts.add(entry.fieldName() + "=" + entry.recovery().value().orElseThrow()
                                + "@" + source.sourcePath() + "#" + source.sha256() + ":" + source.locator());
                    });
            note = note + (note.isBlank() ? "" : "; ")
                    + "authoritative_protocol_recovery[" + String.join("|", receipts) + "]";
        }
        return new EvidenceProvenance(provenance.sourcePath(), provenance.sha256(), provenance.ingestedAt(),
                parents, note);
    }
}
