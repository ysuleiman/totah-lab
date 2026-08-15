package totah.lab.prometheus.numerics;

import java.util.Map;
import java.util.Objects;

/** Read-only values supplied to a node after its dependencies are evaluated. */
public final class ResolvedDependencies {
    private final Map<String, IntermediateValue> values;

    ResolvedDependencies(Map<String, IntermediateValue> values) {
        this.values = Map.copyOf(values);
    }

    public <T extends IntermediateValue> T require(String nodeId, Class<T> type) {
        Objects.requireNonNull(nodeId, "nodeId"); Objects.requireNonNull(type, "type");
        IntermediateValue value = values.get(nodeId);
        if (value == null) throw new IllegalArgumentException("missing dependency: " + nodeId);
        return type.cast(value);
    }
}
