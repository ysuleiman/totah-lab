package totah.lab.hephaestus.receptor.operation;

import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.model.Severity;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.alphafold.AlphaFoldConfidenceReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AlphaFoldFilterOperation
        implements ReceptorPreparationOperation {

    public static final String REPORT_ATTRIBUTE =
            "alphafold-confidence-report";

    private static final Set<String> BACKBONE_ATOM_NAMES =
            Set.of("N", "CA", "C");

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {

        Objects.requireNonNull(
                preparedProtein,
                "preparedProtein");

        Objects.requireNonNull(options, "options");

        Double cutoff = options.plddtCutoff();

        // Experimental structure or filtering disabled.
        if (cutoff == null) {
            return OperationResult.success(preparedProtein);
        }

        validateCutoff(cutoff);

        Protein protein = preparedProtein.protein();
        Structure incomingStructure = protein.structure();

        int incomingResidueCount =
                incomingStructure.getResidueCount();

        if (incomingResidueCount == 0) {
            throw new IllegalStateException(
                    "Protein structure contains no residues. "
                            + "Run structure cleanup first.");
        }

        List<Chain> filteredChains = new ArrayList<>();
        List<String> droppedResidues = new ArrayList<>();

        for (Chain chain : incomingStructure.getChains()) {
            List<Residue> filteredResidues =
                    new ArrayList<>();

            for (Residue residue : chain.residues()) {
                if (hasConfidentBackboneAtom(
                        residue,
                        cutoff)) {

                    // Keep the complete residue and all of its atoms.
                    filteredResidues.add(residue);
                } else {
                    droppedResidues.add(
                            residueLabel(chain, residue));
                }
            }

            if (!filteredResidues.isEmpty()) {
                filteredChains.add(
                        new Chain(
                                chain.id(),
                                filteredResidues));
            }
        }

        Structure filteredStructure =
                new Structure(filteredChains);

        int retainedResidueCount =
                filteredStructure.getResidueCount();

        if (retainedResidueCount == 0) {
            throw new IllegalStateException(
                    "AlphaFold confidence filtering removed every "
                            + "residue; no receptor residues remain.");
        }

        Protein filteredProtein = copyWithStructure(
                protein,
                filteredStructure);

        AlphaFoldConfidenceReport report =
                new AlphaFoldConfidenceReport(
                        cutoff,
                        incomingResidueCount,
                        retainedResidueCount,
                        droppedResidues);

        PreparedProtein updated =
                preparedProtein
                        .withProtein(filteredProtein)
                        .withAttribute(
                                REPORT_ATTRIBUTE,
                                report);

        if (droppedResidues.isEmpty()) {
            return OperationResult.success(updated);
        }

        PreparationIssue issue =
                new PreparationIssue(
                        Severity.INFO,
                        "ALPHAFOLD_LOW_CONFIDENCE_RESIDUES_REMOVED",
                        "Removed "
                                + droppedResidues.size()
                                + " low-confidence residues using "
                                + "a pLDDT cutoff of "
                                + cutoff
                                + ".");

        return new OperationResult<>(
                updated,
                List.of(issue));
    }

    private boolean hasConfidentBackboneAtom(
            Residue residue,
            double cutoff) {

        return residue.getAtoms()
                .stream()
                .filter(Objects::nonNull)
                .filter(atom ->
                        BACKBONE_ATOM_NAMES.contains(
                                normalizeAtomName(atom)))
                .anyMatch(atom ->
                        atom.getBFactor() >= cutoff);
    }

    private String normalizeAtomName(Atom atom) {
        String atomName = atom.getName();

        return atomName == null
                ? ""
                : atomName.trim();
    }

    private Protein copyWithStructure(
            Protein protein,
            Structure structure) {

        return new Protein(
                protein.id(),
                protein.uniProtId().orElse(null),
                protein.name(),
                protein.gene().orElse(null),
                protein.organism().orElse(null),
                protein.function().orElse(null),
                structure);
    }

    private void validateCutoff(double cutoff) {
        if (!Double.isFinite(cutoff)
                || cutoff < 0.0
                || cutoff > 100.0) {

            throw new IllegalArgumentException(
                    "pLDDT cutoff must be between 0 and 100, got: "
                            + cutoff);
        }
    }

    private String residueLabel(
            Chain chain,
            Residue residue) {

        String insertionCode =
                residue.getInsertionCode() == null
                        ? ""
                        : residue.getInsertionCode().toString();

        return residue.getName()
                + " "
                + chain.id()
                + ":"
                + residue.getNumber()
                + insertionCode;
    }
}
