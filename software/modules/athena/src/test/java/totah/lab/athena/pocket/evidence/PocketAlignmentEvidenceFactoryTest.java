package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.AlignmentInitialization;
import totah.lab.athena.pocket.compare.MultiHypothesisPocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketAlignmentEvidenceFactoryTest {

    private static final double TOLERANCE = 1.0e-9;

    private final PocketAlignmentEvidenceFactory factory =
            new PocketAlignmentEvidenceFactory();

    /**
     * Symmetric-cloud fixture (mirrored from
     * {@code MultiHypothesisPocketAlignerTest}): the seeded transform
     * is a symmetry of the cloud, so both hypotheses reach geometry
     * 1.0, and only the seeded frame is sequence-consistent.
     */
    @Test
    void losingHypothesisIsRetainedWithItsRealMetrics() {
        PocketAlignmentResult result = alignSymmetricCloud();

        PocketAlignmentEvidence evidence = factory.create(result);

        assertNotEquals(
                AlignmentInitialization.PCA_ICP,
                evidence.selectedInitialization()
        );

        AlignmentHypothesisEvidence seeded = evidence.sequenceSeeded();

        assertTrue(seeded.available());
        assertTrue(seeded.accepted());
        assertEquals(1.0, seeded.geometrySimilarity(), 1.0e-6);
        assertEquals(3, seeded.sequenceConsistentPairCount());
        assertTrue(
                seeded.residueCorrespondenceCount()
                        >= seeded.sequenceConsistentPairCount()
        );

        // The losing PCA hypothesis keeps its real geometry and its
        // lack of sequence consistency instead of being dropped.
        AlignmentHypothesisEvidence pca = evidence.pcaIcp();

        assertTrue(pca.available());
        assertFalse(pca.accepted());
        assertEquals(1.0, pca.geometrySimilarity(), 1.0e-6);
        assertTrue(
                pca.sequenceConsistentPairCount()
                        < seeded.sequenceConsistentPairCount()
        );

        assertTrue(
                evidence.selectionReason().contains(
                        "sequence consistency"
                ),
                "selectionReason was: " + evidence.selectionReason()
        );
    }

    @Test
    void unavailableSeedHypothesisIsReportedAsUnavailable() {
        PocketPointCloud cloud = cloud(IRREGULAR_CLOUD);

        List<PocketResiduePoint> queryResidues = residues(
                1,
                List.of("ALA", "LEU", "SER", "LYS"),
                IRREGULAR_POSITIONS
        );
        List<PocketResiduePoint> candidateResidues = residues(
                101,
                List.of("ALA", "LEU", "SER", "LYS"),
                IRREGULAR_POSITIONS
        );

        // Identity below the aligner's minimum: no seed is evaluated.
        SequenceAlignment lowIdentity = new SequenceAlignment(
                0.10,
                List.of(
                        pair(1, 101, "ALA", "GLY"),
                        pair(2, 102, "LEU", "GLY"),
                        pair(3, 103, "SER", "GLY"),
                        pair(4, 104, "LYS", "GLY")
                )
        );

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        cloud,
                        cloud,
                        queryResidues,
                        candidateResidues,
                        lowIdentity
                );

        PocketAlignmentEvidence evidence = factory.create(result);

        assertEquals(
                AlignmentInitialization.PCA_ICP,
                evidence.selectedInitialization()
        );
        assertTrue(evidence.pcaIcp().available());
        assertTrue(evidence.pcaIcp().accepted());

        AlignmentHypothesisEvidence seeded = evidence.sequenceSeeded();

        assertFalse(seeded.available());
        assertFalse(seeded.accepted());
        assertEquals(0.0, seeded.geometrySimilarity(), TOLERANCE);
        assertEquals(0, seeded.residueCorrespondenceCount());

        assertTrue(
                evidence.selectionReason().contains(
                        "no usable sequence seed"
                ),
                "selectionReason was: " + evidence.selectionReason()
        );
    }

    private PocketAlignmentResult alignSymmetricCloud() {
        PocketPointCloud cloud = cloud(OCTAHEDRON_CLOUD);

        List<PocketResiduePoint> queryResidues = residues(
                1,
                List.of("ALA", "LEU", "SER", "CYS"),
                List.of(
                        new Point3D(10.0, 0.0, 0.0),
                        new Point3D(0.0, 10.0, 0.0),
                        new Point3D(0.0, 0.0, 10.0),
                        new Point3D(-10.0, 0.0, 0.0)
                )
        );
        List<PocketResiduePoint> candidateResidues = residues(
                11,
                List.of("ALA", "LEU", "SER", "CYS"),
                List.of(
                        new Point3D(0.0, 10.0, 0.0),
                        new Point3D(-10.0, 0.0, 0.0),
                        new Point3D(0.0, 0.0, 10.0),
                        new Point3D(0.0, -10.0, 0.0)
                )
        );

        SequenceAlignment alignment = new SequenceAlignment(
                1.0,
                List.of(
                        pair(1, 11, "ALA", "ALA"),
                        pair(2, 12, "LEU", "LEU"),
                        pair(3, 13, "SER", "SER")
                )
        );

        return new MultiHypothesisPocketAligner().align(
                cloud,
                cloud,
                queryResidues,
                candidateResidues,
                alignment
        );
    }

    private static AlignedResiduePair pair(
            int queryResidueNumber,
            int candidateResidueNumber,
            String queryResidueName,
            String candidateResidueName
    ) {
        return new AlignedResiduePair(
                queryResidueNumber,
                candidateResidueNumber,
                queryResidueName,
                candidateResidueName
        );
    }

    private static List<PocketResiduePoint> residues(
            int firstResidueNumber,
            List<String> names,
            List<Point3D> positions
    ) {
        List<PocketResiduePoint> residues = new ArrayList<>();

        for (int index = 0; index < names.size(); index++) {
            residues.add(new PocketResiduePoint(
                    new ResidueReference(
                            "A",
                            firstResidueNumber + index,
                            ' ',
                            names.get(index)
                    ),
                    positions.get(index),
                    chemistry(names.get(index))
            ));
        }

        return residues;
    }

    private static ResidueChemistry chemistry(String residueName) {
        return switch (residueName) {
            case "CYS" -> ResidueChemistry.CYSTEINE;
            case "SER" -> ResidueChemistry.POLAR;
            case "LYS" -> ResidueChemistry.POSITIVE;
            default -> ResidueChemistry.HYDROPHOBIC;
        };
    }

    private static PocketPointCloud cloud(double[][] coordinates) {
        List<Point3D> points = new ArrayList<>();

        for (double[] coordinate : coordinates) {
            points.add(new Point3D(
                    coordinate[0],
                    coordinate[1],
                    coordinate[2]
            ));
        }

        return new PocketPointCloud(
                points,
                PocketGeometryBasis.ALPHA_SPHERES
        );
    }

    private static final List<Point3D> IRREGULAR_POSITIONS = List.of(
            new Point3D(0.0, 0.0, 0.0),
            new Point3D(10.0, 0.0, 0.0),
            new Point3D(0.0, 6.0, 0.0),
            new Point3D(0.0, 0.0, 3.0)
    );

    private static final double[][] IRREGULAR_CLOUD = {
            {0.0, 0.0, 0.0},
            {10.0, 0.0, 0.0},
            {0.0, 6.0, 0.0},
            {0.0, 0.0, 3.0},
            {8.0, 5.0, 2.0},
            {2.0, 4.0, 6.0},
            {7.0, 1.0, 5.0},
            {3.0, 8.0, 1.0}
    };

    private static final double[][] OCTAHEDRON_CLOUD = {
            {10.0, 0.0, 0.0},
            {-10.0, 0.0, 0.0},
            {0.0, 10.0, 0.0},
            {0.0, -10.0, 0.0},
            {0.0, 0.0, 10.0},
            {0.0, 0.0, -10.0}
    };
}
