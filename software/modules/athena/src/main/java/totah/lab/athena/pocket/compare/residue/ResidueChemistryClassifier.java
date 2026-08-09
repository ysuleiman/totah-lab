package totah.lab.athena.pocket.compare.residue;

import totah.lab.gaia.classification.ResidueCategories;
import totah.lab.gaia.classification.ResidueCategory;
import totah.lab.gaia.structure.Residue;

import java.util.Objects;
import java.util.Set;

/**
 * Classifies amino-acid residues into broad physicochemical classes
 * for pocket residue comparison.
 *
 * <p>Cysteine and glycine are kept as dedicated classes because they
 * often require special treatment during pocket matching.</p>
 */
public final class ResidueChemistryClassifier {

    public ResidueChemistry classify(Residue residue) {
        Objects.requireNonNull(residue, "residue");

        return classifyName(residue.getName());
    }

    /**
     * Classifies a three-letter residue name directly, for callers
     * that work from residue identity rather than structure objects.
     */
    public ResidueChemistry classifyName(String residueName) {
        Objects.requireNonNull(residueName, "residue.name");
        if (residueName.isBlank()) {
            throw new IllegalArgumentException(
                    "Residue name must not be blank"
            );
        }
        Set<ResidueCategory> categories =
                ResidueCategories.classify(residueName);
        if (categories.contains(ResidueCategory.CYSTEINE)) {
            return ResidueChemistry.CYSTEINE;
        }
        if (categories.contains(ResidueCategory.GLYCINE)) {
            return ResidueChemistry.GLYCINE;
        }
        if (categories.contains(ResidueCategory.POSITIVELY_CHARGED)) {
            return ResidueChemistry.POSITIVE;
        }
        if (categories.contains(ResidueCategory.NEGATIVELY_CHARGED)) {
            return ResidueChemistry.NEGATIVE;
        }
        if (categories.contains(ResidueCategory.AROMATIC)) {
            return ResidueChemistry.AROMATIC;
        }
        if (categories.contains(ResidueCategory.HYDROPHOBIC)
                || categories.contains(ResidueCategory.PROLINE)) {
            return ResidueChemistry.HYDROPHOBIC;
        }
        if (categories.contains(ResidueCategory.POLAR)) {
            return ResidueChemistry.POLAR;
        }
        return ResidueChemistry.OTHER;
    }
}
