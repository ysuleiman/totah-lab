package totah.lab.hephaestus.mutation;

import java.util.Objects;

public record MutationContext(
        AmbiguousCovalentTopologyPolicy ambiguousTopologyPolicy,
        boolean allowExplicitBondBreaking,
        boolean repackNeighbors,
        double neighborRadius) {

    public MutationContext {
        Objects.requireNonNull(ambiguousTopologyPolicy, "ambiguousTopologyPolicy");
        if (!Double.isFinite(neighborRadius) || neighborRadius < 0.0) {
            throw new IllegalArgumentException("neighborRadius must be finite and non-negative");
        }
    }

    public static MutationContext defaults() {
        return new MutationContext(
                AmbiguousCovalentTopologyPolicy.FAIL, false, false, 0.0);
    }
}
