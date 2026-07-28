package totah.lab.util;

import totah.lab.pocket.ResidueRef;
import totah.lab.pocket.Pocket;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class ResidueSearch {

    private ResidueSearch() {}

    /**
     * Search 1: Find a single specific residue inside a Pocket.
     */
    public static Residue findSingle(Pocket pocket, String chain, int number) {
        Objects.requireNonNull(pocket, "pocket cannot be null");
        for (ResidueRef ref : pocket.getResidueRefs()) {
            if (ref.chain().equals(chain) && ref.number() == number) {
                // Invokes the pocket's native magical resolver
                return pocket.getResidues().stream()
                        .filter(r -> r.getChain().equals(chain) && r.getNumber() == number)
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    /**
     * Search 2: Find multiple specific residues inside a Pocket by name (e.g., "HIS", "ASP").
     */
    public static List<Residue> findByNames(Pocket pocket, Collection<String> targetNames) {
        Objects.requireNonNull(pocket, "pocket cannot be null");
        if (targetNames == null || targetNames.isEmpty()) return List.of();

        List<Residue> found = new ArrayList<>();
        for (Residue res : pocket.getResidues()) {
            if (targetNames.contains(res.getName())) {
                found.add(res);
            }
        }
        return found;
    }

    /**
     * Search 3: Extract a clean subset list from the global Structure by a batch list of references.
     */
    public static List<Residue> findAll(Structure structure, Collection<ResidueRef> refs) {
        Objects.requireNonNull(structure, "structure cannot be null");
        if (refs == null || refs.isEmpty()) return List.of();

        List<Residue> found = new ArrayList<>();
        for (ResidueRef ref : refs) {
            Residue res = structure.getResidue(ref.chain(), ref.number());
            if (res != null) {
                found.add(res);
            }
        }
        return found;
    }

    /**
     * Search 4: Query a sequence range directly out of the main Structure.
     */
    public static List<Residue> findRange(Structure structure, String chain, int start, int end) {
        Objects.requireNonNull(structure, "structure cannot be null");
        List<Residue> found = new ArrayList<>();

        for (int i = start; i <= end; i++) {
            Residue res = structure.getResidue(chain, i);
            if (res != null) {
                found.add(res);
            }
        }
        return found;
    }
}
