package totah.lab.report.analysis;

import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class PocketAnalysisSupport {

    private static final PocketResidueSelection SELECTION =
            new PocketResidueSelection();

    private PocketAnalysisSupport() {
    }

    static List<ResolvedResidue> resolve(
            Pocket pocket,
            Structure structure) {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(structure, "structure");
        List<ResidueId> unresolved =
                SELECTION.unresolvedResidues(structure, pocket);
        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pocket residue not found: " + unresolved.get(0));
        }
        List<Residue> residues =
                SELECTION.resolvedResidues(structure, pocket);
        List<ResidueId> references = pocket.residues();
        List<ResolvedResidue> resolved = new ArrayList<>(residues.size());
        for (int index = 0; index < residues.size(); index++) {
            resolved.add(new ResolvedResidue(
                    references.get(index).chainId(),
                    residues.get(index)));
        }
        return List.copyOf(resolved);
    }

    record ResolvedResidue(String chainId, Residue residue) {
    }
}
