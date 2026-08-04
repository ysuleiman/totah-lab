package totah.lab.athena.pocket.compare.residue;

import totah.lab.athena.pocket.selection
        .PocketResidueSelection.ResolvedPocketResidue;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;

/**
 * Converts one resolved pocket residue into its representative
 * spatial and chemical description.
 */
public final class PocketResidueMapper {

    private final ResidueCentroidCalculator centroidCalculator;
    private final ResidueChemistryClassifier chemistryClassifier;

    public PocketResidueMapper() {
        this(
                new ResidueCentroidCalculator(),
                new ResidueChemistryClassifier()
        );
    }

    public PocketResidueMapper(
            ResidueCentroidCalculator centroidCalculator,
            ResidueChemistryClassifier chemistryClassifier
    ) {
        this.centroidCalculator = Objects.requireNonNull(
                centroidCalculator,
                "centroidCalculator"
        );

        this.chemistryClassifier = Objects.requireNonNull(
                chemistryClassifier,
                "chemistryClassifier"
        );
    }

    public PocketResiduePoint map(
            ResolvedPocketResidue resolved
    ) {
        Objects.requireNonNull(resolved, "resolved");

        Residue residue = Objects.requireNonNull(
                resolved.residue(),
                "resolved.residue"
        );

        ResidueId id = Objects.requireNonNull(
                resolved.id(),
                "resolved.id"
        );

        Point3D position =
                centroidCalculator.calculate(residue);

        requireFinite(position, resolved);

        ResidueChemistry chemistry =
                chemistryClassifier.classify(residue);

        ResidueReference reference =
                new ResidueReference(
                        id.chainId(),
                        id.residueNumber(),
                        normalizeInsertionCode(
                                id.insertionCode()
                        ),
                        residue.getName()
                );

        return new PocketResiduePoint(
                reference,
                position,
                chemistry
        );
    }

    private static void requireFinite(
            Point3D position,
            ResolvedPocketResidue resolved
    ) {
        Objects.requireNonNull(
                position,
                "Representative residue position"
        );

        if (!Double.isFinite(position.x())
                || !Double.isFinite(position.y())
                || !Double.isFinite(position.z())) {
            throw new IllegalArgumentException(
                    "Representative position contains non-finite "
                            + "coordinates for residue "
                            + describe(resolved)
                            + ": "
                            + position
            );
        }
    }

    private static String describe(
            ResolvedPocketResidue resolved
    ) {
        ResidueId id = resolved.id();
        Residue residue = resolved.residue();

        return id.chainId()
                + ":"
                + residue.getName()
                + id.residueNumber()
                + formatInsertionCode(
                id.insertionCode()
        );
    }

    private static char normalizeInsertionCode(
            Character insertionCode
    ) {
        if (insertionCode == null
                || Character.isWhitespace(insertionCode)) {
            return ' ';
        }

        return insertionCode;
    }

    private static String formatInsertionCode(
            Character insertionCode
    ) {
        if (insertionCode == null
                || Character.isWhitespace(insertionCode)) {
            return "";
        }

        return Character.toString(insertionCode);
    }
}