package totah.lab.athena.pocket.compare.residue;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the greedy one-to-one matching, the pair classification
 * and the summary statistics of
 * {@link ResidueCorrespondenceCalculator}.
 */
class ResidueCorrespondenceCalculatorTest {

    private static final double TOLERANCE = 1.0e-9;

    private final ResidueCorrespondenceCalculator calculator =
            new ResidueCorrespondenceCalculator();

    @Test
    void rejectsNonPositiveMaximumDistance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueCorrespondenceCalculator(0.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueCorrespondenceCalculator(-1.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueCorrespondenceCalculator(Double.NaN)
        );
    }

    @Test
    void assignsCandidateToClosestQueryOnly() {
        PocketResiduePoint firstQuery =
                query(1, "ALA", 0.0, 0.0, 0.0);
        PocketResiduePoint secondQuery =
                query(2, "ALA", 3.0, 0.0, 0.0);
        PocketResiduePoint candidate =
                candidate(10, "ALA", 0.8, 0.0, 0.0);

        ResidueCorrespondence correspondence = calculator.calculate(
                List.of(firstQuery, secondQuery),
                List.of(candidate)
        );

        assertEquals(1, correspondence.matches().size());

        ResidueMatch match = correspondence.matches().get(0);
        assertEquals(firstQuery, match.query());
        assertEquals(candidate, match.candidate());
        assertEquals(0.8, match.distanceAngstroms(), TOLERANCE);

        assertEquals(
                List.of(secondQuery),
                correspondence.unmatchedQuery()
        );
        assertTrue(correspondence.unmatchedCandidate().isEmpty());
    }

    @Test
    void assignsQueryToClosestCandidateOnly() {
        PocketResiduePoint queryPoint =
                query(1, "ALA", 0.0, 0.0, 0.0);
        PocketResiduePoint nearCandidate =
                candidate(10, "ALA", 1.0, 0.0, 0.0);
        PocketResiduePoint farCandidate =
                candidate(11, "ALA", 2.0, 0.0, 0.0);

        ResidueCorrespondence correspondence = calculator.calculate(
                List.of(queryPoint),
                List.of(nearCandidate, farCandidate)
        );

        assertEquals(1, correspondence.matches().size());
        assertEquals(
                nearCandidate,
                correspondence.matches().get(0).candidate()
        );
        assertEquals(
                List.of(farCandidate),
                correspondence.unmatchedCandidate()
        );
    }

    @Test
    void breaksDistanceTiesDeterministicallyByQueryReference() {
        PocketResiduePoint firstQuery =
                query(1, "ALA", 0.0, 0.0, 0.0);
        PocketResiduePoint secondQuery =
                query(2, "ALA", 2.0, 0.0, 0.0);
        PocketResiduePoint candidate =
                candidate(10, "ALA", 1.0, 0.0, 0.0);

        ResidueCorrespondence first = calculator.calculate(
                List.of(firstQuery, secondQuery),
                List.of(candidate)
        );
        ResidueCorrespondence second = calculator.calculate(
                List.of(secondQuery, firstQuery),
                List.of(candidate)
        );

        // The candidate sits exactly between the two query points, so
        // the lexicographically smaller query reference wins.
        assertEquals(1, first.matches().size());
        assertEquals(
                firstQuery,
                first.matches().get(0).query()
        );

        assertEquals(
                first.matches().get(0).query(),
                second.matches().get(0).query()
        );
        assertEquals(
                first.matches().get(0).candidate(),
                second.matches().get(0).candidate()
        );
    }

    @Test
    void respectsMaximumDistanceCutoff() {
        PocketResiduePoint queryPoint =
                query(1, "ALA", 0.0, 0.0, 0.0);
        PocketResiduePoint candidate =
                candidate(10, "ALA", 4.1, 0.0, 0.0);

        ResidueCorrespondence correspondence = calculator.calculate(
                List.of(queryPoint),
                List.of(candidate)
        );

        assertTrue(correspondence.matches().isEmpty());
        assertEquals(
                List.of(queryPoint),
                correspondence.unmatchedQuery()
        );
        assertEquals(
                List.of(candidate),
                correspondence.unmatchedCandidate()
        );
        assertEquals(0.0, correspondence.meanMatchedDistance());
        assertEquals(0.0, correspondence.maximumMatchedDistance());

        ResidueCorrespondence relaxed =
                new ResidueCorrespondenceCalculator(5.0)
                        .calculate(
                                List.of(queryPoint),
                                List.of(candidate)
                        );

        assertEquals(1, relaxed.matches().size());
        assertEquals(
                4.1,
                relaxed.matches().get(0).distanceAngstroms(),
                TOLERANCE
        );
    }

