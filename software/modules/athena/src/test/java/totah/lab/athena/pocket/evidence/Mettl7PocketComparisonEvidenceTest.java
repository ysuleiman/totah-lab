package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.AlignmentInitialization;
import totah.lab.athena.pocket.compare.MultiHypothesisPocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
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
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end assembly of a {@link PocketComparisonEvidence} for the
 * METTL7A pocket vs METTL7B pocket regression fixtures (analysis/
 * mettl7-correspondence): the sequence-seeded hypothesis must win
 * with ~0.265 geometry, 31/31 sequence-consistent pairs and high
 * chemistry, while the discarded PCA+ICP frame (~0.263 geometry,
 * 0/27 consistent) stays inspectable, and the assessment must
 * reflect the functional agreement rather than the geometry alone.
 */
class Mettl7PocketComparisonEvidenceTest {

    @Test
    void mettl7aVsMettl7bAssemblesConsistentEvidence() {
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

        PocketAlignmentResult result = new MultiHypothesisPocketAligner()
                .align(
                        queryCloud,
                        candidateCloud,
                        queryResidues,
                        candidateResidues,
                        sequenceAlignment
                );

        PocketAlignmentEvidence alignment =
                new PocketAlignmentEvidenceFactory().create(result);
        PocketResidueEvidence residues =
                new PocketResidueEvidenceFactory(
                        new ResidueSubstitutionScorer()
                ).create(
                        result.correspondence(),
                        sequenceAlignment,
                        Set.of()
                );
        PocketFunctionalEvidence functional =
                new PocketFunctionalEvidence(
                        Optional.empty(),
                        new PocketFunctionalEvidenceFactory(
                                new ResidueSubstitutionScorer()
                        ).keyResidues(result.correspondence(), Set.of())
                );

        // Retrieval lives in web-api; synthetic evidence with the
        // spec's example ranks.
        PocketRetrievalEvidence retrieval = new PocketRetrievalEvidence(
                new GlobalShapeRetrievalEvidence(
                        true,
                        OptionalInt.of(6840),
                        OptionalDouble.of(0.31)
                ),
                new PocketMatchRetrievalEvidence(
                        true,
                        OptionalInt.of(902),
                        OptionalInt.empty(),
                        OptionalDouble.of(0.42),
                        OptionalDouble.of(0.51),
                        OptionalDouble.of(0.48),
                        4.0
                ),
                false,
                Set.of(
                        PocketCandidateSource.GLOBAL_SHAPE,
                        PocketCandidateSource.POCKET_MATCH
                )
        );

        // The assessment is derived from the preserved bundle; the
        // placeholder verdict below is never read by the rules.
        PocketComparisonEvidence bundle = new PocketComparisonEvidence(
                retrieval,
                alignment,
                residues,
                functional,
                PocketComparisonAssessment.INSUFFICIENT_EVIDENCE
        );

        PocketComparisonEvidence evidence = new PocketComparisonEvidence(
                retrieval,
                alignment,
                residues,
                functional,
                PocketAssessmentRules.defaults().assess(bundle)
        );

        // The discarded PCA+ICP hypothesis is preserved with its
        // known wrong-frame metrics.
        AlignmentHypothesisEvidence pca =
                evidence.alignment().pcaIcp();

        assertTrue(pca.available());
        assertFalse(pca.accepted());
        assertEquals(0.263, pca.geometrySimilarity(), 0.05);
        assertEquals(0, pca.sequenceConsistentPairCount());
        assertEquals(27, pca.residueCorrespondenceCount());

        // The selected sequence-seeded hypothesis.
        AlignmentHypothesisEvidence seeded =
                evidence.alignment().sequenceSeeded();

        assertNotEquals(
                AlignmentInitialization.PCA_ICP,
                evidence.alignment().selectedInitialization()
        );
        assertTrue(seeded.available());
        assertTrue(seeded.accepted());
        assertEquals(0.265, seeded.geometrySimilarity(), 0.05);
        assertEquals(31, seeded.sequenceConsistentPairCount());
        assertEquals(31, seeded.residueCorrespondenceCount());
        assertFalse(evidence.alignment().selectionReason().isBlank());

        // Residue evidence under the selected alignment.
        assertEquals(31, evidence.residues().matchedResidueCount());
        assertEquals(
                0.82,
                evidence.residues().chemistrySimilarity(),
                0.02
        );
        assertEquals(
                0.87,
                evidence.residues().compatibleMatchedFraction(),
                0.02
        );
        assertEquals(
                0.13,
                evidence.residues().replacementFraction(),
                0.02
        );
        assertEquals(
                31,
                evidence.residues().sequenceConsistentPairCount()
        );
        assertEquals(
                1.0,
                evidence.residues().sequenceConsistentFraction(),
                1.0e-9
        );
        assertEquals(
                31,
                evidence.residues().correspondences().size()
        );

        // The assessment reflects the functional agreement.
        assertNotEquals(
                PocketComparisonAssessment.GEOMETRIC_MATCH_ONLY,
                evidence.assessment()
        );
        assertTrue(
                evidence.assessment()
                        == PocketComparisonAssessment
                                .STRONG_FUNCTIONAL_MATCH
                        || evidence.assessment()
                                == PocketComparisonAssessment
                                        .PROBABLE_FUNCTIONAL_MATCH,
                "assessment was " + evidence.assessment()
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
                Mettl7PocketComparisonEvidenceTest.class
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
}
