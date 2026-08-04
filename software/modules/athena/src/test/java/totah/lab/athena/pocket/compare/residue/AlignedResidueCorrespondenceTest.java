package totah.lab.athena.pocket.compare.residue;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.CompositePocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignment;
import totah.lab.athena.pocket.compare.PocketComparator;
import totah.lab.athena.pocket.compare.PocketComparisonOptions;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test covering the full path: align two point clouds
 * related by a known rigid transform, apply the recovered transform
 * to the candidate residue points, and establish the residue
 * correspondence against the query.
 */
class AlignedResidueCorrespondenceTest {

    private static final double LOOSE = 1.0e-2;

    private static final double[][] BASE_COORDINATES = {
            {0.0, 0.0, 0.0},
            {10.0, 0.0, 0.0},
            {0.0, 6.0, 0.0},
            {0.0, 0.0, 3.0},
            {8.0, 5.0, 2.0},
            {2.0, 4.0, 6.0},
            {7.0, 1.0, 5.0},
            {3.0, 8.0, 1.0},
            {5.0, 7.0, 4.0}
    };

    private static final String[] RESIDUE_NAMES = {
            "ALA", "PHE", "SER", "LYS", "ASP",
            "CYS", "GLY", "LEU", "ARG"
    };

    // 90 degrees about the z-axis.
    private static final double[][] ROTATION_90_Z = {
            {0.0, -1.0, 0.0},
            {1.0, 0.0, 0.0},
            {0.0, 0.0, 1.0}
    };

    private static final RigidTransform KNOWN_TRANSFORM =
            new RigidTransform(
                    ROTATION_90_Z,
                    new Point3D(3.0, -2.0, 5.0)
            );

    private final PocketComparator comparator =
            new PocketComparator(
                    new CompositePocketAligner(),
                    PocketComparisonOptions.defaults()
            );

    private final PocketResiduePointTransformer transformer =
            new PocketResiduePointTransformer();

    private final ResidueCorrespondenceCalculator calculator =
            new ResidueCorrespondenceCalculator();

    @Test
    void recoversIdenticalCorrespondenceAfterAlignment() {
        List<PocketResiduePoint> queryResidues = baseResidues();

        // Candidate: same residues, coordinates moved by a known
        // rigid transform.
        List<Point3D> movedPositions = KNOWN_TRANSFORM.apply(
                queryResidues
                        .stream()
                        .map(PocketResiduePoint::position)
                        .toList()
        );

        List<PocketResiduePoint> candidateResidues =
                new ArrayList<>();

        for (int index = 0;
             index < queryResidues.size();
             index++) {
            PocketResiduePoint queryPoint = queryResidues.get(index);

            candidateResidues.add(new PocketResiduePoint(
                    queryPoint.reference(),
                    movedPositions.get(index),
                    queryPoint.chemistry()
            ));
        }

        PocketAlignment alignment = comparator.align(
                cloudOf(queryResidues),
                cloudOf(candidateResidues)
        );

        List<PocketResiduePoint> alignedCandidate =
                transformer.transform(
                        candidateResidues,
                        alignment.transform()
                );

        ResidueCorrespondence correspondence = calculator.calculate(
                queryResidues,
                alignedCandidate
        );

        assertEquals(
                queryResidues.size(),
                correspondence.matches().size()
        );
        assertTrue(correspondence.unmatchedQuery().isEmpty());
        assertTrue(correspondence.unmatchedCandidate().isEmpty());

        assertEquals(
                1.0,
                correspondence.matchedFractionQuery(),
                0.0
        );
        assertEquals(
                1.0,
                correspondence.matchedFractionCandidate(),
                0.0
        );
        assertEquals(
                1.0,
                correspondence.identicalFraction(),
                0.0
        );

        for (ResidueMatch match : correspondence.matches()) {
            assertEquals(
                    MatchType.IDENTICAL,
                    match.matchType()
            );
            assertTrue(
                    match.distanceAngstroms() < LOOSE,
                    "matched distance was "
                            + match.distanceAngstroms()
                            + " for "
                            + match.query().reference().residueName()
            );
            assertEquals(
                    match.query().reference().residueName(),
                    match.candidate().reference().residueName()
            );
        }
    }

    private static List<PocketResiduePoint> baseResidues() {
        List<PocketResiduePoint> residues = new ArrayList<>();

        for (int index = 0;
             index < BASE_COORDINATES.length;
             index++) {
            double[] coordinate = BASE_COORDINATES[index];

            residues.add(new PocketResiduePoint(
                    new ResidueReference(
                            "A",
                            index + 1,
                            ' ',
                            RESIDUE_NAMES[index]
                    ),
                    new Point3D(
                            coordinate[0],
                            coordinate[1],
                            coordinate[2]
                    ),
                    chemistryOf(RESIDUE_NAMES[index])
            ));
        }

        return residues;
    }

    private static PocketPointCloud cloudOf(
            List<PocketResiduePoint> residues
    ) {
        return new PocketPointCloud(
                residues
                        .stream()
                        .map(PocketResiduePoint::position)
                        .toList(),
                PocketGeometryBasis.RESIDUE_ATOMS
        );
    }

    private static ResidueChemistry chemistryOf(String residueName) {
        return switch (residueName) {
            case "CYS" -> ResidueChemistry.CYSTEINE;
            case "GLY" -> ResidueChemistry.GLYCINE;
            case "PHE", "TYR", "TRP" -> ResidueChemistry.AROMATIC;
            case "ALA", "VAL", "LEU", "ILE", "MET", "PRO" ->
                    ResidueChemistry.HYDROPHOBIC;
            case "SER", "THR", "ASN", "GLN" -> ResidueChemistry.POLAR;
            case "LYS", "ARG", "HIS" -> ResidueChemistry.POSITIVE;
            case "ASP", "GLU" -> ResidueChemistry.NEGATIVE;
            default -> ResidueChemistry.OTHER;
        };
    }
}
