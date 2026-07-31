package totah.lab.biohub.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import totah.lab.http.biohub.BiohubAtlasClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads categorized ESM Atlas clusters as research-ready sequence files. */
public final class BiohubAtlasClusterDownloader {

    private static final int CONCURRENT_REQUESTS = 8;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final BiohubAtlasClient client;

    public BiohubAtlasClusterDownloader() {
        this(new BiohubAtlasClient());
    }

    BiohubAtlasClusterDownloader(BiohubAtlasClient client) {
        this.client = client;
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = options(args);
        new BiohubAtlasClusterDownloader().download(
                Path.of(required(options, "clusters")),
                Path.of(required(options, "output"))
        );
    }

    void download(Path selectionFile, Path outputDirectory) throws Exception {
        List<ClusterSelection> selections = objectMapper.readValue(
                selectionFile.toFile(), new TypeReference<>() { }
        );
        Files.createDirectories(outputDirectory);
        Files.copy(selectionFile, outputDirectory.resolve("cluster-selection.json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        StringBuilder combinedFasta = new StringBuilder();
        StringBuilder csv = new StringBuilder(
                "cluster_id,category,representative_hash,member_hash,accession,source,sequence_length\n"
        );
        List<ClusterResult> results = new ArrayList<>();
        for (ClusterSelection selection : selections) {
            BiohubAtlasClient.AtlasCluster cluster = client.getCluster(
                    selection.representativeHash()
            );
            validate(selection, cluster);
            Path clusterDirectory = outputDirectory.resolve(String.format(
                    Locale.ROOT, "cluster-%02d-%s", selection.id(), slug(selection.category())
            ));
            Files.createDirectories(clusterDirectory);
            objectMapper.writeValue(
                    clusterDirectory.resolve("cluster.json").toFile(),
                    cluster.rawMetadata()
            );

            Path fastaPath = clusterDirectory.resolve("sequences.fasta");
            byte[] fastaBytes;
            if (Files.isRegularFile(fastaPath)) {
                fastaBytes = Files.readAllBytes(fastaPath);
            } else {
                fastaBytes = downloadMembers(clusterDirectory, cluster.memberHashes());
                Files.write(fastaPath, fastaBytes);
            }
            List<FastaSequence> sequences = parseFasta(new String(
                    fastaBytes, StandardCharsets.UTF_8
            ));
            if (sequences.size() != cluster.memberCount()) {
                throw new IOException("Cluster " + selection.id() + " expected "
                        + cluster.memberCount() + " sequences but archive contained "
                        + sequences.size());
            }
            for (FastaSequence sequence : sequences) {
                combinedFasta.append('>').append("cluster=")
                        .append(selection.id()).append("|category=")
                        .append(selection.category().replace('|', '_')).append('|')
                        .append(sequence.header()).append('\n')
                        .append(wrap(sequence.sequence())).append('\n');
                String[] header = sequence.header().split("\\|", -1);
                csv.append(selection.id()).append(',')
                        .append(csv(selection.category())).append(',')
                        .append(selection.representativeHash()).append(',')
                        .append(header[0]).append(',')
                        .append(csv(header.length > 1 ? header[1] : "")).append(',')
                        .append(csv(header.length > 2 ? header[2] : "")).append(',')
                        .append(sequence.sequence().length()).append('\n');
            }
            results.add(new ClusterResult(
                    selection.id(), selection.category(), cluster.representativeHash(),
                    cluster.representativeName(), cluster.memberCount(),
                    outputDirectory.relativize(clusterDirectory).toString()
            ));
        }
        Files.writeString(outputDirectory.resolve("all-clusters.fasta"), combinedFasta);
        Files.writeString(outputDirectory.resolve("cluster-members.csv"), csv);
        objectMapper.writeValue(outputDirectory.resolve("manifest.json").toFile(),
                new Manifest("1.0", Instant.now(), results.stream()
                        .mapToInt(ClusterResult::memberCount).sum(), List.copyOf(results)));
    }

    private byte[] downloadMembers(Path clusterDirectory, List<String> hashes)
            throws IOException, InterruptedException {
        Path membersDirectory = clusterDirectory.resolve("members");
        Files.createDirectories(membersDirectory);
        try (var executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            List<Future<?>> futures = new ArrayList<>();
            for (String hash : hashes) {
                Path memberFile = membersDirectory.resolve(hash + ".fasta");
                if (Files.isRegularFile(memberFile)) continue;
                futures.add(executor.submit(() -> {
                    for (int attempt = 1; attempt <= 4; attempt++) {
                        try {
                            BiohubAtlasClient.AtlasProtein protein = client.getProtein(hash);
                            String fasta = ">" + protein.proteinHash() + '|'
                                    + protein.accession() + '|' + protein.source() + '\n'
                                    + wrap(protein.sequence()) + '\n';
                            Files.writeString(memberFile, fasta);
                            return;
                        } catch (IOException | InterruptedException exception) {
                            if (attempt == 4) {
                                throw new MemberDownloadException(hash, exception);
                            }
                            try {
                                Thread.sleep(attempt * 2_000L);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new MemberDownloadException(hash, interrupted);
                            }
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof MemberDownloadException failure) {
                        throw new IOException("Failed to download Atlas member "
                                + failure.hash, failure.getCause());
                    }
                    throw new IOException("Atlas member download failed", cause);
                }
            }
        }
        ByteArrayOutputStream fasta = new ByteArrayOutputStream();
        for (String hash : hashes) {
            fasta.write(Files.readAllBytes(membersDirectory.resolve(hash + ".fasta")));
        }
        return fasta.toByteArray();
    }

    private void validate(
            ClusterSelection selection,
            BiohubAtlasClient.AtlasCluster cluster
    ) throws IOException {
        if (cluster.memberCount() != cluster.memberHashes().size()
                || cluster.memberCount() != selection.expectedMembers()) {
            throw new IOException("Cluster " + selection.id() + " member count mismatch: "
                    + "selection=" + selection.expectedMembers() + ", API="
                    + cluster.memberCount() + ", hashes=" + cluster.memberHashes().size());
        }
    }

    private Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        return Map.copyOf(files);
    }

    private byte[] requiredArchiveFile(Map<String, byte[]> files, String name)
            throws IOException {
        byte[] value = files.get(name);
        if (value == null) {
            throw new IOException("BioHub archive has no " + name);
        }
        return value;
    }

    private List<FastaSequence> parseFasta(String fasta) throws IOException {
        List<FastaSequence> sequences = new ArrayList<>();
        String header = null;
        StringBuilder sequence = new StringBuilder();
        for (String line : fasta.lines().toList()) {
            if (line.startsWith(">")) {
                if (header != null) {
                    sequences.add(new FastaSequence(header, sequence.toString()));
                }
                header = line.substring(1).trim();
                sequence.setLength(0);
            } else if (!line.isBlank()) {
                sequence.append(line.trim());
            }
        }
        if (header != null) {
            sequences.add(new FastaSequence(header, sequence.toString()));
        }
        if (sequences.isEmpty()) {
            throw new IOException("BioHub archive contains no FASTA sequences");
        }
        for (FastaSequence value : sequences) {
            String expectedHash = value.header().split("\\|", 2)[0];
            String actualHash = md5(value.sequence());
            if (!expectedHash.equals(actualHash)) {
                throw new IOException("FASTA sequence hash mismatch: expected "
                        + expectedHash + ", calculated " + actualHash);
            }
        }
        return List.copyOf(sequences);
    }

    private String md5(String sequence) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(sequence.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 is unavailable", exception);
        }
    }

    private String wrap(String sequence) {
        StringBuilder wrapped = new StringBuilder();
        for (int start = 0; start < sequence.length(); start += 80) {
            if (start > 0) wrapped.append('\n');
            wrapped.append(sequence, start, Math.min(start + 80, sequence.length()));
        }
        return wrapped.toString();
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Expected --name=value, got " + arg);
            }
            int separator = arg.indexOf('=');
            result.put(arg.substring(2, separator), arg.substring(separator + 1));
        }
        return Map.copyOf(result);
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing --" + name);
        }
        return value;
    }

    record ClusterSelection(int id, String category, String representativeHash,
                            int expectedMembers) { }
    record FastaSequence(String header, String sequence) { }
    record ClusterResult(int id, String category, String representativeHash,
                         String representativeName, int memberCount,
                         String directory) { }
    record Manifest(String schemaVersion, Instant generatedAt, int totalMembers,
                    List<ClusterResult> clusters) { }

    private static final class MemberDownloadException extends RuntimeException {
        private final String hash;

        private MemberDownloadException(String hash, Exception cause) {
            super(cause);
            this.hash = hash;
        }
    }
}
