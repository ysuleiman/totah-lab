package totah.lab.pipeline.stage;

import totah.lab.ligand.selection.LigandSelection;
import totah.lab.ligand.selection.LigandSelectionPolicy;
import totah.lab.ligand.selection.LigandSelector;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.StructureCleanupResult;

import java.util.Objects;
import java.util.Optional;

/**
 * Selects one eligible cleanup-extracted ligand for preparation.
 */
public final class LigandSelectionStage implements Stage {

    private final LigandSelector selector;
    private final boolean ligandRequired;

    public LigandSelectionStage() {
        this(false, new LigandSelectionPolicy());
    }

    public LigandSelectionStage(
            boolean ligandRequired,
            LigandSelectionPolicy selectionPolicy) {
        this.ligandRequired = ligandRequired;
        this.selector = new LigandSelector(selectionPolicy);
    }

    @Override
    public void run(PipelineContext context) {
        Objects.requireNonNull(context, "context is null");
        StructureCleanupResult cleanup =
                context.require(ContextKeys.STRUCTURE_CLEANUP_RESULT);
        Object configured = context.get(ContextKeys.LIGAND_SELECTION);
        Optional<ClassifiedResidue> selected;
        if (configured == null) {
            selected = selector.selectOnly(cleanup);
        } else if (configured instanceof LigandSelection selection) {
            selected = Optional.of(selector.select(cleanup, selection));
        } else {
            throw new IllegalArgumentException(
                    ContextKeys.LIGAND_SELECTION + " must be a LigandSelection");
        }
        if (selected.isEmpty()) {
            if (ligandRequired) {
                throw new IllegalStateException(
                        "No eligible ligand was found in the configured ligand input");
            }
            return;
        }
        context.put(ContextKeys.SELECTED_LIGAND, selected.orElseThrow());
    }
}
