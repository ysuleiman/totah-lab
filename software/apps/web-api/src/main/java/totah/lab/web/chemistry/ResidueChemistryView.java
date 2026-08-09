package totah.lab.web.chemistry;

import java.util.List;

/** API view of canonical residue chemistry and its primary display category. */
public record ResidueChemistryView(
        List<String> categories,
        String primaryCategory,
        String primaryLabel,
        String colorKey
) {
    public ResidueChemistryView {
        categories = List.copyOf(categories);
    }
}
