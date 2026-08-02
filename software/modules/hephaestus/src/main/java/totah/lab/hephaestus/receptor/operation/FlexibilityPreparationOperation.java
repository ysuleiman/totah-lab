package totah.lab.hephaestus.receptor.operation;

import totah.lab.hephaestus.flexibility.FlexibilityModel;
import totah.lab.hephaestus.flexibility.FlexibilityModelBuilder;
import totah.lab.hephaestus.flexibility.FlexibilityPreparationConfig;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.topology.ProteinTopology;

import java.util.Objects;

public final class FlexibilityPreparationOperation implements ReceptorPreparationOperation {
    public static final String FLEXIBILITY_MODEL_ATTRIBUTE = "flexibility-model";
    private final FlexibilityModelBuilder builder;

    public FlexibilityPreparationOperation() { this(new FlexibilityModelBuilder()); }
    public FlexibilityPreparationOperation(FlexibilityModelBuilder builder) {
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein protein, ReceptorPreparationOptions options) {
        Objects.requireNonNull(protein, "protein");
        Objects.requireNonNull(options, "options");
        FlexibilityPreparationConfig config = options.flexibilityConfig();
        if (config == null || config.flexibleResidues().isEmpty()) {
            return OperationResult.success(protein.withFlexibility(FlexibilityModel.empty()));
        }
        if (!(protein.topology() instanceof ProteinTopology topology)) {
            throw new IllegalStateException("Protein topology is missing. Run TopologyBuilderOperation first.");
        }
        FlexibilityModel model = builder.build(protein.protein().structure(), topology, config);
        return OperationResult.success(protein.withFlexibility(model)
                .withAttribute(FLEXIBILITY_MODEL_ATTRIBUTE, model));
    }
}
