package totah.lab.protein;

import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@ToString
public class Structure {

    @ToString.Exclude
    private final List<Residue> residues;

    // Fast multi-level lookup index: Chain -> Number -> Residue
    @ToString.Exclude
    private final Map<String, Map<Integer, Residue>> lookupIndex;

    public Structure(List<Residue> residues) {
        this.residues=residues;
        this.lookupIndex = new HashMap<>();
        // Index everything immediately upon creation
        if (residues != null) {
            for (Residue res : residues) {
                // Adjust if your Residue class uses different getter names (e.g., getChain(), getNumber())
                this.lookupIndex
                        .computeIfAbsent(res.getChain(), k -> new HashMap<>())
                        .put(res.getNumber(), res);
            }
        }
    }

    /**
     * Instantly looks up a heavy Residue object using coordinates from a ResidueRef.
     */
    public Residue getResidue(String chain, int number) {
        Map<Integer, Residue> chainMap = lookupIndex.get(chain);
        return chainMap != null ? chainMap.get(number) : null;
    }

    @ToString.Include(name = "atomCount")
    public int getAtomCount() {
        return residues == null ? 0 : residues.size();
    }
}
