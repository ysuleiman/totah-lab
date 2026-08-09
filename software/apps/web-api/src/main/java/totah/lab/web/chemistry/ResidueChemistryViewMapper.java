package totah.lab.web.chemistry;

import totah.lab.gaia.classification.ResidueCategories;
import totah.lab.gaia.classification.ResidueCategory;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Maps Gaia's overlapping residue categories to an API presentation view. */
public final class ResidueChemistryViewMapper {

    private static final List<ResidueCategory> PRIMARY_PRECEDENCE = List.of(
            ResidueCategory.CYSTEINE,
            ResidueCategory.AROMATIC,
            ResidueCategory.POSITIVELY_CHARGED,
            ResidueCategory.NEGATIVELY_CHARGED,
            ResidueCategory.POLAR,
            ResidueCategory.HYDROPHOBIC,
            ResidueCategory.GLYCINE,
            ResidueCategory.PROLINE
    );

    private ResidueChemistryViewMapper() {
    }

    public static ResidueChemistryView map(String residueName) {
        if (residueName == null) {
            return new ResidueChemistryView(
                    List.of(), null, null, null);
        }
        Set<ResidueCategory> categories =
                ResidueCategories.classify(residueName);
        List<String> categoryNames = categories.stream()
                .sorted(Comparator.comparingInt(
                        ResidueChemistryViewMapper::displayOrder))
                .map(Enum::name)
                .toList();
        ResidueCategory primary = PRIMARY_PRECEDENCE.stream()
                .filter(categories::contains)
                .findFirst()
                .orElse(null);
        return new ResidueChemistryView(
                categoryNames,
                primary == null ? null : primary.name(),
                primary == null ? null : label(primary),
                primary == null ? null : primary.name()
        );
    }

    private static int displayOrder(ResidueCategory category) {
        int index = PRIMARY_PRECEDENCE.indexOf(category);
        return index < 0 ? PRIMARY_PRECEDENCE.size() : index;
    }

    private static String label(ResidueCategory category) {
        return switch (category) {
            case POSITIVELY_CHARGED -> "Positive";
            case NEGATIVELY_CHARGED -> "Negative";
            case HYDROPHOBIC -> "Hydrophobic";
            case AROMATIC -> "Aromatic";
            case POLAR -> "Polar";
            case CYSTEINE -> "Cysteine";
            case GLYCINE -> "Glycine";
            case PROLINE -> "Proline";
            case OTHER -> "Other";
        };
    }
}
