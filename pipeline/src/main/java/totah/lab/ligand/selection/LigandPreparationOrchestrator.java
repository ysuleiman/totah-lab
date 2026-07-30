package totah.lab.ligand.selection;

import totah.lab.ligand.LigandPreparer;
import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.StructureCleanupResult;

import java.util.Objects;
import java.util.Optional;

/**
 * Selects one cleanup-extracted free ligand and delegates its chemistry.
 */
public final class LigandPreparationOrchestrator {

    private final LigandPreparer ligandPreparer;
    private final LigandSelector selector;

    public LigandPreparationOrchestrator() {
        this(new LigandPreparer(), new LigandSelectionPolicy());
    }

    public LigandPreparationOrchestrator(LigandPreparer ligandPreparer) {
        this(ligandPreparer, new LigandSelectionPolicy());
    }

    public LigandPreparationOrchestrator(
            LigandPreparer ligandPreparer,
            LigandSelectionPolicy selectionPolicy) {
        this.ligandPreparer = Objects.requireNonNull(
                ligandPreparer, "ligandPreparer is null");
        this.selector = new LigandSelector(selectionPolicy);
    }

    public Optional<SelectedLigandPreparation> prepareOnly(
            StructureCleanupResult cleanupResult) {
        return selector.selectOnly(cleanupResult).map(this::prepare);
    }

    public SelectedLigandPreparation prepare(
            StructureCleanupResult cleanupResult,
            LigandSelection selection) {
        return prepare(selector.select(cleanupResult, selection));
    }

    private SelectedLigandPreparation prepare(ClassifiedResidue selected) {
        return new SelectedLigandPreparation(
                selected,
                ligandPreparer.prepare(selected.residue()));
    }
}