    @Test
    void preservesInputOrderInUnmatchedLists() {
        PocketResiduePoint matchedQuery =
                query(1, "ALA", 0.0, 0.0, 0.0);
        PocketResiduePoint unmatchedFirst =
                query(2, "LEU", 50.0, 0.0, 0.0);
        PocketResiduePoint unmatchedSecond =
                query(3, "SER", 60.0, 0.0, 0.0);

        PocketResiduePoint matchedCandidate =
                candidate(1, "ALA", 0.0, 0.0, 0.0);
        PocketResiduePoint unmatchedCandidate =
                candidate(2, "LYS", 70.0, 0.0, 0.0);

        ResidueCorrespondence correspondence = calculator.calculate(
                List.of(matchedQuery, unmatchedFirst, unmatchedSecond),
                List.of(matchedCandidate, unmatchedCandidate)
        );

        assertEquals(1, correspondence.matches().size());
        assertEquals(
                List.of(unmatchedFirst, unmatchedSecond),
                correspondence.unmatchedQuery()
        );
        assertEquals(
                List.of(unmatchedCandidate),
                correspondence.unmatchedCandidate()
        );
    }

    @Test
    void classifiesIdenticalResidues() {
        ResidueMatch match = singleMatch(
                query(1, "LEU", 0.0, 0.0, 0.0),
                candidate(10, "leu", 1.0, 0.0, 0.0)
        );

        assertEquals(MatchType.IDENTICAL, match.matchType());
        assertTrue(match.identicalResidue());
        assertTrue(match.chemistryCompatible());
        assertEquals(1.0, match.distanceAngstroms(), TOLERANCE);
    }

    @Test
    void classifiesConservativeSubstitutions() {
        ResidueMatch match = singleMatch(
                query(1, "LEU", 0.0, 0.0, 0.0),
                candidate(10, "ILE", 1.0, 0.0, 0.0)
        );

        assertEquals(MatchType.CONSERVATIVE, match.matchType());
        assertFalse(match.identicalResidue());
        assertTrue(match.chemistryCompatible());
    }

    @Test
    void classifiesChemistryCompatibleResidues() {
        // Leucine and proline share the hydrophobic chemistry class
        // but no conservative set.
        ResidueMatch match = singleMatch(
                query(1, "LEU", 0.0, 0.0, 0.0),
                candidate(10, "PRO", 1.0, 0.0, 0.0)
        );

        assertEquals(
                MatchType.CHEMISTRY_COMPATIBLE,
                match.matchType()
        );
        assertFalse(match.identicalResidue());
        assertTrue(match.chemistryCompatible());
    }

    @Test
    void classifiesDifferentResidues() {
        ResidueMatch match = singleMatch(
                query(1, "LEU", 0.0, 0.0, 0.0),
                candidate(10, "LYS", 1.0, 0.0, 0.0)
        );

        assertEquals(MatchType.DIFFERENT, match.matchType());
        assertFalse(match.identicalResidue());
        assertFalse(match.chemistryCompatible());
    }

    @Test
    void treatsCysteineAsMatchableOnlyWithItself() {
        ResidueMatch identical = singleMatch(
                query(1, "CYS", 0.0, 0.0, 0.0),
                candidate(10, "CYS", 1.0, 0.0, 0.0)
        );

        assertEquals(MatchType.IDENTICAL, identical.matchType());
        assertTrue(identical.identicalResidue());
        assertTrue(identical.chemistryCompatible());

        ResidueMatch differentName = singleMatch(
                query(1, "CYS", 0.0, 0.0, 0.0),
                candidate(10, "CYX", 1.0, 0.0, 0.0)
        );

        assertEquals(MatchType.DIFFERENT, differentName.matchType());
        assertFalse(differentName.chemistryCompatible());

        ResidueMatch otherClass = singleMatch(
                query(1, "CYS", 0.0, 0.0, 0.0),
                candidate(10, "SER", 1.0, 0.0, 0.0)
        );

        assertEquals(MatchType.DIFFERENT, otherClass.matchType());
        assertFalse(otherClass.chemistryCompatible());
    }

    @Test
    void treatsGlycineAsMatchableOnlyWithItself() {
        ResidueMatch identical = singleMatch(
                query(1, "GLY", 0.0, 0.0, 0.0),
                candidate(10, "GLY", 1.0, 0.0, 0.0)
        );

        assertEquals(MatchType.IDENTICAL, identical.matchType());
        assertTrue(identical.chemistryCompatible());

        ResidueMatch different = singleMatch(
                query(1, "GLY", 0.0, 0.0, 0.0),
                candidate(10, "ALA", 1.0, 0.0, 0.0)
        );

        assertEquals(MatchType.DIFFERENT, different.matchType());
        assertFalse(different.chemistryCompatible());
    }

