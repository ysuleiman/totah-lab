package totah.lab.athena.pocket.compare;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryAssessment;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryScorer;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.athena.sequence.SequenceResidue;
import totah.lab.gaia.geometry.Point3D;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiHypothesisPocketAlignerTest {

    private static final double TOLERANCE = 1.0e-9;

    /**
     * Reference validation target (analysis/mettl7-correspondence):
     * production PCA+ICP aligns METTL7A pocket 32 onto METTL7B pocket 3
     * with geometry ~0.263 but 0/27 sequence-consistent residue pairs
     * and chemistry ~0.193; a sequence-seeded Kabsch reaches equivalent
     * geometry (~0.265) with 31/31 sequence-consistent pairs and
     * chemistry ~0.823.
     */
    @Test
    void mettl7aPocket32VsMettl7bPocket3SelectsTheSequenceSeed() {
        PocketPointCloud queryCloud = new PocketPointCloud(
                points("/mettl7/query_alpha_spheres.csv"),
                PocketGeometryBasis.ALPHA_SPHERES
        );
        PocketPointCloud candidateCloud = new PocketPointCloud(
                points("/mettl7/candidate_alpha_spheres.csv"),
                PocketGeometryBasis.ALPHA_SPHERES
        );
        List<PocketResiduePoint> queryResidues =
                residues("/mettl7/query_residues.csv");
        List<PocketResiduePoint> candidateResidues =
                residues("/mettl7/candidate_residues.csv");

        SequenceAlignment sequenceAlignment =
                new NeedlemanWunschSequenceAligner().align(
                        sequence("/mettl7/query_sequence.csv"),
                        sequence("/mettl7/candidate_sequence.csv")
                );

        assertTrue(
                sequenceAlignment.identity()
                        >= MultiHypothesisPocketAligner
                                .MINIMUM_SEQUENCE_IDENTITY,
                "fixture identity was " + sequenceAlignment.identity()
        );

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        sequenceAlignment
                );

        // The seeded hypothesis wins.
        assertTrue(result.sequenceSeedAvailable());
        assertFalse(result.sequenceSeedDegenerate());
        assertNotEquals(
                AlignmentInitialization.PCA_ICP,
                result.initialization()
        );
        assertEquals(31, result.seedPairCount());
        assertEquals(
                31,
                result.sequenceConsistentCorrespondenceCount()
        );

        ResidueChemistryAssessment chemistry = new ResidueChemistryScorer()
                .assess(result.correspondence(), Set.of());

        assertTrue(
                chemistry.chemistrySimilarity() > 0.80,
                "chemistrySimilarity was "
                        + chemistry.chemistrySimilarity()
        );
        assertTrue(
                chemistry.compatibleMatchedFraction() > 0.80,
                "compatibleMatchedFraction was "
                        + chemistry.compatibleMatchedFraction()
        );
        assertTrue(
                chemistry.spatialReplacementFraction() < 0.20,
                "spatialReplacementFraction was "
                        + chemistry.spatialReplacementFraction()
        );
        assertEquals(
                0.265,
                result.comparison().geometrySimilarity(),
                0.05
        );

        // The PCA hypothesis is retained diagnostically with its known
        // wrong-frame result.
        assertEquals(2, result.hypotheses().size());

        SeededAlignmentEvaluation pca = result.hypotheses().get(0);

        assertEquals(
                AlignmentInitialization.PCA_ICP,
                pca.initialization()
        );
        assertEquals(
                0,
                pca.sequenceConsistentCorrespondenceCount()
        );
        assertEquals(27, pca.correspondence().matches().size());
        assertEquals(
                0.263,
                pca.comparison().geometrySimilarity(),
                0.05
        );

        ResidueChemistryAssessment pcaChemistry =
                new ResidueChemistryScorer().assess(
                        pca.correspondence(),
                        Set.of()
                );

        assertTrue(
                pcaChemistry.chemistrySimilarity() < 0.30,
                "PCA chemistrySimilarity was "
                        + pcaChemistry.chemistrySimilarity()
        );
    }

    @Test
    void unrelatedProteinsFallBackToPca() {
        PocketPointCloud queryCloud = cloud(IRREGULAR_CLOUD);
        PocketPointCloud candidateCloud = cloud(IRREGULAR_CLOUD);

        List<PocketResiduePoint> queryResidues = residues(
                1,
                List.of("ALA", "LEU", "SER", "LYS"),
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(10.0, 0.0, 0.0),
                        new Point3D(0.0, 6.0, 0.0),
                        new Point3D(0.0, 0.0, 3.0)
                )
        );
        List<PocketResiduePoint> candidateResidues = residues(
                101,
                List.of("ALA", "LEU", "SER", "LYS"),
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(10.0, 0.0, 0.0),
                        new Point3D(0.0, 6.0, 0.0),
                        new Point3D(0.0, 0.0, 3.0)
                )
        );

        // Four aligned pairs, but only one identical: identity 0.25 is
        // NOT met when below the threshold.
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
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        lowIdentity
                );

        assertPcaOnly(result, false);
    }

    @Test
    void fewerThanThreeSeedPairsFallsBackToPca() {
        PocketPointCloud queryCloud = cloud(IRREGULAR_CLOUD);
        PocketPointCloud candidateCloud = cloud(IRREGULAR_CLOUD);

        List<PocketResiduePoint> queryResidues = residues(
                1,
                List.of("ALA", "LEU"),
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(10.0, 0.0, 0.0)
                )
        );
        List<PocketResiduePoint> candidateResidues = residues(
                101,
                List.of("ALA", "LEU"),
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(10.0, 0.0, 0.0)
                )
        );

        SequenceAlignment alignment = new SequenceAlignment(
                1.0,
                List.of(
                        pair(1, 101, "ALA", "ALA"),
                        pair(2, 102, "LEU", "LEU")
                )
        );

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        alignment
                );

        assertPcaOnly(result, false);
    }

    @Test
    void collinearSeedIsDegenerateAndFallsBackToPca() {
        PocketPointCloud queryCloud = cloud(IRREGULAR_CLOUD);
        PocketPointCloud candidateCloud = cloud(IRREGULAR_CLOUD);

        List<PocketResiduePoint> queryResidues = residues(
                1,
                List.of("ALA", "LEU", "SER"),
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(10.0, 0.0, 0.0),
                        new Point3D(0.0, 6.0, 0.0)
                )
        );

        // All candidate seed points on one line: no unique frame.
        List<PocketResiduePoint> candidateResidues = residues(
                101,
                List.of("ALA", "LEU", "SER"),
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(1.0, 0.0, 0.0),
                        new Point3D(2.0, 0.0, 0.0)
                )
        );

        SequenceAlignment alignment = new SequenceAlignment(
                1.0,
                List.of(
                        pair(1, 101, "ALA", "ALA"),
                        pair(2, 102, "LEU", "LEU"),
                        pair(3, 103, "SER", "SER")
                )
        );

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        alignment
                );

        assertPcaOnly(result, true);
        assertTrue(result.sequenceSeedAvailable());
    }

    @Test
    void geometricallyPoorSeedIsRejected() {
        // Query and candidate clouds are the identical 3x3x3 grid
        // (spacing 13). The sequence seed pairs query residues with
        // candidate residues two grid spacings away, so the seeded
        // Kabsch translates the candidate cloud by -26: two of three
        // grid planes land in empty space. ICP refinement converges to
        // a midway local minimum that is still far outside the
        // geometry gate, so the retained seeded hypothesis is rejected
        // and production PCA+ICP (identity frame, geometry 1.0) wins —
        // sequence evidence never rescues a geometrically rejected
        // seed.
        PocketPointCloud queryCloud = cloud(GRID_CLOUD);
        PocketPointCloud candidateCloud = cloud(GRID_CLOUD);

        List<PocketResiduePoint> queryResidues = residues(
                1,
                List.of("ALA", "LEU", "SER"),
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(13.0, 0.0, 0.0),
                        new Point3D(0.0, 13.0, 0.0)
                )
        );
        List<PocketResiduePoint> candidateResidues = residues(
                101,
                List.of("ALA", "LEU", "SER"),
                List.of(
                        new Point3D(26.0, 0.0, 0.0),
                        new Point3D(39.0, 0.0, 0.0),
                        new Point3D(26.0, 13.0, 0.0)
                )
        );

        SequenceAlignment alignment = new SequenceAlignment(
                1.0,
                List.of(
                        pair(1, 101, "ALA", "ALA"),
                        pair(2, 102, "LEU", "LEU"),
                        pair(3, 103, "SER", "SER")
                )
        );

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        alignment
                );

        assertEquals(
                AlignmentInitialization.PCA_ICP,
                result.initialization()
        );
        assertTrue(result.sequenceSeedAvailable());
        assertFalse(result.sequenceSeedDegenerate());
        assertEquals(2, result.hypotheses().size());
        assertFalse(result.hypotheses().get(1).geometryAcceptable());
    }

    @Test
    void equalGeometryPrefersHigherSequenceConsistency() {
        // Regular octahedron: the seeded transform (90 degrees about
        // z) is a symmetry of the cloud, so both hypotheses have
        // geometry 1.0. Only the seeded frame matches the
        // sequence-aligned residues onto each other.
        PocketPointCloud queryCloud = cloud(OCTAHEDRON_CLOUD);
        PocketPointCloud candidateCloud = cloud(OCTAHEDRON_CLOUD);

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

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        alignment
                );

        assertNotEquals(
                AlignmentInitialization.PCA_ICP,
                result.initialization()
        );
        assertEquals(3, result.sequenceConsistentCorrespondenceCount());
        assertEquals(
                1.0,
                result.comparison().geometrySimilarity(),
                1.0e-6
        );

        SeededAlignmentEvaluation pca = result.hypotheses().get(0);

        assertEquals(
                1.0,
                pca.comparison().geometrySimilarity(),
                1.0e-6
        );
        assertTrue(
                pca.sequenceConsistentCorrespondenceFraction()
                        < result.sequenceConsistentCorrespondenceFraction()
        );
    }

    @Test
    void tiedGeometryAndSequenceConsistencyPrefersHigherChemistry() {
        // Cloud symmetric under 180 degrees about z. The candidate
        // seed residues C1/C2 already sit on the query positions (so
        // the PCA frame matches them), while C3 is the rotated image
        // of Q3: the seeded Kabsch is exactly that symmetry rotation.
        // Both frames are sequence-consistent for every match; the
        // seeded frame additionally matches the SER pair, raising the
        // chemistry above the conservative-only PCA frame.
        PocketPointCloud queryCloud = cloud(Z_SYMMETRIC_CLOUD);
        PocketPointCloud candidateCloud = cloud(Z_SYMMETRIC_CLOUD);

        List<PocketResiduePoint> queryResidues = residues(
                1,
                List.of("ALA", "LEU", "SER"),
                List.of(
                        new Point3D(0.0, 0.0, -5.0),
                        new Point3D(0.0, 0.0, 5.0),
                        new Point3D(8.0, 0.0, 0.0)
                )
        );
        List<PocketResiduePoint> candidateResidues = residues(
                11,
                List.of("VAL", "LEU", "SER"),
                List.of(
                        new Point3D(0.0, 0.0, -5.0),
                        new Point3D(0.0, 0.0, 5.0),
                        new Point3D(-8.0, 0.0, 0.0)
                )
        );

        SequenceAlignment alignment = new SequenceAlignment(
                2.0 / 3.0,
                List.of(
                        pair(1, 11, "ALA", "VAL"),
                        pair(2, 12, "LEU", "LEU"),
                        pair(3, 13, "SER", "SER")
                )
        );

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        alignment
                );

        SeededAlignmentEvaluation pca = result.hypotheses().get(0);
        SeededAlignmentEvaluation seeded = result.hypotheses().get(1);

        assertEquals(
                1.0,
                pca.comparison().geometrySimilarity(),
                1.0e-6
        );
        assertEquals(
                pca.sequenceConsistentCorrespondenceFraction(),
                seeded.sequenceConsistentCorrespondenceFraction(),
                TOLERANCE
        );
        assertNotEquals(
                AlignmentInitialization.PCA_ICP,
                result.initialization()
        );

        ResidueChemistryScorer scorer = new ResidueChemistryScorer();

        double pcaChemistry = scorer.assess(
                pca.correspondence(),
                Set.of()
        ).chemistrySimilarity();
        double seededChemistry = scorer.assess(
                result.correspondence(),
                Set.of()
        ).chemistrySimilarity();

        assertTrue(
                seededChemistry > pcaChemistry,
                "seeded chemistry " + seededChemistry
                        + " should exceed PCA chemistry " + pcaChemistry
        );
    }

    @Test
    void perfectTieKeepsPca() {
        // Identical clouds and identical residue sets with a fully
        // aligned sequence: both hypotheses recover the identity
        // frame, so the tie-breaking falls through to hypothesis A.
        PocketPointCloud queryCloud = cloud(IRREGULAR_CLOUD);
        PocketPointCloud candidateCloud = cloud(IRREGULAR_CLOUD);

        List<Point3D> positions = List.of(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(10.0, 0.0, 0.0),
                new Point3D(0.0, 6.0, 0.0),
                new Point3D(0.0, 0.0, 3.0)
        );
        List<String> names = List.of("ALA", "LEU", "SER", "LYS");

        List<PocketResiduePoint> queryResidues =
                residues(1, names, positions);
        List<PocketResiduePoint> candidateResidues =
                residues(11, names, positions);

        SequenceAlignment alignment = new SequenceAlignment(
                1.0,
                List.of(
                        pair(1, 11, "ALA", "ALA"),
                        pair(2, 12, "LEU", "LEU"),
                        pair(3, 13, "SER", "SER"),
                        pair(4, 14, "LYS", "LYS")
                )
        );

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        alignment
                );

        assertEquals(
                AlignmentInitialization.PCA_ICP,
                result.initialization()
        );
        assertTrue(result.sequenceSeedAvailable());
        assertFalse(result.sequenceSeedDegenerate());
        assertEquals(2, result.hypotheses().size());
        assertTrue(result.hypotheses().get(1).geometryAcceptable());
    }

    @Test
    void strictlyImprovingIcpRefinementIsRetained() {
        // Octahedron cloud again: the seeded transform (90 degrees
        // about z) is a cloud symmetry. The candidate seed residues
        // carry a small jitter, so the seeded Kabsch approximates the
        // symmetry with a residual error that ICP then removes —
        // strictly improving the mean bidirectional distance, so the
        // refined hypothesis is retained and wins on sequence
        // consistency.
        PocketPointCloud queryCloud = cloud(OCTAHEDRON_CLOUD);
        PocketPointCloud candidateCloud = cloud(OCTAHEDRON_CLOUD);

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
                        new Point3D(0.0, 10.3, 0.0),
                        new Point3D(-10.0, 0.0, 0.2),
                        new Point3D(0.0, 0.0, 10.0),
                        new Point3D(0.25, -10.0, 0.0)
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

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        alignment
                );

        assertEquals(
                AlignmentInitialization.SEQUENCE_SEEDED_KABSCH_ICP,
                result.initialization()
        );
        assertEquals(3, result.sequenceConsistentCorrespondenceCount());
    }

    private void assertPcaOnly(
            PocketAlignmentResult result,
            boolean expectDegenerate
    ) {
        assertEquals(
                AlignmentInitialization.PCA_ICP,
                result.initialization()
        );
        assertEquals(expectDegenerate, result.sequenceSeedDegenerate());
        assertEquals(1, result.hypotheses().size());
        assertEquals(0, result.seedPairCount());
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

    private static List<Point3D> points(String resource) {
        List<Point3D> points = new ArrayList<>();

        for (String line : readLines(resource)) {
            String[] columns = line.split(",");
            points.add(new Point3D(
                    Double.parseDouble(columns[0]),
                    Double.parseDouble(columns[1]),
                    Double.parseDouble(columns[2])
            ));
        }

        return points;
    }

    private static List<PocketResiduePoint> residues(String resource) {
        List<PocketResiduePoint> residues = new ArrayList<>();

        for (String line : readLines(resource)) {
            String[] columns = line.split(",");
            residues.add(new PocketResiduePoint(
                    new ResidueReference(
                            "A",
                            Integer.parseInt(columns[0]),
                            ' ',
                            columns[1]
                    ),
                    new Point3D(
                            Double.parseDouble(columns[3]),
                            Double.parseDouble(columns[4]),
                            Double.parseDouble(columns[5])
                    ),
                    ResidueChemistry.valueOf(columns[2])
            ));
        }

        return residues;
    }

    private static List<SequenceResidue> sequence(String resource) {
        List<SequenceResidue> sequence = new ArrayList<>();

        for (String line : readLines(resource)) {
            String[] columns = line.split(",");
            sequence.add(new SequenceResidue(
                    Integer.parseInt(columns[0]),
                    columns[1]
            ));
        }

        return sequence;
    }

    private static List<String> readLines(String resource) {
        InputStream input =
                MultiHypothesisPocketAlignerTest.class
                        .getResourceAsStream(resource);

        if (input == null) {
            throw new IllegalStateException(
                    "Missing test resource: " + resource
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
        )) {
            return reader.lines().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // Irregular 8-point cloud (same fixture as the similarity tests).
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

    // Regular octahedron: invariant under 90-degree rotations about
    // any axis.
    private static final double[][] OCTAHEDRON_CLOUD = {
            {10.0, 0.0, 0.0},
            {-10.0, 0.0, 0.0},
            {0.0, 10.0, 0.0},
            {0.0, -10.0, 0.0},
            {0.0, 0.0, 10.0},
            {0.0, 0.0, -10.0}
    };

    // Invariant under the 180-degree rotation about the z-axis.
    private static final double[][] Z_SYMMETRIC_CLOUD = {
            {6.0, 0.0, 4.0},
            {-6.0, 0.0, 4.0},
            {6.0, 0.0, -4.0},
            {-6.0, 0.0, -4.0},
            {0.0, 6.0, 0.0},
            {0.0, -6.0, 0.0}
    };

    // 3x3x3 grid with spacing 13: invariant under translations by one
    // spacing except for the boundary planes.
    private static final double[][] GRID_CLOUD = grid();

    private static double[][] grid() {
        double[][] points = new double[27][];
        int index = 0;

        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    points[index++] = new double[]{
                            13.0 * x,
                            13.0 * y,
                            13.0 * z
                    };
                }
            }
        }

        return points;
    }
}
