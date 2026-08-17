package totah.lab.gaia.graph;

import totah.lab.gaia.classification.ResidueCategories;
import totah.lab.gaia.classification.ResidueCategory;
import totah.lab.gaia.structure.Residue;

import java.util.Objects;
import java.util.Set;

/** Intrinsic residue chemistry with explicit availability. */
public record ResidueChemistry(
        ResidueChemistryStatus status,
        Set<ResidueCategory> categories) {

    public ResidueChemistry {
        Objects.requireNonNull(status, "status");
        categories = Set.copyOf(
                Objects.requireNonNull(categories, "categories"));
        if (status == ResidueChemistryStatus.NOT_AVAILABLE
                && !categories.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unavailable chemistry cannot contain categories");
        }
    }

    public static ResidueChemistry from(Residue residue) {
        Objects.requireNonNull(residue, "residue");
        if (!ResidueCategories.isStandardAminoAcid(residue.getName())) {
            return new ResidueChemistry(
                    ResidueChemistryStatus.NOT_AVAILABLE,
                    Set.of());
        }
        return new ResidueChemistry(
                ResidueChemistryStatus.AVAILABLE,
                ResidueCategories.classify(residue.getName()));
    }

    public boolean isAvailable() {
        return status == ResidueChemistryStatus.AVAILABLE;
    }

    public boolean contains(ResidueCategory category) {
        Objects.requireNonNull(category, "category");
        return categories.contains(category);
    }
}
