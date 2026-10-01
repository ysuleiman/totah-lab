package totah.lab.mnemosyne;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Injected authoritative lookup. Resolution is linkage; verified bytes are separately and explicitly attributed. */
public final class ReferenceResolver<T> {
    public enum Status { RESOLVED, UNAVAILABLE, CONFLICTING, UNSUPPORTED }
    public record Verification(List<ScientificReference> verifiedArtifacts, String reason) {
        public Verification {
            verifiedArtifacts = List.copyOf(verifiedArtifacts);
            verifiedArtifacts.forEach(r -> r.require(ScientificReference.Kind.ARTIFACT)); ScientificReference.text(reason);
        }
        public static Verification linkageOnly(String reason) { return new Verification(List.of(), reason); }
    }
    public record Result<T>(ScientificReference requested, Status status, Optional<T> record,
                            Optional<Verification> verification, String reason) {
        public Result {
            Objects.requireNonNull(requested); Objects.requireNonNull(status); record = Objects.requireNonNull(record);
            verification = Objects.requireNonNull(verification); ScientificReference.text(reason);
            if ((status == Status.RESOLVED) != record.isPresent() || record.isPresent() != verification.isPresent())
                throw new IllegalArgumentException("only resolved results carry a record and verification detail");
        }
    }
    public interface Authority<T> {
        boolean supports(ScientificReference requested);
        Optional<T> find(ScientificReference requested) throws IOException;
        /** Derive identity from the returned domain content, not by echoing a requested ID/version. */
        ScientificReference identityOf(T record, ScientificReference.Kind kind, String namespace) throws IOException;
        Verification verify(T record) throws IOException;
    }
    public static final class Conflict extends IOException {
        public Conflict(String reason) { super(reason); }
        public Conflict(String reason, Throwable cause) { super(reason, cause); }
    }
    private final Authority<T> authority;
    public ReferenceResolver(Authority<T> authority) { this.authority = Objects.requireNonNull(authority); }
    public Result<T> resolve(ScientificReference requested) {
        Objects.requireNonNull(requested);
        if (!authority.supports(requested)) return absent(requested, Status.UNSUPPORTED, "unsupported kind/namespace/version");
        try {
            var found = authority.find(requested);
            if (found.isEmpty()) return absent(requested, Status.UNAVAILABLE, "authoritative record unavailable");
            var record = found.orElseThrow();
            if (!requested.equals(authority.identityOf(record, requested.kind(), requested.namespace())))
                return absent(requested, Status.CONFLICTING, "returned content identity differs from request");
            return new Result<>(requested, Status.RESOLVED, Optional.of(record), Optional.of(authority.verify(record)),
                    "authoritative linkage resolved; verification detail does not establish scientific truth");
        } catch (Conflict error) { return absent(requested, Status.CONFLICTING, error.getMessage()); }
        catch (NoSuchFileException error) { return absent(requested, Status.UNAVAILABLE, "authoritative file missing: " + error.getFile()); }
        catch (IOException error) { return absent(requested, Status.UNAVAILABLE, "authority I/O failure: " + error.getMessage()); }
        catch (IllegalArgumentException error) { return absent(requested, Status.CONFLICTING, "invalid authoritative content: " + error.getMessage()); }
    }
    private Result<T> absent(ScientificReference requested, Status status, String reason) {
        return new Result<>(requested, status, Optional.empty(), Optional.empty(), reason);
    }
}
