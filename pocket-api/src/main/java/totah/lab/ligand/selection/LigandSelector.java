package totah.lab.ligand.selection;

import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.ResidueDisposition;
import totah.lab.pipeline.cleanup.StructureCleanupResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects and validates one ligand candidate without preparing its chemistry.
 */
public final class LigandSelector {

    private final LigandSelectionPolicy selectionPolicy;

    public LigandSelector() {
        this(new LigandSelectionPolicy());
    }

    public LigandSelector(LigandSelectionPolicy selectionPolicy) {
        this.selectionPolicy = Objects.requireNonNull(
                selectionPolicy, "selectionPolicy is null");
    }

    public Optional<ClassifiedResidue> selectOnly(
            StructureCleanupResult cleanupResult) {
        Objects.requireNonNull(cleanupResult, "cleanupResult is null");
        List<ClassifiedResidue> extracted = cleanupResult.extractedLigands();
        if (extracted.isEmpty()) {
            return Optional.empty();
        }
        if (extracted.size() > 1) {
            throw failure(
                    LigandSelectionFailure.AMBIGUOUS_SELECTION,
                    "Found " + extracted.size()
                            + " extracted components; select one by residue identity");
        }
        return Optional.of(validate(extracted.getFirst()));
    }

    public ClassifiedResidue select(
            StructureCleanupResult cleanupResult,
            LigandSelection selection) {
        Objects.requireNonNull(cleanupResult, "cleanupResult is null");
        Objects.requireNonNull(selection, "selection is null");
        List<ClassifiedResidue> matches = allPrimaryClassifications(cleanupResult).stream()
                .filter(classified -> selection.matches(classified.residue()))
                .toList();
        if (matches.isEmpty()) {
            throw failure(
                    LigandSelectionFailure.SELECTION_NOT_FOUND,
                    "No cleanup component matches " + selection);
        }
        if (matches.size() > 1) {
            throw failure(
                    LigandSelectionFailure.AMBIGUOUS_SELECTION,
                    "Multiple cleanup components match " + selection);
        }
        ClassifiedResidue selected = matches.getFirst();
        if (selected.disposition() != ResidueDisposition.EXTRACT_AS_LIGAND) {
            throw failure(
                    LigandSelectionFailure.NOT_EXTRACTED_AS_LIGAND,
                    selection + " has cleanup disposition " + selected.disposition());
        }
        return validate(selected);
    }

    private ClassifiedResidue validate(ClassifiedResidue selected) {
        LigandSelectionDecision decision = selectionPolicy.evaluate(selected);
        if (!decision.eligible()) {
            throw failure(
                    decision.failure(),
                    LigandSelection.from(selected.residue()) + ": " + decision.reason());
        }
        if (selected.disposition() != ResidueDisposition.EXTRACT_AS_LIGAND) {
            throw failure(
                    LigandSelectionFailure.NOT_EXTRACTED_AS_LIGAND,
                    LigandSelection.from(selected.residue())
                            + " has cleanup disposition " + selected.disposition());
        }
        return selected;
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

    private LigandSelectionException failure(
            LigandSelectionFailure failure,
            String detail) {
        return new LigandSelectionException(failure, detail);
    }
}
