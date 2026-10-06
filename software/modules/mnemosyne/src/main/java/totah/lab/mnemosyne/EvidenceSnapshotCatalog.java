package totah.lab.mnemosyne;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Append-only local snapshot files. No branch selection, merging, or domain interpretation. */
public final class EvidenceSnapshotCatalog {
    public static final String FORMAT = "mnemosyne-snapshot-catalog/1";
    private static final String SUFFIX = ".snapshot.json";
    private final Path directory;
    private final EvidenceExchange exchange = new EvidenceExchange();
    private final EvidenceAdmission admission = new EvidenceAdmission();

    public enum Status { STORED, REPLAY, CONFLICT }
    public enum Reason { ADMITTED, IDENTICAL_SNAPSHOT, ADMISSION_CONFLICT, PARENT_UNAVAILABLE,
        ROOT_HAS_PARENT, SNAPSHOT_IDENTITY_CONFLICT }
    public record Entry(EvidenceAdmission.Pin pin, Optional<ScientificReference> parent) {
        public Entry { Objects.requireNonNull(pin); parent = Objects.requireNonNull(parent); }
    }
    public record Result(Status status, Reason reason, EvidenceAdmission.Expectation expectation,
                         Optional<EvidenceAdmission.Result> admission) {
        public Result {
            Objects.requireNonNull(status); Objects.requireNonNull(reason); Objects.requireNonNull(expectation);
            admission = Objects.requireNonNull(admission);
            if (status != Status.CONFLICT && (admission.isEmpty()
                    || admission.orElseThrow().status() == EvidenceAdmission.Status.CONFLICT))
                throw new IllegalArgumentException("successful storage requires admission");
        }
    }

    /** The caller supplies an existing dedicated directory; opening creates no files. */
    public EvidenceSnapshotCatalog(Path directory) throws IOException {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        requireDirectory();
    }

    /** Explicit bootstrap for a parentless snapshot; self-admission verifies both identity and digest. */
    public Result seed(EvidenceExchange.Snapshot root, EvidenceAdmission.Pin expected) throws IOException {
        Objects.requireNonNull(root); Objects.requireNonNull(expected);
        var expectation = new EvidenceAdmission.Expectation(expected, expected);
        var decision = admission.check(root, root, expectation);
        if (decision.status() == EvidenceAdmission.Status.CONFLICT)
            return result(Status.CONFLICT, Reason.ADMISSION_CONFLICT, expectation, decision);
        if (root.manifest().parent().isPresent())
            return result(Status.CONFLICT, Reason.ROOT_HAS_PARENT, expectation, decision);
        return publish(root, expectation, decision);
    }

    /** All successors require a stored, pinned parent; callers cannot supply an unpersisted parent. */
    public Result append(EvidenceExchange.Snapshot incoming, EvidenceAdmission.Expectation expected) throws IOException {
        Objects.requireNonNull(incoming); Objects.requireNonNull(expected);
        var parent = find(expected.parent().reference());
        if (parent.isEmpty()) return result(Status.CONFLICT, Reason.PARENT_UNAVAILABLE, expected, null);
        var decision = admission.check(parent.orElseThrow(), incoming, expected);
        if (decision.status() == EvidenceAdmission.Status.CONFLICT)
            return result(Status.CONFLICT, Reason.ADMISSION_CONFLICT, expected, decision);
        return publish(incoming, expected, decision);
    }

    /** Missing is distinct from corrupt or incorrectly pinned content, which raises checked I/O failure. */
    public Optional<EvidenceExchange.Snapshot> read(EvidenceAdmission.Pin expected) throws IOException {
        Objects.requireNonNull(expected);
        var found = find(expected.reference());
        if (found.isPresent() && !pin(found.orElseThrow()).equals(expected))
            throw new IOException("catalog snapshot digest mismatch");
        if (found.isPresent()) for (var envelope : found.orElseThrow().history().envelopes().values()) envelope.verifyArtifact();
        return found;
    }

