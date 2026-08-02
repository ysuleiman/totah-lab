package totah.lab.gaia.classification;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Assigns physicochemical categories to amino-acid residue names.
 *
 * <p>A residue can belong to several categories at once (for example PHE is
 * both hydrophobic and aromatic). Residues that match no category are
 * classified as {@link ResidueCategory#OTHER}.</p>
 */
public final class ResidueCategories {

    private static final Set<String> HYDROPHOBIC =
            Set.of("ALA", "VAL", "ILE", "LEU", "MET", "PHE", "TYR", "TRP");
    private static final Set<String> POLAR =
            Set.of("SER", "THR", "ASN", "GLN", "CYS");
    private static final Set<String> POSITIVE =
            Set.of("LYS", "ARG", "HIS");
    private static final Set<String> NEGATIVE = Set.of("ASP", "GLU");
    private static final Set<String> AROMATIC =
            Set.of("PHE", "TYR", "TRP", "HIS");

    private ResidueCategories() {
    }

    public static Set<ResidueCategory> classify(String residueName) {
        Objects.requireNonNull(residueName, "residueName");
        String name = residueName.trim().toUpperCase(Locale.ROOT);
        EnumSet<ResidueCategory> categories =
                EnumSet.noneOf(ResidueCategory.class);
        addIf(categories, HYDROPHOBIC, name, ResidueCategory.HYDROPHOBIC);
        addIf(categories, POLAR, name, ResidueCategory.POLAR);
        addIf(categories, POSITIVE, name,
                ResidueCategory.POSITIVELY_CHARGED);
        addIf(categories, NEGATIVE, name,
                ResidueCategory.NEGATIVELY_CHARGED);
        addIf(categories, AROMATIC, name, ResidueCategory.AROMATIC);
        if ("CYS".equals(name)) {
            categories.add(ResidueCategory.CYSTEINE);
        }
        if ("GLY".equals(name)) {
            categories.add(ResidueCategory.GLYCINE);
        }
        if ("PRO".equals(name)) {
            categories.add(ResidueCategory.PROLINE);
        }
        if (categories.isEmpty()) {
            categories.add(ResidueCategory.OTHER);
        }
        return Set.copyOf(categories);
    }

    private static void addIf(
            Set<ResidueCategory> categories,
            Set<String> names,
            String name,
            ResidueCategory category) {
        if (names.contains(name)) {
            categories.add(category);
        }
    }
}
