package totah.lab.mnemosyne;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Read-only successor admission. Integrity and linkage do not establish scientific truth or trust. */
public final class EvidenceAdmission {
    public static final String METHOD = "mnemosyne-snapshot-admission/1";
    public enum Status { ADMISSIBLE, REPLAY, CONFLICT }
    public enum Reason {
        INVALID_PARENT, INVALID_INCOMING, PARENT_REFERENCE_MISMATCH, INCOMING_REFERENCE_MISMATCH,
        PARENT_DIGEST_MISMATCH, INCOMING_DIGEST_MISMATCH, SNAPSHOT_IDENTITY_CONFLICT,
        WRONG_PARENT, CHRONOLOGY_CONFLICT, MISSING_INHERITED_RECORD, CHANGED_INHERITED_RECORD
    }
    /** Digest of EvidenceExchange.encode(snapshot), not a raw-file digest or an authenticity claim. */
    public record Pin(ScientificReference reference, String sha256) {
        public Pin {
            Objects.requireNonNull(reference).require(ScientificReference.Kind.SNAPSHOT);
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("expected lowercase SHA-256 digest");
        }
    }
    public record Expectation(Pin parent, Pin incoming) {
        public Expectation { Objects.requireNonNull(parent); Objects.requireNonNull(incoming); }
    }
    public record Finding(Reason reason, ScientificReference reference) {
        public Finding { Objects.requireNonNull(reason); Objects.requireNonNull(reference); }
    }
    public record Result(Status status, Expectation expectation, List<Finding> findings, String method) {
        public Result {
            Objects.requireNonNull(status); Objects.requireNonNull(expectation);
            findings = List.copyOf(findings);
            if ((status == Status.CONFLICT) != !findings.isEmpty() || !METHOD.equals(method))
                throw new IllegalArgumentException("inconsistent admission result");
        }
    }

    /** Checks two complete snapshots independently; never selects a branch, merges, or writes. */
    public Result check(EvidenceExchange.Snapshot parent, EvidenceExchange.Snapshot incoming, Expectation expected) {
        Objects.requireNonNull(parent); Objects.requireNonNull(incoming); Objects.requireNonNull(expected);
        var findings = new ArrayList<Finding>();
        var exchange = new EvidenceExchange();
        String parentDigest = digest(exchange, parent, Reason.INVALID_PARENT, findings);
        String incomingDigest = digest(exchange, incoming, Reason.INVALID_INCOMING, findings);
        checkPin(parent, parentDigest, expected.parent(), Reason.PARENT_REFERENCE_MISMATCH,
                Reason.PARENT_DIGEST_MISMATCH, findings);
        checkPin(incoming, incomingDigest, expected.incoming(), Reason.INCOMING_REFERENCE_MISMATCH,
                Reason.INCOMING_DIGEST_MISMATCH, findings);
        if (!findings.isEmpty()) return result(Status.CONFLICT, expected, findings);
        if (parent.manifest().reference().equals(incoming.manifest().reference())) {
            if (parentDigest.equals(incomingDigest)) return result(Status.REPLAY, expected, findings);
            findings.add(new Finding(Reason.SNAPSHOT_IDENTITY_CONFLICT, incoming.manifest().reference()));
            return result(Status.CONFLICT, expected, findings);
        }
        if (!incoming.manifest().parent().filter(parent.manifest().reference()::equals).isPresent())
            findings.add(new Finding(Reason.WRONG_PARENT, incoming.manifest().reference()));
        if (incoming.manifest().createdAt().isBefore(parent.manifest().createdAt()))
            findings.add(new Finding(Reason.CHRONOLOGY_CONFLICT, incoming.manifest().reference()));
        var records = new HashMap<ScientificReference, EvidenceExchange.RecordDigest>();
        incoming.manifest().records().forEach(r -> records.put(r.reference(), r));
        for (var inherited : parent.manifest().records()) {
            var found = records.get(inherited.reference());
            if (found == null) findings.add(new Finding(Reason.MISSING_INHERITED_RECORD, inherited.reference()));
            else if (!found.equals(inherited)) findings.add(new Finding(Reason.CHANGED_INHERITED_RECORD, inherited.reference()));
        }
        return result(findings.isEmpty() ? Status.ADMISSIBLE : Status.CONFLICT, expected, findings);
    }
    private static String digest(EvidenceExchange exchange, EvidenceExchange.Snapshot snapshot,
                                 Reason invalid, List<Finding> findings) {
        try { return EvidenceExchange.sha256(exchange.encode(snapshot)); }
        catch (IOException error) {
            findings.add(new Finding(invalid, snapshot.manifest().reference()));
            return null;
        }
    }
    private static void checkPin(EvidenceExchange.Snapshot snapshot, String digest, Pin pin,
                                 Reason referenceMismatch, Reason digestMismatch, List<Finding> findings) {
        if (!snapshot.manifest().reference().equals(pin.reference())) findings.add(new Finding(referenceMismatch, pin.reference()));
        if (digest != null && !digest.equals(pin.sha256())) findings.add(new Finding(digestMismatch, pin.reference()));
    }
    private static Result result(Status status, Expectation expected, List<Finding> findings) {
        findings.sort(Comparator.comparing((Finding f) -> f.reason().name())
                .thenComparing(f -> f.reference().kind().name()).thenComparing(f -> f.reference().namespace())
                .thenComparing(f -> f.reference().id()).thenComparing(f -> f.reference().version()));
        return new Result(status, expected, findings, METHOD);
    }
}