    /** Inventory only. Sibling parent references remain independent; ordering does not designate a head. */
    public List<Entry> entries() throws IOException {
        requireDirectory();
        var entries = new ArrayList<Entry>();
        try (var paths = Files.newDirectoryStream(directory, "*" + SUFFIX)) {
            for (var path : paths) {
                var snapshot = load(path);
                entries.add(new Entry(pin(snapshot), snapshot.manifest().parent()));
            }
        }
        entries.sort(Comparator.comparing((Entry e) -> e.pin().reference().namespace())
                .thenComparing(e -> e.pin().reference().id()).thenComparing(e -> e.pin().reference().version()));
        return List.copyOf(entries);
    }

    private Result publish(EvidenceExchange.Snapshot incoming, EvidenceAdmission.Expectation expected,
                           EvidenceAdmission.Result decision) throws IOException {
        requireDirectory();
        for (var envelope : incoming.history().envelopes().values()) envelope.verifyArtifact();
        byte[] bytes = exchange.encode(incoming);
        var destination = path(incoming.manifest().reference());
        var existing = find(incoming.manifest().reference());
        if (existing.isPresent()) return existingResult(existing.orElseThrow(), expected, decision);
        // Stage outside the catalog so conflicts/replays never add catalog entries or temporary files.
        // A hard link publishes complete bytes atomically and fails if the identity already exists.
        // Unlike ATOMIC_MOVE, this primitive cannot replace an existing destination in a race.
        Path temporary = Files.createTempFile(directory.getParent(), ".mnemosyne-snapshot-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try { Files.createLink(destination, temporary); }
            catch (FileAlreadyExistsException race) {
                return existingResult(load(destination), expected, decision);
            } catch (UnsupportedOperationException unsupported) {
                throw new IOException("catalog requires atomic non-replacing hard-link publication", unsupported);
            }
            return result(Status.STORED, Reason.ADMITTED, expected, decision);
        } finally {
            Files.deleteIfExists(temporary); // Only this call's private staging file, never a catalog entry.
        }
    }
    private Result existingResult(EvidenceExchange.Snapshot existing, EvidenceAdmission.Expectation expected,
                                  EvidenceAdmission.Result decision) throws IOException {
        return pin(existing).equals(expected.incoming())
                ? result(Status.REPLAY, Reason.IDENTICAL_SNAPSHOT, expected, decision)
                : result(Status.CONFLICT, Reason.SNAPSHOT_IDENTITY_CONFLICT, expected, decision);
    }
    private Optional<EvidenceExchange.Snapshot> find(ScientificReference reference) throws IOException {
        requireDirectory();
        try { return Optional.of(load(path(reference))); }
        catch (NoSuchFileException absent) { return Optional.empty(); }
    }
    private EvidenceExchange.Snapshot load(Path path) throws IOException {
        var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > EvidenceExchange.MAX_BYTES)
            throw new IOException("invalid catalog snapshot file");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(EvidenceExchange.MAX_BYTES + 1); }
        var snapshot = exchange.decode(bytes);
        if (!path.getFileName().equals(path(snapshot.manifest().reference()).getFileName()))
            throw new IOException("catalog path/reference mismatch");
        if (!Arrays.equals(bytes, exchange.encode(snapshot))) throw new IOException("noncanonical catalog snapshot");
        return snapshot;
    }
    private Path path(ScientificReference reference) throws IOException {
        reference.require(ScientificReference.Kind.SNAPSHOT);
        return directory.resolve(EvidenceExchange.sha256(exchange.encodeRecord(reference)) + SUFFIX);
    }
    private EvidenceAdmission.Pin pin(EvidenceExchange.Snapshot snapshot) throws IOException {
        return new EvidenceAdmission.Pin(snapshot.manifest().reference(), EvidenceExchange.sha256(exchange.encode(snapshot)));
    }
    private void requireDirectory() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || directory.getParent() == null)
            throw new IOException("catalog requires an existing non-symlink directory with a parent");
    }
    private static Result result(Status status, Reason reason, EvidenceAdmission.Expectation expected,
                                 EvidenceAdmission.Result decision) {
        return new Result(status, reason, expected, Optional.ofNullable(decision));
    }
}
