package totah.lab.mnemosyne;

import java.util.List;
import java.util.Objects;

/** State-scoped source identity or explicit collection; resolution is an assessment, not an intake gate. */
public record EvidenceSubject(ScientificReference state, String entityKind, String localId,
                              List<EvidenceSubject> members) {
    public EvidenceSubject {
        Objects.requireNonNull(state); ScientificReference.text(entityKind); ScientificReference.text(localId);
        members = List.copyOf(members);
    }
}
