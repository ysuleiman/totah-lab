package totah.lab.mettl7.campaign.v2;

import java.util.List;
import java.util.Objects;

/** One required SAM-containing receptor background in the clean v2 campaign. */
public record ReceptorBackground(
        String id,
        Paralog paralog,
        List<String> substitutions) {

    public ReceptorBackground {
        if (Objects.requireNonNull(id, "id").isBlank()) {
            throw new IllegalArgumentException("blank receptor id");
        }
        Objects.requireNonNull(paralog, "paralog");
        substitutions = List.copyOf(Objects.requireNonNull(
                substitutions, "substitutions"));
    }

    public enum Paralog { METTL7A, METTL7B }
}
