package totah.lab.hephaestus.protein.flexibility;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Ordered rotatable chi-bond preparation policy for standard residues. */
public final class StandardResidueChiBonds {
    public record ChiBond(String parentAtomName, String childAtomName) {
        public ChiBond {
            if (parentAtomName == null || parentAtomName.isBlank()) throw new IllegalArgumentException("parentAtomName is blank");
            if (childAtomName == null || childAtomName.isBlank()) throw new IllegalArgumentException("childAtomName is blank");
        }
    }

    private static final Map<String, List<ChiBond>> BONDS = Map.ofEntries(
            Map.entry("ARG", List.of(bond("CA","CB"), bond("CB","CG"), bond("CG","CD"), bond("CD","NE"))),
            Map.entry("ASN", List.of(bond("CA","CB"), bond("CB","CG"))),
            Map.entry("ASP", List.of(bond("CA","CB"), bond("CB","CG"))),
            Map.entry("CYS", List.of(bond("CA","CB"), bond("CB","SG"))),
            Map.entry("GLN", List.of(bond("CA","CB"), bond("CB","CG"), bond("CG","CD"))),
            Map.entry("GLU", List.of(bond("CA","CB"), bond("CB","CG"), bond("CG","CD"))),
            Map.entry("HIS", List.of(bond("CA","CB"))),
            Map.entry("ILE", List.of(bond("CA","CB"), bond("CB","CG1"))),
            Map.entry("LEU", List.of(bond("CA","CB"), bond("CB","CG"))),
            Map.entry("LYS", List.of(bond("CA","CB"), bond("CB","CG"), bond("CG","CD"), bond("CD","CE"))),
            Map.entry("MET", List.of(bond("CA","CB"), bond("CB","CG"), bond("CG","SD"))),
            Map.entry("PHE", List.of(bond("CA","CB"))), Map.entry("PRO", List.of()),
            Map.entry("SER", List.of(bond("CA","CB"), bond("CB","OG"))),
            Map.entry("THR", List.of(bond("CA","CB"), bond("CB","OG1"))),
            Map.entry("TRP", List.of(bond("CA","CB"))), Map.entry("TYR", List.of(bond("CA","CB"))),
            Map.entry("VAL", List.of(bond("CA","CB"))), Map.entry("ALA", List.of()), Map.entry("GLY", List.of()));

    private StandardResidueChiBonds() {}

    public static boolean supports(String residueName) {
        return residueName != null && BONDS.containsKey(normalize(residueName));
    }

    public static List<ChiBond> bondsFor(String residueName) {
        return residueName == null ? List.of() : BONDS.getOrDefault(normalize(residueName), List.of());
    }

    private static String normalize(String residueName) {
        return residueName.trim().toUpperCase(Locale.ROOT);
    }

    private static ChiBond bond(String parent, String child) { return new ChiBond(parent, child); }
}
