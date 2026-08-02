package totah.lab.hephaestus.ligand.operation;

import totah.lab.hephaestus.ligand.LigandPreparationOperation;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.flexibility.LigandFlexibilityModel;
import totah.lab.hephaestus.ligand.flexibility.LigandFlexibilityModelBuilder;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.preparation.OperationResult;

import java.util.Objects;

public final class LigandTorsionOperation implements LigandPreparationOperation {
    private final LigandFlexibilityModelBuilder builder;

    public LigandTorsionOperation() { this(new LigandFlexibilityModelBuilder()); }
    public LigandTorsionOperation(LigandFlexibilityModelBuilder builder) {
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    @Override
    public OperationResult<PreparedLigand> apply(
            PreparedLigand preparedLigand, LigandPreparationOptions options) {
        if (!(preparedLigand.topology() instanceof LigandTopology topology)) {
            throw new IllegalStateException("Ligand CCD topology is required before torsion analysis.");
        }
        var atoms = LigandStructureSupport.singleResidue(preparedLigand.ligand()).getAtoms();
        LigandFlexibilityModel model = builder.build(atoms, topology);
        return OperationResult.success(preparedLigand.withAttribute(
                LigandFlexibilityModel.ATTRIBUTE_KEY, model));
    }
}
