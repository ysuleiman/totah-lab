package totah.lab.ligand;

import totah.lab.chemistry.MolecularGraph;
import totah.lab.ligand.ccd.LigandGraphValidationReport;
import totah.lab.ligand.charge.LigandChargeAssignmentResult;
import totah.lab.ligand.hydrogen.LigandHydrogenationResult;
import totah.lab.ligand.torsion.LigandTorsionTreeResult;
import totah.lab.ligand.typing.LigandAd4TypingResult;

import java.util.Objects;

public record LigandPreparationResult(
        MolecularGraph graph,
        LigandGraphValidationReport graphValidation,
        LigandHydrogenationResult hydrogenation,
        LigandChargeAssignmentResult chargeAssignment,
        LigandAd4TypingResult atomTyping,
        LigandTorsionTreeResult torsionTree,
        String pdbqt) {

    public LigandPreparationResult {
        Objects.requireNonNull(graph, "graph is null");
        Objects.requireNonNull(graphValidation, "graphValidation is null");
        Objects.requireNonNull(hydrogenation, "hydrogenation is null");
        Objects.requireNonNull(chargeAssignment, "chargeAssignment is null");
        Objects.requireNonNull(atomTyping, "atomTyping is null");
        Objects.requireNonNull(torsionTree, "torsionTree is null");
        Objects.requireNonNull(pdbqt, "pdbqt is null");
        if (!graphValidation.valid()) {
            throw new IllegalArgumentException("Prepared ligand graph is not valid");
        }
        if (graph != atomTyping.graph()) {
            throw new IllegalArgumentException(
                    "Final ligand graph must be the AD4-typed graph");
        }
        if (pdbqt.isBlank()) {
            throw new IllegalArgumentException("pdbqt is blank");
        }
    }
}
