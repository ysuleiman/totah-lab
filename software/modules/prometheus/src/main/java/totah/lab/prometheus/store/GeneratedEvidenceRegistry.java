package totah.lab.prometheus.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.execution.RawArtifact;
import totah.lab.prometheus.recovery.ArtifactChecksums;

/**
 * Durable, checksum-verified registry for evidence created after archive import.
 * The JSONL file is replaced atomically, then the in-memory index is replaced;
 * a process crash therefore cannot expose an in-memory-only successful result.
 */
public final class GeneratedEvidenceRegistry {

    public static final String FILE_NAME = "generated-evidence.jsonl";
    private final Path registryFile;
    private final ObjectMapper mapper;
    private volatile Map<String, GeneratedEvidenceEntry> entries;

    public GeneratedEvidenceRegistry(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Files.createDirectories(directory);
        registryFile = directory.resolve(FILE_NAME).toAbsolutePath().normalize();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        entries = Map.copyOf(load());
    }

    public synchronized RegistrationResult register(
            String specificationChecksum,
            QuantumEvidence evidence,
            GeneratedEvidenceRole role,
            Path artifactBase,
            List<RawArtifact> artifacts,
            String note) throws IOException {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(role, "role");
        verifyArtifacts(artifactBase, artifacts);
        String key = key(evidence.identity().evidenceHash(), role);
        GeneratedEvidenceStatus status = evidence.acceptance() == EvidenceAcceptanceState.ACCEPTED
                ? GeneratedEvidenceStatus.ACCEPTED : GeneratedEvidenceStatus.REJECTED;
        String payload = evidencePayload(evidence, role, status);
        GeneratedEvidenceEntry proposed = new GeneratedEvidenceEntry(
                key, specificationChecksum, evidence.identity().evidenceHash(), role, status, Optional.of(evidence),
                Optional.of(artifactBase.toAbsolutePath().normalize().toString()), artifacts,
                Optional.empty(), successfulLifecycle(status),
                sha256(payload), Instant.now(), note);
        GeneratedEvidenceEntry existing = entries.get(key);
        if (existing != null) {
            if (!existing.payloadSha256().equals(proposed.payloadSha256())) {
                throw new IOException("scientific identity already registered with different content: " + key);
            }
            return new RegistrationResult(existing, RegistrationDisposition.REUSED_EXISTING);
        }
        persistWith(proposed);
        return new RegistrationResult(proposed, RegistrationDisposition.REGISTERED_NEW);
    }

    /** Atomically persists one primary result and any auxiliary validation results. */
    public synchronized List<RegistrationResult> registerBatch(
            String specificationChecksum, List<GeneratedEvidenceCandidate> candidates) throws IOException {
        if (candidates.isEmpty()) throw new IllegalArgumentException("generated evidence batch is empty");
        Map<String, GeneratedEvidenceEntry> additions = new LinkedHashMap<>();
        List<RegistrationResult> results = new ArrayList<>();
        for (GeneratedEvidenceCandidate candidate : List.copyOf(candidates)) {
            verifyArtifacts(candidate.artifactBase(), candidate.artifacts());
            QuantumEvidence evidence = candidate.evidence();
            String key = key(evidence.identity().evidenceHash(), candidate.role());
            GeneratedEvidenceStatus status = evidence.acceptance() == EvidenceAcceptanceState.ACCEPTED
                    ? GeneratedEvidenceStatus.ACCEPTED : GeneratedEvidenceStatus.REJECTED;
            String payload = evidencePayload(evidence, candidate.role(), status);
            GeneratedEvidenceEntry proposed = new GeneratedEvidenceEntry(key, specificationChecksum,
                    evidence.identity().evidenceHash(),
                    candidate.role(), status, Optional.of(evidence),
                    Optional.of(candidate.artifactBase().toAbsolutePath().normalize().toString()),
                    candidate.artifacts(), Optional.empty(), successfulLifecycle(status),
                    sha256(payload), Instant.now(), candidate.note());
            GeneratedEvidenceEntry existing = entries.get(key);
            if (existing != null) {
                if (!existing.payloadSha256().equals(proposed.payloadSha256())) {
                    throw new IOException("scientific identity already registered with different content: " + key);
                }
                results.add(new RegistrationResult(existing, RegistrationDisposition.REUSED_EXISTING));
            } else {
                if (additions.putIfAbsent(key, proposed) != null) {
                    throw new IOException("duplicate identity within generated evidence batch: " + key);
                }
                results.add(new RegistrationResult(proposed, RegistrationDisposition.REGISTERED_NEW));
            }
        }
        if (!additions.isEmpty()) persistWith(additions);
        return List.copyOf(results);
    }

