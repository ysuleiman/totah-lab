package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/**
 * An evidence channel whose absence semantics cannot be confused with an
 * evaluated empty result. Evaluated collections may be empty and therefore
 * mean "evaluated and found nothing".
 */
public sealed interface EvidenceChannel<T>
        permits EvidenceChannel.Present, EvidenceChannel.Empty,
        EvidenceChannel.NotEvaluated, EvidenceChannel.NotApplicable,
        EvidenceChannel.Failed {

    EvaluationStatus status();

    record Present<T>(
            T value,
            EvidenceOrigin origin,
            EvidenceMethod method
    ) implements EvidenceChannel<T> {
        public Present {
            Objects.requireNonNull(value, "value");
            requirePresentValue(value);
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(method, "method");
            value = immutableValue(value);
        }

        @Override
        public EvaluationStatus status() {
            return EvaluationStatus.PRESENT;
        }
    }

    private static void requirePresentValue(Object value) {
        if (value instanceof java.util.Collection<?> collection
                && collection.isEmpty()) {
            throw new IllegalArgumentException(
                    "Present evidence collection must not be empty; use empty()");
        }
        if (value instanceof java.util.Map<?, ?> map && map.isEmpty()) {
            throw new IllegalArgumentException(
                    "Present evidence map must not be empty; use empty()");
        }
    }

    /** The channel was evaluated successfully and found no evidence. */
    record Empty<T>(
            EvidenceOrigin origin,
            EvidenceMethod method
    ) implements EvidenceChannel<T> {
        public Empty {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(method, "method");
        }

        @Override
        public EvaluationStatus status() {
            return EvaluationStatus.EMPTY;
        }
    }

    record NotEvaluated<T>(String reason) implements EvidenceChannel<T> {
        public NotEvaluated {
            reason = requireReason(reason);
        }

        @Override
        public EvaluationStatus status() {
            return EvaluationStatus.NOT_EVALUATED;
        }
    }

    record NotApplicable<T>(String reason) implements EvidenceChannel<T> {
        public NotApplicable {
            reason = requireReason(reason);
        }

        @Override
        public EvaluationStatus status() {
            return EvaluationStatus.NOT_APPLICABLE;
        }
    }

    record Failed<T>(String failureCode, String reason)
            implements EvidenceChannel<T> {
        public Failed {
            failureCode = requireText(failureCode, "failureCode");
            reason = requireReason(reason);
        }

        @Override
        public EvaluationStatus status() {
            return EvaluationStatus.FAILED;
        }
    }

    static <T> Present<T> present(
            T value, EvidenceOrigin origin, EvidenceMethod method) {
        return new Present<>(value, origin, method);
    }

    static <T> Empty<T> empty(
            EvidenceOrigin origin, EvidenceMethod method) {
        return new Empty<>(origin, method);
    }

    static <T> NotEvaluated<T> notEvaluated(String reason) {
        return new NotEvaluated<>(reason);
    }

    static <T> NotApplicable<T> notApplicable(String reason) {
        return new NotApplicable<>(reason);
    }

    static <T> Failed<T> failed(String code, String reason) {
        return new Failed<>(code, reason);
    }

    static void requireOrigin(
            EvidenceChannel<?> channel, EvidenceOrigin expected, String name) {
        Objects.requireNonNull(channel, name);
        EvidenceOrigin actual = switch (channel) {
            case Present<?> present -> present.origin();
            case Empty<?> empty -> empty.origin();
            default -> null;
        };
        if (actual != null && actual != expected) {
            throw new IllegalArgumentException(name + " must have origin " + expected);
        }
    }

    private static String requireReason(String value) {
        return requireText(value, "reason");
    }

    @SuppressWarnings("unchecked")
    private static <T> T immutableValue(T value) {
        if (value instanceof java.util.List<?> list) {
            return (T) java.util.List.copyOf(list);
        }
        if (value instanceof java.util.Set<?> set) {
            return (T) java.util.Set.copyOf(set);
        }
        if (value instanceof java.util.Map<?, ?> map) {
            return (T) java.util.Map.copyOf(map);
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
