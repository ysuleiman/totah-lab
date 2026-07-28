package totah.lab.pipeline.stage;

import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.torsion.LigandTorsionTreeResult;
import totah.lab.ligand.typing.LigandAd4TypingResult;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.PipelineLigandPreparerFactory;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;

import java.util.Objects;

/**
 * Classifies rotatable bonds and constructs the ligand torsion tree.
 */
public final class LigandTorsionTreeStage implements Stage {

    private final LigandPreparer preparer;

    public LigandTorsionTreeStage() {
        this(null);
    }

    LigandTorsionTreeStage(LigandPreparer preparer) {
        this.preparer = preparer;
    }

    @Override
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        ClassifiedResidue selected = context.require(ContextKeys.SELECTED_LIGAND);
        LigandAd4TypingResult typed =
                context.require(ContextKeys.LIGAND_AD4_TYPING_RESULT);
        LigandPreparer active = preparer != null
                ? preparer
                : PipelineLigandPreparerFactory.create(context);
        LigandTorsionTreeResult result =
                active.buildTorsionTree(selected.residue().getName(), typed);
        context.put(ContextKeys.LIGAND_TORSION_TREE_RESULT, result);
    }
}
