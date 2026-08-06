package totah.lab.athena.pocket.compare.residue;

import totah.lab.gaia.structure.Residue;

import java.util.Locale;
import java.util.Objects;

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
        String normalized = normalize(residueName);

        return switch (normalized) {
            case "CYS" -> ResidueChemistry.CYSTEINE;
            case "GLY" -> ResidueChemistry.GLYCINE;

            case "PHE", "TYR", "TRP" ->
                    ResidueChemistry.AROMATIC;

            case "ALA", "VAL", "LEU", "ILE", "MET", "PRO" ->
                    ResidueChemistry.HYDROPHOBIC;

            case "SER", "THR", "ASN", "GLN" ->
                    ResidueChemistry.POLAR;

            case "LYS", "ARG", "HIS" ->
                    ResidueChemistry.POSITIVE;

            case "ASP", "GLU" ->
                    ResidueChemistry.NEGATIVE;

            default -> ResidueChemistry.OTHER;
        };
    }

    private static String normalize(String residueName) {
        Objects.requireNonNull(residueName, "residue.name");

        String normalized =
                residueName.trim().toUpperCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Residue name must not be blank"
            );
        }

        return normalized;
    }
}
