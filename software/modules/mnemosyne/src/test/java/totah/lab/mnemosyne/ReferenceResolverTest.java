package totah.lab.mnemosyne;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceResolverTest {
    private static ScientificReference ref(String id, String version) {
        return new ScientificReference(ScientificReference.Kind.SOURCE, "fixture", id, version);
    }
    private record Source(ScientificReference identity, String data) { }
    private ReferenceResolver<Source> resolver(Optional<Source> source, boolean conflict) {
        return new ReferenceResolver<>(new ReferenceResolver.Authority<>() {
            public boolean supports(ScientificReference r) { return r.kind() == ScientificReference.Kind.SOURCE && r.namespace().equals("fixture") && r.version().equals("1"); }
            public Optional<Source> find(ScientificReference r) { return source; }
            public ScientificReference identityOf(Source s, ScientificReference.Kind k, String namespace) { return s.identity(); }
            public ReferenceResolver.Verification verify(Source s) throws ReferenceResolver.Conflict {
                if (conflict) throw new ReferenceResolver.Conflict("source bytes inconsistent");
                return ReferenceResolver.Verification.linkageOnly("record linked; no raw bytes checked");
            }
        });
    }
    @Test void resolutionDoesNotImplyRawByteVerification() {
        var result = resolver(Optional.of(new Source(ref("a", "1"), "value")), false).resolve(ref("a", "1"));
        assertEquals(ReferenceResolver.Status.RESOLVED, result.status());
        assertTrue(result.verification().orElseThrow().verifiedArtifacts().isEmpty());
    }
    @Test void mismatchedReturnedIdentityOrVersionCannotMasqueradeAsResolved() {
        for (var wrong : List.of(ref("other", "1"), ref("a", "2"))) {
            var result = resolver(Optional.of(new Source(wrong, "value")), false).resolve(ref("a", "1"));
            assertEquals(ReferenceResolver.Status.CONFLICTING, result.status()); assertTrue(result.record().isEmpty());
        }
    }
    @Test void unsupportedUnavailableAndConflictingRemainDistinct() {
        assertEquals(ReferenceResolver.Status.UNAVAILABLE, resolver(Optional.empty(), false).resolve(ref("a", "1")).status());
        var resolver = resolver(Optional.of(new Source(ref("a", "1"), "value")), true);
        assertEquals(ReferenceResolver.Status.CONFLICTING, resolver.resolve(ref("a", "1")).status());
        assertEquals(ReferenceResolver.Status.UNSUPPORTED, resolver.resolve(ref("a", "2")).status());
        assertEquals(ReferenceResolver.Status.UNSUPPORTED, resolver.resolve(new ScientificReference(ScientificReference.Kind.SOURCE, "unknown", "a", "1")).status());
    }
}
