package totah.lab.prometheus.evidence;

import totah.lab.mnemosyne.Observation;
import totah.lab.mnemosyne.ReferenceResolver;
import totah.lab.mnemosyne.ScientificReference;
import totah.lab.prometheus.store.GeneratedEvidenceEntry;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;
import totah.lab.prometheus.store.GeneratedEvidenceRole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Read-only resolution through the existing generated registry; role selection is explicit, never inferred from a projection. */
public final class ScientificReferenceResolution implements ReferenceResolver.Authority<GeneratedEvidenceEntry> {
    private final Path registry;
    private final Observation expected;
    private final GeneratedEvidenceRole role;
    public ScientificReferenceResolution(Path registry, Observation expected, GeneratedEvidenceRole role) {
        this.registry = java.util.Objects.requireNonNull(registry); this.expected = java.util.Objects.requireNonNull(expected);
        this.role = java.util.Objects.requireNonNull(role);
    }
    @Override public boolean supports(ScientificReference requested) {
        return references(expected).stream().anyMatch(r -> r.kind() == requested.kind()
                && r.namespace().equals(requested.namespace()) && r.version().equals(requested.version()));
    }
    @Override public Optional<GeneratedEvidenceEntry> find(ScientificReference requested) throws IOException {
        if (!references(expected).contains(requested) || !Files.isRegularFile(registry.resolve(GeneratedEvidenceRegistry.FILE_NAME)))
            return Optional.empty();
        try {
            // Reopen the domain authority, so payload and artifact checks are repeated rather than trusting a stale in-memory index.
            var entries = new GeneratedEvidenceRegistry(registry).entries().stream()
                    .filter(e -> e.scientificIdentityHash().equals(expected.method().id()) && e.role() == role && e.evidence().isPresent()).toList();
            if (entries.size() > 1) throw new ReferenceResolver.Conflict("ambiguous authoritative entry");
            return entries.stream().findFirst();
        } catch (IOException invalid) { throw new ReferenceResolver.Conflict("generated registry verification failed", invalid); }
    }
    @Override public ScientificReference identityOf(GeneratedEvidenceEntry record, ScientificReference.Kind kind, String namespace) throws IOException {
        var projected = project(record);
        return references(projected).stream().filter(r -> r.kind() == kind && r.namespace().equals(namespace)).findFirst()
                .orElseThrow(() -> new ReferenceResolver.Conflict("domain record has no requested identity axis"));
    }
    @Override public ReferenceResolver.Verification verify(GeneratedEvidenceEntry record) throws IOException {
        var evidence = record.evidence().orElseThrow(() -> new ReferenceResolver.Conflict("entry has no quantum result"));
        var projected = project(record);
        if (record.role() != role || !record.scientificIdentityHash().equals(evidence.identity().evidenceHash())
                || !record.registryKey().equals(role.name().toLowerCase(java.util.Locale.ROOT) + ":" + evidence.identity().evidenceHash()) || !projected.equals(expected))
            throw new ReferenceResolver.Conflict("registry identity/role/projection conflict");
        if (record.artifactBase().isEmpty()) throw new ReferenceResolver.Conflict("no authoritative artifact base");
        var base = Path.of(record.artifactBase().orElseThrow()).toAbsolutePath().normalize();
        var source = Path.of(evidence.provenance().sourcePath()).toAbsolutePath().normalize();
        boolean linked = record.artifacts().stream().anyMatch(a -> a.sha256().equals(evidence.provenance().sha256())
                && base.resolve(a.relativePath()).normalize().equals(source));
        if (!linked || !projected.provenance().artifact().namespace().equals("sha256"))
            throw new ReferenceResolver.Conflict("source artifact is not covered by registry verification");
        return new ReferenceResolver.Verification(List.of(projected.provenance().artifact()),
                "Existing registry reverified payload and raw artifact checksums; receipt/source/specification linkage only, not new scientific validity or reuse permission");
    }
    private Observation project(GeneratedEvidenceEntry entry) throws IOException {
        return ScientificObservationAdapter.energy(entry.evidence().orElseThrow(() -> new ReferenceResolver.Conflict("missing result")),
                expected.reference(), expected.activity());
    }
    private static List<ScientificReference> references(Observation observation) {
        return List.of(observation.provenance().source(), observation.provenance().artifact(), observation.provenance().receipt(),
                observation.method(), observation.context());
    }
}
