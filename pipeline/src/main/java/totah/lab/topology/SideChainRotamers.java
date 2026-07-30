package totah.lab.topology;

import java.util.List;
import java.util.Map;

/**
 * Rotatable chi-bond table for the 20 standard amino acids, used to build
 * PDBQT torsion trees for flexible side chains (Meeko/prepare_flexreceptor
 * conventions).
 *
 * Each entry is an ordered list of heavy-atom bonds from CA outward; the
 * first bond is always CA-CB (chi1). Rings are rigid (PHE/TYR/TRP/HIS stop
 * at CA-CB), terminal methyl/amine/carboxyl rotations are excluded
 * (LYS stops at CD-CE, ARG at CD-NE, MET at CG-SD), hydroxyl/thiol
 * rotations are kept (SER/THR/CYS). PRO, GLY and ALA have none.
 * Deterministic: a static table, no ring detection at runtime.
 */
public final class SideChainRotamers {

    /** A rotatable side-chain bond; parent is closer to CA than child. */
    public record ChiBond(String parent, String child) {}

    private static final Map<String, List<ChiBond>> CHI_BONDS = Map.ofEntries(
            Map.entry("ARG", bonds("CA-CB", "CB-CG", "CG-CD", "CD-NE")),
            Map.entry("ASN", bonds("CA-CB", "CB-CG")),
            Map.entry("ASP", bonds("CA-CB", "CB-CG")),
            Map.entry("CYS", bonds("CA-CB", "CB-SG")),
            Map.entry("GLN", bonds("CA-CB", "CB-CG", "CG-CD")),
            Map.entry("GLU", bonds("CA-CB", "CB-CG", "CG-CD")),
            Map.entry("HIS", bonds("CA-CB")),
            Map.entry("ILE", bonds("CA-CB", "CB-CG1")),
            Map.entry("LEU", bonds("CA-CB", "CB-CG")),
            Map.entry("LYS", bonds("CA-CB", "CB-CG", "CG-CD", "CD-CE")),
            Map.entry("MET", bonds("CA-CB", "CB-CG", "CG-SD")),
            Map.entry("PHE", bonds("CA-CB")),
            Map.entry("PRO", bonds()),
            Map.entry("SER", bonds("CA-CB", "CB-OG")),
            Map.entry("THR", bonds("CA-CB", "CB-OG1")),
            Map.entry("TRP", bonds("CA-CB")),
            Map.entry("TYR", bonds("CA-CB")),
            Map.entry("VAL", bonds("CA-CB")),
            Map.entry("ALA", bonds()),
            Map.entry("GLY", bonds()));

    private SideChainRotamers() {
    }

    private static List<ChiBond> bonds(String... namePairs) {
        List<ChiBond> result = new java.util.ArrayList<>(namePairs.length);
        for (String pair : namePairs) {
            String[] parts = pair.split("-");
            result.add(new ChiBond(parts[0], parts[1]));
        }
        return List.copyOf(result);
    }

    /** True when the residue name is one of the 20 standard amino acids. */
    public static boolean isStandardAminoAcid(String residueName) {
        return CHI_BONDS.containsKey(residueName);
    }

    /** Ordered rotatable chi bonds for a standard amino acid (empty if none). */
    public static List<ChiBond> chiBonds(String residueName) {
        return CHI_BONDS.getOrDefault(residueName, List.of());
    }
}
