package totah.lab.pipeline.stage;

import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AlphaFoldFilterStage implements Stage {

    // A residue survives only if at least one backbone atom (N/CA/C) clears the
    // cutoff. Retained residues keep all atoms; atom-level trimming creates
    // chemically broken residues for downstream Amber/template stages.
    private static final Set<String> BACKBONE_NAMES = Set.of("N", "CA", "C");

    public AlphaFoldFilterStage() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        Object cutoffValue = context.get(ContextKeys.PLDDT_CUTOFF);
        if (cutoffValue == null) {
            // No cutoff configured (e.g. experimental PDB) - no-op
            return;
        }
        double plddtCutoff = parseCutoff(cutoffValue);
        if (plddtCutoff < 0 || plddtCutoff > 100) {
            throw new IllegalArgumentException(
                    "plddtCutoff must be 0-100, got: " + plddtCutoff);
        }
        List<Residue> incomingResidues = (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
        if (incomingResidues.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run StructureCleanupStage first.");
        }
        List<Residue> filteredResidues = new ArrayList<>();
        List<String> droppedResidues = new ArrayList<>();
        for (Residue residue : incomingResidues) {
            boolean hasConfidentBackboneAtom = false;
            for (Atom atom : residue.getAtoms()) {
                if (BACKBONE_NAMES.contains(atom.getName()) && atom.getBFactor() >= plddtCutoff) {
                    hasConfidentBackboneAtom = true;
                    break;
                }
            }

            if (!hasConfidentBackboneAtom) {
                droppedResidues.add(residueLabel(residue));
                continue; // Skip entire residue
            }

            filteredResidues.add(residue);
        }
        if (!droppedResidues.isEmpty()) {
            System.out.println("[AlphaFoldFilter] Dropped " + droppedResidues.size()
                    + " low-confidence residues (no backbone atom above pLDDT cutoff "
                    + plddtCutoff + ")");
        }
        if (filteredResidues.isEmpty()) {
            throw new IllegalStateException("AlphaFold confidence filtering removed every residue; no receptor residues remain.");
        }
        context.put(ContextKeys.PROTEIN_RESIDUES, List.copyOf(filteredResidues));
        context.put(ContextKeys.ALPHAFOLD_CONFIDENCE_REPORT,
                new AlphaFoldConfidenceReport(plddtCutoff, incomingResidues.size(),
                        filteredResidues.size(), droppedResidues));
    }

    private double parseCutoff(Object cutoffValue) {
        if (cutoffValue instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(cutoffValue.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("plddtCutoff must be numeric, got: " + cutoffValue, e);
        }
    }

    private String residueLabel(Residue residue) {
        return residue.getName() + " " + residue.getChain() + ":" + residue.getNumber();
    }
}
