package totah.lab.athena.ligand.selectivity;

import totah.lab.gaia.classification.ResidueCategories;
import totah.lab.gaia.classification.ResidueCategory;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Raw per-position chemistry deltas between the residues of two
 * receptors at one aligned position, computed from gaia
 * {@link ResidueCategories}. Every feature is preserved separately;
 * there is deliberately no collapsed chemistry score.
 *
 * <p>Features are symmetric: swapping A and B flips no flag. The
 * flags are: aromatic gain/loss (aromatic on exactly one side),
 * charge gain/loss (charged on exactly one side), polar-hydrophobic
 * swap (one side polar-only, the other hydrophobic-only), and the
 * proline/glycine special case (backbone-special residue involved in
 * a non-identical substitution). A substitution is conservative when
 * none of the flags fire and the two residues still share at least
 * one category.
 */
public record SubstitutionChemistry(
        Set<ResidueCategory> categoriesA,
        Set<ResidueCategory> categoriesB,
        boolean identical,
        boolean conservative,
        boolean aromaticGainLoss,
        boolean chargeGainLoss,
        boolean polarHydrophobicSwap,
        boolean prolineGlycineSpecial,
        SubstitutionClass substitutionClass
) {

    public SubstitutionChemistry {
        categoriesA = Set.copyOf(
                Objects.requireNonNull(categoriesA, "categoriesA")
        );
        categoriesB = Set.copyOf(
                Objects.requireNonNull(categoriesB, "categoriesB")
        );
        Objects.requireNonNull(substitutionClass, "substitutionClass");
    }

    public static SubstitutionChemistry between(
            String residueA,
            String residueB
    ) {
        Objects.requireNonNull(residueA, "residueA");
        Objects.requireNonNull(residueB, "residueB");

        Set<ResidueCategory> categoriesA =
                ResidueCategories.classify(residueA);
        Set<ResidueCategory> categoriesB =
                ResidueCategories.classify(residueB);

        boolean identical = normalize(residueA)
                .equals(normalize(residueB));

        boolean aromaticGainLoss =
                categoriesA.contains(ResidueCategory.AROMATIC)
                        != categoriesB.contains(ResidueCategory.AROMATIC);

        boolean chargeGainLoss = isCharged(categoriesA)
                != isCharged(categoriesB);

        boolean polarHydrophobicSwap =
                (isPolarOnly(categoriesA) && isHydrophobicOnly(categoriesB))
                        || (isPolarOnly(categoriesB)
                        && isHydrophobicOnly(categoriesA));

        boolean prolineGlycineSpecial = !identical
                && (isBackboneSpecial(categoriesA)
                || isBackboneSpecial(categoriesB));

        boolean sharedCategory = categoriesA.stream()
                .anyMatch(categoriesB::contains);

        boolean conservative = !identical
                && !aromaticGainLoss
                && !chargeGainLoss
                && !polarHydrophobicSwap
                && !prolineGlycineSpecial
                && sharedCategory;

        SubstitutionClass substitutionClass = identical
                ? SubstitutionClass.IDENTICAL
                : conservative
                ? SubstitutionClass.CONSERVATIVE
                : aromaticGainLoss || chargeGainLoss
                ? SubstitutionClass.RADICAL
                : SubstitutionClass.MODERATE;

        return new SubstitutionChemistry(
                categoriesA,
                categoriesB,
                identical,
                conservative,
                aromaticGainLoss,
                chargeGainLoss,
                polarHydrophobicSwap,
                prolineGlycineSpecial,
                substitutionClass
        );
    }

    private static boolean isCharged(Set<ResidueCategory> categories) {
        return categories.contains(ResidueCategory.POSITIVELY_CHARGED)
                || categories.contains(ResidueCategory.NEGATIVELY_CHARGED);
    }

    private static boolean isPolarOnly(Set<ResidueCategory> categories) {
        return categories.contains(ResidueCategory.POLAR)
                && !categories.contains(ResidueCategory.HYDROPHOBIC);
    }

    private static boolean isHydrophobicOnly(
            Set<ResidueCategory> categories
    ) {
        return categories.contains(ResidueCategory.HYDROPHOBIC)
                && !categories.contains(ResidueCategory.POLAR);
    }

    private static boolean isBackboneSpecial(
            Set<ResidueCategory> categories
    ) {
        return categories.contains(ResidueCategory.PROLINE)
                || categories.contains(ResidueCategory.GLYCINE);
    }

    private static String normalize(String residueName) {
        return residueName.trim().toUpperCase(Locale.ROOT);
    }
}
