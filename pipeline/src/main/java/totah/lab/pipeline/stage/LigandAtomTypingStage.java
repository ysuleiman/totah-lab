package totah.lab.pipeline.stage;

import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.charge.LigandChargeAssignmentResult;
import totah.lab.ligand.typing.LigandAd4TypingResult;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.PipelineLigandPreparerFactory;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;

import java.util.Objects;

/**
 * Assigns AutoDock4 atom types to the charged ligand graph.
 */
public final class LigandAtomTypingStage implements Stage {

    private final LigandPreparer preparer;

    public LigandAtomTypingStage() {
        this(null);
    }

    LigandAtomTypingStage(LigandPreparer preparer) {
        this.preparer = preparer;
    }

    @Override
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        ClassifiedResidue selected = context.require(ContextKeys.SELECTED_LIGAND);
        LigandChargeAssignmentResult charged =
                context.require(ContextKeys.LIGAND_CHARGE_ASSIGNMENT_RESULT);
        LigandPreparer active = preparer != null
                ? preparer
                : PipelineLigandPreparerFactory.create(context);
        LigandAd4TypingResult result =
                active.assignAtomTypes(selected.residue().getName(), charged);
        context.put(ContextKeys.LIGAND_AD4_TYPING_RESULT, result);
    }
}
