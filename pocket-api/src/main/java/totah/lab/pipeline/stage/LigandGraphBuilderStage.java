package totah.lab.pipeline.stage;

import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.ccd.CcdLigandGraphResult;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.PipelineLigandPreparerFactory;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;

import java.util.Objects;

/**
 * Builds and validates the CCD-backed molecular graph.
 */
public final class LigandGraphBuilderStage implements Stage {

    private final LigandPreparer preparer;

    public LigandGraphBuilderStage() {
        this(null);
    }

    LigandGraphBuilderStage(LigandPreparer preparer) {
        this.preparer = preparer;
    }

    @Override
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        ClassifiedResidue selected = context.require(ContextKeys.SELECTED_LIGAND);
        LigandPreparer active = preparer != null
                ? preparer
                : PipelineLigandPreparerFactory.create(context);
        CcdLigandGraphResult result = active.buildGraph(selected.residue());
        context.put(ContextKeys.LIGAND_GRAPH_RESULT, result);
    }
}
