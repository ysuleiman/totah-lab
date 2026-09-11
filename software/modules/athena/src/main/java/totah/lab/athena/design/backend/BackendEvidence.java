package totah.lab.athena.design.backend;

import java.util.List;
import java.util.Map;

/** Backend result metadata; backend atom indices are intentionally absent. */
public record BackendEvidence(String backend, String version, String operation,
                              Map<String, String> atomLineage,
                              List<GraphChange> graphChanges, List<String> messages) {
    public BackendEvidence {
        atomLineage = Map.copyOf(atomLineage);
        graphChanges = List.copyOf(graphChanges);
        messages = List.copyOf(messages);
    }
    public record GraphChange(String dimension, String before, String after,
                              Disposition disposition) { }
    public enum Disposition { NONE, BENIGN_NORMALIZATION, UNAUTHORIZED_MEANINGFUL_CHANGE }
}