    @Test
    void computesExactSummaryStatistics() {
        PocketResiduePoint alanine =
                query(1, "ALA", 0.0, 0.0, 0.0);
        PocketResiduePoint leucine =
                query(2, "LEU", 10.0, 0.0, 0.0);
        PocketResiduePoint serine =
                query(3, "SER", 20.0, 0.0, 0.0);

        PocketResiduePoint matchedAlanine =
                candidate(1, "ALA", 1.0, 0.0, 0.0);
        PocketResiduePoint matchedLysine =
                candidate(2, "LYS", 12.0, 0.0, 0.0);
        PocketResiduePoint arginine =
                candidate(3, "ARG", 50.0, 0.0, 0.0);

        ResidueCorrespondence correspondence = calculator.calculate(
                List.of(alanine, leucine, serine),
                List.of(matchedAlanine, matchedLysine, arginine)
        );

        assertEquals(2, correspondence.matches().size());
        assertEquals(
                List.of(serine),
                correspondence.unmatchedQuery()
        );
        assertEquals(
                List.of(arginine),
                correspondence.unmatchedCandidate()
        );

        assertEquals(
                2.0 / 3.0,
                correspondence.matchedFractionQuery(),
                TOLERANCE
        );
        assertEquals(
                2.0 / 3.0,
                correspondence.matchedFractionCandidate(),
                TOLERANCE
        );
        assertEquals(
                0.5,
                correspondence.identicalFraction(),
                TOLERANCE
        );
        assertEquals(
                0.5,
                correspondence.chemistryCompatibleFraction(),
                TOLERANCE
        );
        assertEquals(
                1.5,
                correspondence.meanMatchedDistance(),
                TOLERANCE
        );
        assertEquals(
                2.0,
                correspondence.maximumMatchedDistance(),
                TOLERANCE
        );
    }

    @Test
    void handlesEmptyInputs() {
        ResidueCorrespondence correspondence =
                calculator.calculate(List.of(), List.of());

        assertTrue(correspondence.matches().isEmpty());
        assertEquals(0.0, correspondence.matchedFractionQuery());
        assertEquals(0.0, correspondence.matchedFractionCandidate());
        assertEquals(0.0, correspondence.identicalFraction());
        assertEquals(
                0.0,
                correspondence.chemistryCompatibleFraction()
        );
        assertEquals(0.0, correspondence.meanMatchedDistance());
        assertEquals(0.0, correspondence.maximumMatchedDistance());
    }

    @Test
    void rejectsInvalidRecordComponents() {
        PocketResiduePoint point =
                query(1, "ALA", 0.0, 0.0, 0.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueMatch(
                        point,
                        point,
                        -1.0,
                        MatchType.IDENTICAL,
                        true,
                        true
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResidueCorrespondence(
                        List.of(),
                        List.of(),
                        List.of(),
                        1.5,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                )
        );
    }

    private ResidueMatch singleMatch(
            PocketResiduePoint queryPoint,
            PocketResiduePoint candidatePoint
    ) {
        ResidueCorrespondence correspondence = calculator.calculate(
                List.of(queryPoint),
                List.of(candidatePoint)
        );

        assertEquals(1, correspondence.matches().size());

        return correspondence.matches().get(0);
    }

    private static PocketResiduePoint query(
            int residueNumber,
            String residueName,
            double x,
            double y,
            double z
    ) {
        return point("A", residueNumber, residueName, x, y, z);
    }

    private static PocketResiduePoint candidate(
            int residueNumber,
            String residueName,
            double x,
            double y,
            double z
    ) {
        return point("B", residueNumber, residueName, x, y, z);
    }

    private static PocketResiduePoint point(
            String chainId,
            int residueNumber,
            String residueName,
            double x,
            double y,
            double z
    ) {
        return new PocketResiduePoint(
                new ResidueReference(
                        chainId,
                        residueNumber,
                        ' ',
                        residueName
                ),
                new Point3D(x, y, z),
                chemistryOf(residueName)
        );
    }

    private static ResidueChemistry chemistryOf(String residueName) {
        return switch (residueName.toUpperCase(
                java.util.Locale.ROOT
        )) {
            case "CYS", "CYX" -> ResidueChemistry.CYSTEINE;
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
