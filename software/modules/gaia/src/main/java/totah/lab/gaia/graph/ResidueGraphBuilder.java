package totah.lab.gaia.graph;

import totah.lab.gaia.structure.Structure;

import java.util.Objects;

/** Builder for immutable structure-backed residue graphs. */
public final class ResidueGraphBuilder {

    private final Structure structure;
    private SequencePolicy sequencePolicy =
            SequencePolicy.EXPLICIT_BONDS_ONLY;

    ResidueGraphBuilder(Structure structure) {
        this.structure = Objects.requireNonNull(structure, "structure");
    }

    public ResidueGraphBuilder sequencePolicy(
            SequencePolicy sequencePolicy) {

        this.sequencePolicy = Objects.requireNonNull(
                sequencePolicy,
                "sequencePolicy");
        return this;
    }

    public ResidueGraph build() {
        return new ResidueGraph(structure, sequencePolicy);
    }
}
