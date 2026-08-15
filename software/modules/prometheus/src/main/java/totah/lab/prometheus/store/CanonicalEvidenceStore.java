package totah.lab.prometheus.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EnergyDecomposition;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QuantumEvidence;

/**
 * Content-addressed canonical JSON store for extracted calculation evidence.
 *
 * <p>Source parsing occurs only when the descriptor identifies a generation
 * that is not already present. Normal startup uses {@link #loadCurrent(Path)}
 * and reads Prometheus-owned JSON directly into memory; it does not inspect or
 * recompute the original calculations.
 */
public final class CanonicalEvidenceStore {

    public static final int SCHEMA_VERSION = 1;
    private static final String CURRENT_FILE = "current.json";
    private static final String GENERATIONS = "generations";
    private static final String MANIFEST = "manifest.json";
    private static final String QUANTUM = "quantum";
    private static final String CLASSICAL = "classical";

    private final ObjectMapper mapper;

    public CanonicalEvidenceStore() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    }

    public CompilationResult compileOrLoad(
            Path sourceRoot,
            Path storeRoot,
            EvidenceImportDescriptor descriptor,
            EvidenceImporter importer) throws IOException {

        requireSchema(descriptor);
        Path generation = generationPath(storeRoot, descriptor.generationId());
        if (Files.isRegularFile(generation.resolve(MANIFEST))) {
            LoadedEvidence loaded = loadGeneration(generation);
            verifyDescriptor(descriptor, loaded.manifest());
            writeCurrent(storeRoot, descriptor.generationId());
            return new CompilationResult(loaded.index(), loaded.manifest(), CompilationStatus.LOADED_EXISTING);
        }

        EvidenceBundle extracted = importer.importEvidence(sourceRoot);
        EvidenceStoreManifest manifest = writeGeneration(generation, descriptor, extracted);
        writeCurrent(storeRoot, descriptor.generationId());
        LoadedEvidence loaded = loadGeneration(generation);
        return new CompilationResult(loaded.index(), manifest, CompilationStatus.IMPORTED_NEW_GENERATION);
    }

    /** Loads the current canonical generation without touching the source archive. */
    public LoadedEvidence loadCurrent(Path storeRoot) throws IOException {
        CurrentPointer pointer = mapper.readValue(storeRoot.resolve(CURRENT_FILE).toFile(), CurrentPointer.class);
        return loadGeneration(generationPath(storeRoot, pointer.generationId()));
    }

    public LoadedEvidence loadGeneration(Path generationDirectory) throws IOException {
        EvidenceStoreManifest manifest = mapper.readValue(
                generationDirectory.resolve(MANIFEST).toFile(), EvidenceStoreManifest.class);
        EvidenceBundle bundle = new EvidenceBundle();
        readQuantum(generationDirectory.resolve(QUANTUM), manifest, bundle);
        readClassical(generationDirectory.resolve(CLASSICAL), manifest, bundle);
        if (bundle.quantum().size() != manifest.quantumCount()
                || bundle.classical().size() != manifest.classicalCount()) {
            throw new IOException("canonical evidence counts do not match manifest");
        }
        return new LoadedEvidence(new EvidenceMemoryIndex(bundle), manifest);
    }

    private EvidenceStoreManifest writeGeneration(
            Path generation,
            EvidenceImportDescriptor descriptor,
            EvidenceBundle bundle) throws IOException {

        Files.createDirectories(generation.resolve(QUANTUM));
        Files.createDirectories(generation.resolve(CLASSICAL));
        Map<String, String> checksums = new LinkedHashMap<>();

        for (QuantumEvidence evidence : bundle.quantum().stream()
                .sorted((left, right) -> left.identity().evidenceHash()
                        .compareTo(right.identity().evidenceHash()))
                .toList()) {
            String relative = QUANTUM + "/" + evidence.identity().evidenceHash() + ".json";
            writeRecord(generation.resolve(relative), QuantumRecord.from(evidence), checksums, relative);
        }
        for (ClassicalEvidence evidence : bundle.classical().stream()
                .sorted((left, right) -> left.identity().evidenceHash()
                        .compareTo(right.identity().evidenceHash()))
                .toList()) {
            String relative = CLASSICAL + "/" + evidence.identity().evidenceHash() + ".json";
            writeRecord(generation.resolve(relative), evidence, checksums, relative);
        }

        EvidenceStoreManifest manifest = new EvidenceStoreManifest(
                descriptor,
                Instant.now(),
                bundle.quantum().size(),
                bundle.classical().size(),
                checksums);
        writeNew(generation.resolve(MANIFEST), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
        return manifest;
    }

    private void writeRecord(
            Path path,
            Object record,
            Map<String, String> checksums,
            String relativePath) throws IOException {
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(record);
        writeNew(path, bytes);
        checksums.put(relativePath, sha256(bytes));
    }

    private static void writeNew(Path path, byte[] bytes) throws IOException {
        if (Files.exists(path)) {
            byte[] existing = Files.readAllBytes(path);
            if (!MessageDigest.isEqual(existing, bytes)) {
                throw new IOException("immutable canonical record already exists with different content: " + path);
            }
            return;
        }
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private void writeCurrent(Path storeRoot, String generationId) throws IOException {
        Files.createDirectories(storeRoot);
        Path temporary = Files.createTempFile(storeRoot, "current-", ".json");
        Files.write(temporary,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new CurrentPointer(generationId)),
                StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporary, storeRoot.resolve(CURRENT_FILE),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, storeRoot.resolve(CURRENT_FILE), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void readQuantum(Path directory, EvidenceStoreManifest manifest, EvidenceBundle bundle)
            throws IOException {
        for (Path path : jsonFiles(directory)) {
            verifyChecksum(path, directory.getParent(), manifest);
            bundle.add(mapper.readValue(path.toFile(), QuantumRecord.class).toEvidence());
        }
    }

    private void readClassical(Path directory, EvidenceStoreManifest manifest, EvidenceBundle bundle)
            throws IOException {
        for (Path path : jsonFiles(directory)) {
            verifyChecksum(path, directory.getParent(), manifest);
            bundle.add(mapper.readValue(path.toFile(), ClassicalEvidence.class));
        }
    }

    private static List<Path> jsonFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private static void verifyChecksum(
            Path path,
            Path generation,
            EvidenceStoreManifest manifest) throws IOException {
        String relative = generation.relativize(path).toString();
        String expected = manifest.recordSha256().get(relative);
        if (expected == null || !expected.equals(sha256(Files.readAllBytes(path)))) {
            throw new IOException("canonical evidence checksum mismatch: " + relative);
        }
    }

    private static Path generationPath(Path storeRoot, String generationId) {
        return storeRoot.resolve(GENERATIONS).resolve(generationId);
    }

    private static void requireSchema(EvidenceImportDescriptor descriptor) {
        if (descriptor.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported canonical evidence schema " + descriptor.schemaVersion());
        }
    }

    private static void verifyDescriptor(
            EvidenceImportDescriptor requested,
            EvidenceStoreManifest actual) throws IOException {
        if (!requested.equals(actual.importDescriptor())) {
            throw new IOException("stored generation descriptor does not match requested import");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public enum CompilationStatus {
        LOADED_EXISTING,
        IMPORTED_NEW_GENERATION
    }

    public record CompilationResult(
            EvidenceMemoryIndex index,
            EvidenceStoreManifest manifest,
            CompilationStatus status) {
    }

    public record LoadedEvidence(EvidenceMemoryIndex index, EvidenceStoreManifest manifest) {
    }

    private record CurrentPointer(String generationId) {
    }

    private record QuantumRecord(
            EvidenceIdentity identity,
            EvidenceProvenance provenance,
            ConvergenceStatus convergence,
            EvidenceAcceptanceState acceptance,
            Double energyHartree,
            List<Double> gradientHartreePerBohr,
            List<Double> hessianHartreePerBohr2,
            List<Double> dipoleDebye,
            Double interactionEnergyKcalMol,
            String convergenceNote) {

        private static QuantumRecord from(QuantumEvidence evidence) {
            return new QuantumRecord(
                    evidence.identity(),
                    evidence.provenance(),
                    evidence.convergence(),
                    evidence.acceptance(),
                    evidence.energyHartree().orElse(null),
                    evidence.gradientHartreePerBohr().orElse(null),
                    evidence.hessianHartreePerBohr2().orElse(null),
                    evidence.dipoleDebye().orElse(null),
                    evidence.interactionEnergyKcalMol().orElse(null),
                    evidence.convergenceNote());
        }

        private QuantumEvidence toEvidence() {
            return new QuantumEvidence(
                    identity,
                    provenance,
                    convergence,
                    acceptance,
                    Optional.ofNullable(energyHartree),
                    Optional.ofNullable(gradientHartreePerBohr),
                    Optional.ofNullable(hessianHartreePerBohr2),
                    Optional.ofNullable(dipoleDebye),
                    Optional.ofNullable(interactionEnergyKcalMol),
                    convergenceNote);
        }
    }
}
