package totah.lab.hephaestus.receptor.operation;

import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.AmberTopologyBuilder;
import totah.lab.hephaestus.topology.TopologyBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TopologyBuilderOperation
        implements ReceptorPreparationOperation {

    public static final String TOPOLOGY_BUILD_REPORT_ATTRIBUTE =
            "topology-build-report";

    private final TopologyBuilder topologyBuilder;

    public TopologyBuilderOperation() {
        this(new AmberTopologyBuilder());
    }

    public TopologyBuilderOperation(TopologyBuilder topologyBuilder) {
        this.topologyBuilder = Objects.requireNonNull(
                topologyBuilder,
                "topologyBuilder");
    }

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {

        Objects.requireNonNull(preparedProtein, "preparedProtein");
        Objects.requireNonNull(options, "options");

        if (!options.buildTopology()) {
            return OperationResult.success(preparedProtein);
        }

        requireHydrogenOptimization(preparedProtein);
        Structure structure = preparedProtein.protein().structure();
        if (structure.getResidueCount() == 0) {
            throw new IllegalStateException(
                    "Protein structure contains no residues. Run "
                            + "HydrogenOptimizationOperation first.");
        }

        TopologyBuilder.BuildResult result = topologyBuilder.build(
                structure,
                residueStates(preparedProtein));

        return OperationResult.success(
                preparedProtein
                        .withTopology(result.topology())
                        .withAttribute(
                                TOPOLOGY_BUILD_REPORT_ATTRIBUTE,
                                result.report()));
    }

    private void requireHydrogenOptimization(PreparedProtein protein) {
        if (!protein.attributes().containsKey(
                HydrogenOptimizationOperation
                        .HYDROGEN_OPTIMIZATION_REPORT_ATTRIBUTE)) {
            throw new IllegalStateException(
                    "Hydrogen optimization report is missing. Run "
                            + "HydrogenOptimizationOperation first.");
        }
    }

    private Map<String, ResidueState> residueStates(PreparedProtein protein) {
        Object value = protein.attributes().get(
                ResidueStateAssignmentOperation.RESIDUE_STATES_ATTRIBUTE);
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("Residue states are missing.");
        }

        Map<String, ResidueState> states = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key
                    && entry.getValue() instanceof ResidueState state) {
                states.put(key, state);
            } else {
                throw new IllegalStateException(
                        "Invalid residue-state entry: " + entry);
            }
        }
        return Map.copyOf(states);
    }
}
