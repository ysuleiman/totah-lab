package totah.lab.mnemosyne;

import java.time.Instant;
import java.util.*;

/** Append-only attributed interpretation, not a replacement for its immutable input records. */
public record EvidenceInterpretation(ScientificReference reference, List<Input> inputs,
                                     ScientificReference evaluator, Map<String,String> configuration,
                                     List<EvidenceSubject> subjects, Status status, Map<String,String> measurements,
                                     List<String> reasons, List<String> limitations,
                                     Optional<ScientificReference> supersedes, Instant recordedAt) {
    public enum Status { SUPPORTED_PRESENT, ABSENT_FALSE, NOT_EVALUATED, UNSUPPORTED, UNKNOWN_INCONCLUSIVE, FAILED }
    public record Input(ScientificReference reference, String sha256) {
        public Input {
            reference.require(ScientificReference.Kind.EVIDENCE_ENVELOPE);
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("input digest required");
        }
    }
    public EvidenceInterpretation {
        reference.require(ScientificReference.Kind.EVIDENCE_INTERPRETATION);
        inputs = List.copyOf(inputs); evaluator.require(ScientificReference.Kind.METHOD);
        configuration = Collections.unmodifiableMap(new TreeMap<>(configuration)); subjects = List.copyOf(subjects);
        Objects.requireNonNull(status); measurements = Collections.unmodifiableMap(new TreeMap<>(measurements));
        reasons = List.copyOf(reasons); limitations = List.copyOf(limitations); supersedes = Objects.requireNonNull(supersedes);
        supersedes.ifPresent(r -> r.require(ScientificReference.Kind.EVIDENCE_INTERPRETATION)); Objects.requireNonNull(recordedAt);
        if (inputs.isEmpty() || subjects.isEmpty() || reasons.isEmpty()) throw new IllegalArgumentException("inputs, subjects and reasons required");
        if (supersedes.filter(reference::equals).isPresent()) throw new IllegalArgumentException("self supersession");
    }
}
