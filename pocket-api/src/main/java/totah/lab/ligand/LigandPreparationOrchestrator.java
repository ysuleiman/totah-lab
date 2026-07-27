package totah.lab.ligand;

import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.ResidueDisposition;
import totah.lab.pipeline.cleanup.StructureCleanupResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects one cleanup-extracted free ligand and delegates its chemistry.
 */
public final class LigandPreparationOrchestrator {

    private final LigandPreparer ligandPreparer;
    private final LigandSelectionPolicy selectionPolicy;

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
        this.selectionPolicy = Objects.requireNonNull(
                selectionPolicy, "selectionPolicy is null");
    }

    public Optional<SelectedLigandPreparation> prepareOnly(
            StructureCleanupResult cleanupResult) {
        Objects.requireNonNull(cleanupResult, "cleanupResult is null");
        List<ClassifiedResidue> extracted = cleanupResult.extractedLigands();
        if (extracted.isEmpty()) {
            return Optional.empty();
        }
        if (extracted.size() > 1) {
            throw selectionFailure(
                    LigandSelectionFailure.AMBIGUOUS_SELECTION,
                    "Found " + extracted.size()
                            + " extracted components; select one by residue identity");
        }
        return Optional.of(prepare(extracted.getFirst()));
    }

    public SelectedLigandPreparation prepare(
            StructureCleanupResult cleanupResult,
            LigandSelection selection) {
        Objects.requireNonNull(cleanupResult, "cleanupResult is null");
        Objects.requireNonNull(selection, "selection is null");

        List<ClassifiedResidue> matches = allPrimaryClassifications(cleanupResult).stream()
                .filter(classified -> selection.matches(classified.residue()))
                .toList();
        if (matches.isEmpty()) {
            throw selectionFailure(
                    LigandSelectionFailure.SELECTION_NOT_FOUND,
                    "No cleanup component matches " + selection);
        }
        if (matches.size() > 1) {
            throw selectionFailure(
                    LigandSelectionFailure.AMBIGUOUS_SELECTION,
                    "Multiple cleanup components match " + selection);
        }

        ClassifiedResidue selected = matches.getFirst();
        if (selected.disposition() != ResidueDisposition.EXTRACT_AS_LIGAND) {
            throw selectionFailure(
                    LigandSelectionFailure.NOT_EXTRACTED_AS_LIGAND,
                    selection + " has cleanup disposition " + selected.disposition());
        }
        return prepare(selected);
    }

    private SelectedLigandPreparation prepare(ClassifiedResidue selected) {
        LigandSelectionDecision decision = selectionPolicy.evaluate(selected);
        if (!decision.eligible()) {
            throw selectionFailure(
                    decision.failure(),
                    LigandSelection.from(selected.residue()) + ": " + decision.reason());
        }
        if (selected.disposition() != ResidueDisposition.EXTRACT_AS_LIGAND) {
            throw selectionFailure(
                    LigandSelectionFailure.NOT_EXTRACTED_AS_LIGAND,
                    LigandSelection.from(selected.residue())
                            + " has cleanup disposition " + selected.disposition());
        }
        return new SelectedLigandPreparation(
                selected,
                ligandPreparer.prepare(selected.residue()));
    }

    private List<ClassifiedResidue> allPrimaryClassifications(
            StructureCleanupResult cleanupResult) {
        List<ClassifiedResidue> classified = new ArrayList<>();
        classified.addAll(cleanupResult.receptorResidues());
        classified.addAll(cleanupResult.extractedLigands());
        classified.addAll(cleanupResult.removedWaters());
        classified.addAll(cleanupResult.removedMetals());
        return List.copyOf(classified);
    }

    private LigandSelectionException selectionFailure(
            LigandSelectionFailure failure,
            String detail) {
        return new LigandSelectionException(failure, detail);
    }
}
