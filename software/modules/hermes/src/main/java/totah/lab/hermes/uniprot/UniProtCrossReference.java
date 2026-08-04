package totah.lab.hermes.uniprot;

import java.util.Objects;

/**
 * A named cross-reference to an external database (for example a Pfam
 * family or an InterPro entry). {@code name} may be null when the
 * source database does not report one.
 */
public record UniProtCrossReference(String id, String name) {

    public UniProtCrossReference {
        Objects.requireNonNull(id, "id");
    }
}