    public synchronized GeneratedEvidenceEntry recordFailure(
            String specificationChecksum, String scientificIdentityHash, String note) throws IOException {
        return recordFailure(specificationChecksum, scientificIdentityHash,
                GeneratedFailureClassification.EXECUTION_FAILED, Optional.empty(), List.of(), note);
    }

    public synchronized GeneratedEvidenceEntry recordFailure(
            String specificationChecksum,
            String scientificIdentityHash,
            GeneratedFailureClassification classification,
            Optional<Path> artifactBase,
            List<RawArtifact> artifacts,
            String note) throws IOException {
        String prefix = "failure:" + specificationChecksum + ":";
        long attempt = entries.keySet().stream().filter(key -> key.startsWith(prefix)).count() + 1;
        String key = prefix + attempt;
        if (artifactBase.isPresent()) verifyArtifacts(artifactBase.get(), artifacts);
        String payload = "failure\n" + specificationChecksum + "\n" + scientificIdentityHash + "\n" + note;
        GeneratedEvidenceEntry failure = new GeneratedEvidenceEntry(
                key, specificationChecksum, scientificIdentityHash,
                GeneratedEvidenceRole.PRIMARY, GeneratedEvidenceStatus.FAILED,
                Optional.empty(), artifactBase.map(path -> path.toAbsolutePath().normalize().toString()), artifacts,
                Optional.of(classification),
                List.of(GeneratedLifecycleState.MISSING, GeneratedLifecycleState.AUTHORIZED,
                        GeneratedLifecycleState.RUNNING, GeneratedLifecycleState.FAILED),
                sha256(payload), Instant.now(), note);
        persistWith(failure);
        return failure;
    }

    /** Only primary accepted evidence is eligible for scientific reuse. */
    public Optional<QuantumEvidence> reusable(String scientificIdentityHash) {
        GeneratedEvidenceEntry entry = entries.get(key(scientificIdentityHash, GeneratedEvidenceRole.PRIMARY));
        if (entry == null || entry.status() != GeneratedEvidenceStatus.ACCEPTED) return Optional.empty();
        return entry.evidence();
    }

    public Optional<GeneratedEvidenceEntry> failure(String specificationChecksum) {
        String prefix = "failure:" + specificationChecksum + ":";
        return entries.values().stream().filter(entry -> entry.registryKey().startsWith(prefix))
                .max(Comparator.comparing(GeneratedEvidenceEntry::recordedAt));
    }

    public List<GeneratedEvidenceEntry> entries() {
        return entries.values().stream().sorted(Comparator.comparing(GeneratedEvidenceEntry::registryKey)).toList();
    }

    public Path registryFile() {
        return registryFile;
    }

    private void persistWith(GeneratedEvidenceEntry added) throws IOException {
        persistWith(Map.of(added.registryKey(), added));
    }

