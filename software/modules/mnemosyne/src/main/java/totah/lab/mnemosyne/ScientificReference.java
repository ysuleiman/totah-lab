package totah.lab.mnemosyne;

import java.util.Objects;

/** Versioned, namespaced reference. Kind distinguishes identity axes; equal values do not merge runs. */
public record ScientificReference(Kind kind, String namespace, String id, String version) {
    public enum Kind {
        ARTIFACT, SOURCE, SUBJECT, ENDPOINT, METHOD, CONTEXT, ACTIVITY, OBSERVATION,
        REVIEW, ASSESSMENT, PROPOSITION, CRITERION, POLICY, AGENT, RECEIPT, REVIEW_CHANGE
    }

    public ScientificReference {
        Objects.requireNonNull(kind, "kind");
        text(namespace); text(id); text(version);
        if (kind == Kind.ARTIFACT && namespace.equals("sha256") && !id.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("invalid SHA-256 artifact reference");
    }

    public ScientificReference require(Kind expected) {
        if (kind != expected) throw new IllegalArgumentException("expected " + expected + ", found " + kind);
        return this;
    }

    static String text(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("nonblank text required");
        return value;
    }
}
