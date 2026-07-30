package totah.lab.pipeline.stage;

import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.charge.LigandChargeAssignmentResult;
import totah.lab.ligand.hydrogen.LigandHydrogenationResult;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.PipelineLigandPreparerFactory;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;

import java.util.Objects;

/**
 * Assigns ligand partial charges using the native charge implementation.
 */
public final class LigandChargeAssignmentStage implements Stage {

    private final LigandPreparer preparer;

    public LigandChargeAssignmentStage() {
        this(null);
    }

    LigandChargeAssignmentStage(LigandPreparer preparer) {
        this.preparer = preparer;
    }

    @Override
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        ClassifiedResidue selected = context.require(ContextKeys.SELECTED_LIGAND);
        LigandHydrogenationResult hydrogenated =
                context.require(ContextKeys.LIGAND_HYDROGENATION_RESULT);
        LigandPreparer active = preparer != null
                ? preparer
                : PipelineLigandPreparerFactory.create(context);
        LigandChargeAssignmentResult result =
                active.assignCharges(selected.residue().getName(), hydrogenated);
        context.put(ContextKeys.LIGAND_CHARGE_ASSIGNMENT_RESULT, result);
    }
}