    private void persistWith(Map<String, GeneratedEvidenceEntry> additions) throws IOException {
        Map<String, GeneratedEvidenceEntry> updated = new LinkedHashMap<>(entries);
        updated.putAll(additions);
        Path temporary = Files.createTempFile(registryFile.getParent(), "generated-evidence-", ".jsonl");
        List<String> lines = updated.values().stream()
                .sorted(Comparator.comparing(GeneratedEvidenceEntry::registryKey))
                .map(this::serialize).toList();
        Files.writeString(temporary, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        force(temporary);
        try {
            Files.move(temporary, registryFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, registryFile, StandardCopyOption.REPLACE_EXISTING);
        }
        force(registryFile);
        entries = Map.copyOf(updated);
    }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private Map<String, GeneratedEvidenceEntry> load() throws IOException {
        Map<String, GeneratedEvidenceEntry> loaded = new LinkedHashMap<>();
        if (!Files.isRegularFile(registryFile)) return loaded;
        int lineNumber = 0;
        for (String line : Files.readAllLines(registryFile, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) continue;
            GeneratedEvidenceEntry entry;
            try {
                entry = fromJson(mapper.readTree(line));
            } catch (RuntimeException e) {
                throw new IOException("invalid generated evidence at line " + lineNumber, e);
            }
            verifyPayload(entry);
            if (entry.artifactBase().isPresent()) {
                verifyArtifacts(Path.of(entry.artifactBase().get()), entry.artifacts());
            }
            if (loaded.putIfAbsent(entry.registryKey(), entry) != null) {
                throw new IOException("duplicate generated evidence registry key: " + entry.registryKey());
            }
        }
        return loaded;
    }

    private void verifyPayload(GeneratedEvidenceEntry entry) throws IOException {
        String expected = entry.evidence().isPresent()
                ? sha256(evidencePayload(entry.evidence().get(), entry.role(), entry.status()))
                : sha256("failure\n" + entry.specificationChecksum() + "\n"
                        + entry.scientificIdentityHash() + "\n" + entry.note());
        if (!expected.equals(entry.payloadSha256())) {
            throw new IOException("generated evidence payload checksum mismatch: " + entry.registryKey());
        }
    }

    private static void verifyArtifacts(Path base, List<RawArtifact> artifacts) throws IOException {
        Objects.requireNonNull(base, "artifactBase");
        Path normalizedBase = base.toAbsolutePath().normalize();
        for (RawArtifact artifact : List.copyOf(artifacts)) {
            Path resolved = normalizedBase.resolve(artifact.relativePath()).normalize();
            if (!resolved.startsWith(normalizedBase) || !Files.isRegularFile(resolved)
                    || !ArtifactChecksums.sha256(resolved).equals(artifact.sha256())) {
                throw new IOException("generated raw artifact checksum mismatch: " + resolved);
            }
        }
    }

    private String serialize(GeneratedEvidenceEntry entry) {
        try {
            ObjectNodeBuilder json = new ObjectNodeBuilder(mapper);
            json.text("registryKey", entry.registryKey());
            json.text("specificationChecksum", entry.specificationChecksum());
            json.text("scientificIdentityHash", entry.scientificIdentityHash());
            json.text("role", entry.role().name());
            json.text("status", entry.status().name());
            json.text("artifactBase", entry.artifactBase().orElse(null));
            json.value("artifacts", entry.artifacts());
            json.text("failureClassification", entry.failureClassification().map(Enum::name).orElse(null));
            json.value("lifecycle", entry.lifecycle());
            json.text("payloadSha256", entry.payloadSha256());
            json.text("recordedAt", entry.recordedAt().toString());
            json.text("note", entry.note());
            json.value("evidence", entry.evidence().map(QuantumRecord::from).orElse(null));
            return mapper.writeValueAsString(json.node);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private GeneratedEvidenceEntry fromJson(JsonNode node) throws IOException {
        QuantumRecord record = node.path("evidence").isNull() || node.path("evidence").isMissingNode()
                ? null : mapper.treeToValue(node.path("evidence"), QuantumRecord.class);
        return new GeneratedEvidenceEntry(node.path("registryKey").asText(),
                node.path("specificationChecksum").asText(),
                node.path("scientificIdentityHash").asText(),
                GeneratedEvidenceRole.valueOf(node.path("role").asText()),
                GeneratedEvidenceStatus.valueOf(node.path("status").asText()),
                Optional.ofNullable(record).map(QuantumRecord::toEvidence),
                Optional.ofNullable(node.path("artifactBase").isNull() ? null : node.path("artifactBase").asText()),
                mapper.readerForListOf(RawArtifact.class).readValue(node.path("artifacts")),
                Optional.ofNullable(node.path("failureClassification").isNull()
                        || node.path("failureClassification").isMissingNode() ? null
                                : GeneratedFailureClassification.valueOf(node.path("failureClassification").asText())),
                mapper.readerForListOf(GeneratedLifecycleState.class).readValue(node.path("lifecycle")),
                node.path("payloadSha256").asText(), Instant.parse(node.path("recordedAt").asText()),
                node.path("note").asText());
    }

    private String evidencePayload(QuantumEvidence evidence, GeneratedEvidenceRole role,
            GeneratedEvidenceStatus status) {
        try {
            return mapper.writeValueAsString(QuantumRecord.from(evidence)) + "\n" + role + "\n" + status;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String key(String identity, GeneratedEvidenceRole role) {
        return role.name().toLowerCase() + ":" + identity;
    }

    private static List<GeneratedLifecycleState> successfulLifecycle(GeneratedEvidenceStatus status) {
        List<GeneratedLifecycleState> base = new ArrayList<>(List.of(
                GeneratedLifecycleState.MISSING, GeneratedLifecycleState.AUTHORIZED,
                GeneratedLifecycleState.RUNNING, GeneratedLifecycleState.COMPLETED,
                GeneratedLifecycleState.VALIDATED, GeneratedLifecycleState.REGISTERED));
        base.add(status == GeneratedEvidenceStatus.ACCEPTED
                ? GeneratedLifecycleState.REUSABLE : GeneratedLifecycleState.REJECTED);
        return List.copyOf(base);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public enum RegistrationDisposition { REGISTERED_NEW, REUSED_EXISTING }
    public record RegistrationResult(GeneratedEvidenceEntry entry, RegistrationDisposition disposition) { }

    private static final class ObjectNodeBuilder {
        private final com.fasterxml.jackson.databind.node.ObjectNode node;
        private ObjectNodeBuilder(ObjectMapper mapper) { node = mapper.createObjectNode(); }
        private void text(String key, String value) { if (value == null) node.putNull(key); else node.put(key, value); }
        private void value(String key, Object value) { node.set(key, node.objectNode().pojoNode(value)); }
    }

    private record QuantumRecord(
            totah.lab.prometheus.evidence.EvidenceIdentity identity,
            totah.lab.prometheus.evidence.EvidenceProvenance provenance,
            totah.lab.prometheus.evidence.ConvergenceStatus convergence,
            EvidenceAcceptanceState acceptance,
            Double energyHartree,
            List<Double> gradientHartreePerBohr,
            List<Double> hessianHartreePerBohr2,
            List<Double> dipoleDebye,
            Double interactionEnergyKcalMol,
            String convergenceNote) {
        static QuantumRecord from(QuantumEvidence e) {
            return new QuantumRecord(e.identity(), e.provenance(), e.convergence(), e.acceptance(),
                    e.energyHartree().orElse(null), e.gradientHartreePerBohr().orElse(null),
                    e.hessianHartreePerBohr2().orElse(null), e.dipoleDebye().orElse(null),
                    e.interactionEnergyKcalMol().orElse(null), e.convergenceNote());
        }
        QuantumEvidence toEvidence() {
            return new QuantumEvidence(identity, provenance, convergence, acceptance,
                    Optional.ofNullable(energyHartree), Optional.ofNullable(gradientHartreePerBohr),
                    Optional.ofNullable(hessianHartreePerBohr2), Optional.ofNullable(dipoleDebye),
                    Optional.ofNullable(interactionEnergyKcalMol), convergenceNote);
        }
    }
}
