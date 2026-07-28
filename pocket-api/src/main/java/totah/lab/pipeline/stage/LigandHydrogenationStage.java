package totah.lab.pipeline.stage;

import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.ccd.CcdLigandGraphResult;
import totah.lab.ligand.hydrogen.LigandHydrogenationResult;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.PipelineLigandPreparerFactory;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;

import java.util.Objects;

/**
 * Adds missing CCD hydrogens and validates ligand valence.
 */
public final class LigandHydrogenationStage implements Stage {

    private final LigandPreparer preparer;

    public LigandHydrogenationStage() {
        this(null);
    }

    LigandHydrogenationStage(LigandPreparer preparer) {
        this.preparer = preparer;
    }

    @Override
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        ClassifiedResidue selected = context.require(ContextKeys.SELECTED_LIGAND);
        CcdLigandGraphResult graph = context.require(ContextKeys.LIGAND_GRAPH_RESULT);
        LigandPreparer active = preparer != null
                ? preparer
                : PipelineLigandPreparerFactory.create(context);
        LigandHydrogenationResult result =
                active.hydrogenate(selected.residue().getName(), graph);
        context.put(ContextKeys.LIGAND_HYDROGENATION_RESULT, result);
    }
}
