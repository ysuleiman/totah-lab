package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;
import totah.lab.athena.pocket.compare.residue.ResidueMatch;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketResidueEvidenceFactoryTest {

    private static final double TOLERANCE = 1.0e-9;

    private final PocketResidueEvidenceFactory factory =
            new PocketResidueEvidenceFactory(
                    new ResidueSubstitutionScorer()
            );

    @Test
    void identicalPairReportsIdentityChemistryAndSubstitution() {
        ResidueMatch match = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                Set.of()
        );

        assertEquals(1, evidence.matchedResidueCount());
        assertEquals(1, evidence.identicalCount());
        assertEquals(1.0, evidence.identityFraction(), TOLERANCE);
        assertEquals(1.0, evidence.chemistrySimilarity(), TOLERANCE);
        // The shared scorer keeps the BLOSUM62 diagonal: LEU-LEU is
        // (4 + 4) / 15, not a forced 1.0.
        assertEquals(
                8.0 / 15.0,
                evidence.substitutionSimilarity(),
                TOLERANCE
        );
        assertEquals(
                1.0,
                evidence.compatibleMatchedFraction(),
                TOLERANCE
        );
        assertEquals(0.0, evidence.replacementFraction(), TOLERANCE);

        ResidueCorrespondenceEvidence pair =
                evidence.correspondences().getFirst();

        assertTrue(pair.identical());
        assertFalse(pair.conservativeSubstitution());
        assertEquals(MatchType.IDENTICAL, pair.matchType());
        assertEquals("LEU", pair.queryAminoAcid());
        assertEquals("LEU", pair.candidateAminoAcid());
    }

    @Test
    void conservativePairIsDistinctFromIdentityAndChemistry() {
        ResidueMatch match = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "ILE", ResidueChemistry.HYDROPHOBIC,
                MatchType.CONSERVATIVE
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                Set.of()
        );

        assertEquals(0, evidence.identicalCount());
        assertEquals(1, evidence.conservativeSubstitutionCount());
        assertEquals(0.0, evidence.identityFraction(), TOLERANCE);
        assertEquals(0.70, evidence.chemistrySimilarity(), TOLERANCE);
        // BLOSUM62 LEU-ILE = 2, normalized (2 + 4) / 15.
        assertEquals(0.4, evidence.substitutionSimilarity(), TOLERANCE);

        ResidueCorrespondenceEvidence pair =
                evidence.correspondences().getFirst();

        assertFalse(pair.identical());
        assertTrue(pair.conservativeSubstitution());
        assertEquals(0.70, pair.chemistryScore(), TOLERANCE);
        assertEquals(0.4, pair.substitutionScore(), TOLERANCE);
    }

    @Test
    void chemistryCompatiblePairCanHavePoorSubstitution() {
        // ALA/PRO share the hydrophobic class but BLOSUM62 scores
        // them -1 (normalized 3/15): chemistry-compatible with poor
        // substitution. The two dimensions must stay distinct.
        ResidueMatch match = match(
                83, "ALA", ResidueChemistry.HYDROPHOBIC,
                777, "PRO", ResidueChemistry.HYDROPHOBIC,
                MatchType.CHEMISTRY_COMPATIBLE
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                Set.of()
        );

        ResidueCorrespondenceEvidence pair =
                evidence.correspondences().getFirst();

        assertEquals(MatchType.CHEMISTRY_COMPATIBLE, pair.matchType());
        assertEquals(0.80, pair.chemistryScore(), TOLERANCE);
        assertEquals(3.0 / 15.0, pair.substitutionScore(), TOLERANCE);
        assertTrue(pair.substitutionScore() < 0.3);
        assertEquals(0, evidence.identicalCount());
        assertEquals(0, evidence.conservativeSubstitutionCount());
        assertEquals(1, evidence.chemistryCompatibleCount());
        assertEquals(
                1.0,
                evidence.compatibleMatchedFraction(),
                TOLERANCE
        );
    }

    @Test
    void incompatiblePairIsASpatialReplacement() {
        ResidueMatch match = match(
                33, "LYS", ResidueChemistry.POSITIVE,
                915, "ASP", ResidueChemistry.NEGATIVE,
                MatchType.DIFFERENT
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                Set.of()
        );

        assertEquals(1, evidence.incompatibleReplacementCount());
        assertEquals(1.0, evidence.replacementFraction(), TOLERANCE);
        assertEquals(
                0.0,
                evidence.compatibleMatchedFraction(),
                TOLERANCE
        );
        assertEquals(0.0, evidence.chemistrySimilarity(), TOLERANCE);
    }

    @Test
    void unmatchedResiduesLowerCoverageWithoutTouchingMatchScores() {
        ResidueMatch match = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );
        PocketResiduePoint unmatchedQuery = point(
                30, "SER", ResidueChemistry.POLAR
        );
        PocketResiduePoint unmatchedCandidate = point(
                640, "THR", ResidueChemistry.POLAR
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(
                        List.of(match),
                        List.of(unmatchedQuery),
                        List.of(unmatchedCandidate)
                ),
                null,
                Set.of()
        );

        assertEquals(2, evidence.queryResidueCount());
        assertEquals(2, evidence.candidateResidueCount());
        assertEquals(1, evidence.unmatchedQueryResidueCount());
        assertEquals(1, evidence.unmatchedCandidateResidueCount());
        assertEquals(0.5, evidence.queryResidueCoverage(), TOLERANCE);
        assertEquals(
                0.5,
                evidence.candidateResidueCoverage(),
                TOLERANCE
        );
        assertEquals(1.0, evidence.chemistrySimilarity(), TOLERANCE);
    }

    @Test
    void sequenceAlignedPairsAreFlaggedAndCounted() {
        ResidueMatch consistent = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );
        ResidueMatch inconsistent = match(
                33, "LYS", ResidueChemistry.POSITIVE,
                915, "LYS", ResidueChemistry.POSITIVE,
                MatchType.IDENTICAL
        );

        SequenceAlignment alignment = new SequenceAlignment(
                1.0,
                List.of(new AlignedResiduePair(145, 500, "LEU", "LEU"))
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(
                        List.of(consistent, inconsistent),
                        List.of(),
                        List.of()
                ),
                alignment,
                Set.of()
        );

        assertEquals(1, evidence.sequenceConsistentPairCount());
        assertEquals(
                0.5,
                evidence.sequenceConsistentFraction(),
                TOLERANCE
        );
        assertTrue(
                evidence.correspondences().get(0).sequenceAlignedPair()
        );
        assertFalse(
                evidence.correspondences().get(1).sequenceAlignedPair()
        );
    }

    @Test
    void absentSequenceAlignmentYieldsZeroSequenceConsistency() {
        ResidueMatch match = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                Set.of()
        );

        assertEquals(0, evidence.sequenceConsistentPairCount());
        assertEquals(
                0.0,
                evidence.sequenceConsistentFraction(),
                TOLERANCE
        );
        assertFalse(
                evidence.correspondences().getFirst()
                        .sequenceAlignedPair()
        );
    }

    @Test
    void keyResidueFlagFollowsTheConfiguredSet() {
        ResidueMatch key = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );
        ResidueMatch other = match(
                33, "LYS", ResidueChemistry.POSITIVE,
                915, "LYS", ResidueChemistry.POSITIVE,
                MatchType.IDENTICAL
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(List.of(key, other), List.of(), List.of()),
                null,
                Set.of("leu145")
        );

        assertTrue(
                evidence.correspondences().get(0).queryKeyResidue()
        );
        assertFalse(
                evidence.correspondences().get(1).queryKeyResidue()
        );
    }

    @Test
    void contactFlagsComeFromTheContactLabelSets() {
        ResidueMatch match = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );

        PocketResidueEvidence evidence = factory.create(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                Set.of(),
                Set.of("LEU145"),
                Set.of()
        );

        ResidueCorrespondenceEvidence pair =
                evidence.correspondences().getFirst();

        assertTrue(pair.querySamContact());
        assertFalse(pair.candidateSamContact());
    }

    private static ResidueMatch match(
            int queryNumber,
            String queryName,
            ResidueChemistry queryChemistry,
            int candidateNumber,
            String candidateName,
            ResidueChemistry candidateChemistry,
            MatchType matchType
    ) {
        return new ResidueMatch(
                point(queryNumber, queryName, queryChemistry),
                point(candidateNumber, candidateName, candidateChemistry),
                1.5,
                matchType,
                matchType == MatchType.IDENTICAL,
                matchType != MatchType.DIFFERENT
        );
    }

    private static PocketResiduePoint point(
            int residueNumber,
            String residueName,
            ResidueChemistry chemistry
    ) {
        return new PocketResiduePoint(
                new ResidueReference("A", residueNumber, ' ', residueName),
                new Point3D(0.0, 0.0, 0.0),
                chemistry
        );
    }

    private static ResidueCorrespondence correspondence(
            List<ResidueMatch> matches,
            List<PocketResiduePoint> unmatchedQuery,
            List<PocketResiduePoint> unmatchedCandidate
    ) {
        int queryCount = matches.size() + unmatchedQuery.size();
        int candidateCount = matches.size() + unmatchedCandidate.size();

        return new ResidueCorrespondence(
                matches,
                unmatchedQuery,
                unmatchedCandidate,
                queryCount == 0
                        ? 0.0
                        : (double) matches.size() / queryCount,
                candidateCount == 0
                        ? 0.0
                        : (double) matches.size() / candidateCount,
                0.0,
                0.0,
                matches.isEmpty() ? 0.0 : 1.5,
                matches.isEmpty() ? 0.0 : 1.5
        );
    }
}
