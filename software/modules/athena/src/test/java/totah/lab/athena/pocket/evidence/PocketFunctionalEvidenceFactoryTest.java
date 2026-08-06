package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;
import totah.lab.athena.pocket.compare.residue.ResidueMatch;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketFunctionalEvidenceFactoryTest {

    private static final double TOLERANCE = 1.0e-9;

    private final PocketFunctionalEvidenceFactory factory =
            new PocketFunctionalEvidenceFactory(
                    new ResidueSubstitutionScorer()
            );

    @Test
    void contactConservationCoversAllAnnotationCombinations() {
        // M1: both annotated, identical. M2: query-only annotated,
        // conservative. M3: both annotated, incompatible.
        // M4: candidate-only annotated. SER30: annotated query
        // residue without a correspondence.
        ResidueMatch both = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );
        ResidueMatch queryOnly = match(
                200, "ASP", ResidueChemistry.NEGATIVE,
                610, "GLU", ResidueChemistry.NEGATIVE,
                MatchType.CONSERVATIVE
        );
        ResidueMatch incompatible = match(
                33, "LYS", ResidueChemistry.POSITIVE,
                915, "ASP", ResidueChemistry.NEGATIVE,
                MatchType.DIFFERENT
        );
        ResidueMatch candidateOnly = match(
                83, "ALA", ResidueChemistry.HYDROPHOBIC,
                777, "PHE", ResidueChemistry.AROMATIC,
                MatchType.DIFFERENT
        );
        PocketResiduePoint unmatched = point(
                30, "SER", ResidueChemistry.POLAR
        );

        ResidueCorrespondence correspondence = correspondence(
                List.of(both, queryOnly, incompatible, candidateOnly),
                List.of(unmatched),
                List.of()
        );

        LigandContactProvider provider = provider(
                Map.of(
                        "query",
                        Set.of(
                                reference(145, "LEU"),
                                reference(200, "ASP"),
                                reference(33, "LYS"),
                                reference(30, "SER")
                        ),
                        "candidate",
                        Set.of(
                                reference(500, "LEU"),
                                reference(915, "ASP"),
                                reference(777, "PHE")
                        )
                )
        );

        LigandContactEvidence evidence = factory.ligandContacts(
                correspondence,
                null,
                provider,
                "query",
                "candidate",
                FunctionalLigand.SAM
        );

        assertEquals("SAM", evidence.ligandName());
        assertEquals(4, evidence.queryContactResidueCount());
        assertEquals(3, evidence.matchedQueryContactResidueCount());
        assertEquals(1, evidence.identicalContactCount());
        assertEquals(1, evidence.conservativeContactCount());
        assertEquals(0, evidence.chemistryCompatibleContactCount());
        assertEquals(1, evidence.incompatibleContactCount());
        assertEquals(1, evidence.unmatchedContactCount());
        assertEquals(2, evidence.sharedContactAnnotationCount());
        assertEquals(0.75, evidence.contactCoverage(), TOLERANCE);
        assertEquals(
                1.0 / 3.0,
                evidence.contactIdentityFraction(),
                TOLERANCE
        );

        // Both annotated, query-only, candidate-only and the
        // unmatched annotated query residue all appear.
        assertEquals(5, evidence.correspondences().size());

        FunctionalResidueCorrespondence bothEntry =
                evidence.correspondences().get(0);
        assertTrue(bothEntry.queryAnnotated());
        assertTrue(bothEntry.candidateAnnotated());
        assertTrue(bothEntry.correspondence().isPresent());
        assertTrue(
                bothEntry.correspondence().orElseThrow().identical()
        );

        FunctionalResidueCorrespondence queryOnlyEntry =
                evidence.correspondences().get(1);
        assertTrue(queryOnlyEntry.queryAnnotated());
        assertFalse(queryOnlyEntry.candidateAnnotated());
        assertTrue(
                queryOnlyEntry.correspondence().orElseThrow()
                        .conservativeSubstitution()
        );

        FunctionalResidueCorrespondence candidateOnlyEntry =
                evidence.correspondences().get(3);
        assertFalse(candidateOnlyEntry.queryAnnotated());
        assertTrue(candidateOnlyEntry.candidateAnnotated());

        FunctionalResidueCorrespondence unmatchedEntry =
                evidence.correspondences().get(4);
        assertTrue(unmatchedEntry.queryAnnotated());
        assertFalse(unmatchedEntry.candidateAnnotated());
        assertTrue(unmatchedEntry.correspondence().isEmpty());
    }

    @Test
    void noAnnotationYieldsZeroedEvidence() {
        ResidueMatch match = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );

        LigandContactEvidence evidence = factory.ligandContacts(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                provider(Map.of()),
                "query",
                "candidate",
                FunctionalLigand.SAH
        );

        assertEquals("SAH", evidence.ligandName());
        assertEquals(0, evidence.queryContactResidueCount());
        assertEquals(0.0, evidence.contactCoverage(), TOLERANCE);
        assertTrue(evidence.correspondences().isEmpty());
    }

    @Test
    void ligandChoiceSelectsTheAnnotationSet() {
        ResidueMatch match = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );

        LigandContactProvider provider =
                (structureKey, ligand) -> ligand == FunctionalLigand.SAH
                        && structureKey.equals("query")
                        ? Set.of(reference(145, "LEU"))
                        : Set.of();

        LigandContactEvidence sah = factory.ligandContacts(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                provider,
                "query",
                "candidate",
                FunctionalLigand.SAH
        );
        LigandContactEvidence sam = factory.ligandContacts(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                provider,
                "query",
                "candidate",
                FunctionalLigand.SAM
        );

        assertEquals(1, sah.queryContactResidueCount());
        assertEquals(0, sam.queryContactResidueCount());
    }

    @Test
    void keyResiduesSummarizeTheConfiguredSet() {
        ResidueMatch keyIdentical = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );
        ResidueMatch keyConservative = match(
                200, "ASP", ResidueChemistry.NEGATIVE,
                610, "GLU", ResidueChemistry.NEGATIVE,
                MatchType.CONSERVATIVE
        );
        ResidueMatch keyReplaced = match(
                33, "LYS", ResidueChemistry.POSITIVE,
                915, "ASP", ResidueChemistry.NEGATIVE,
                MatchType.DIFFERENT
        );
        ResidueMatch notKey = match(
                83, "ALA", ResidueChemistry.HYDROPHOBIC,
                777, "ALA", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );
        PocketResiduePoint keyUnmatched = point(
                30, "SER", ResidueChemistry.POLAR
        );

        KeyResidueEvidence evidence = factory.keyResidues(
                correspondence(
                        List.of(
                                keyIdentical,
                                keyConservative,
                                keyReplaced,
                                notKey
                        ),
                        List.of(keyUnmatched),
                        List.of()
                ),
                Set.of("LEU145", "ASP200", "LYS33", "SER30", "GLY999")
        );

        // GLY999 is not in the query pocket and does not count.
        assertEquals(4, evidence.totalKeyResidueCount());
        assertEquals(3, evidence.matchedKeyResidueCount());
        assertEquals(1, evidence.identicalKeyResidueCount());
        assertEquals(
                2,
                evidence.chemistryCompatibleKeyResidueCount()
        );
    }

    @Test
    void freeFormCcdCodeSelectsTheAnnotationSets() {
        ResidueMatch match = match(
                145, "LEU", ResidueChemistry.HYDROPHOBIC,
                500, "LEU", ResidueChemistry.HYDROPHOBIC,
                MatchType.IDENTICAL
        );

        LigandContactEvidence evidence = factory.ligandContacts(
                correspondence(List.of(match), List.of(), List.of()),
                null,
                Set.of(reference(145, "LEU")),
                Set.of(reference(500, "LEU")),
                "ATP"
        );

        assertEquals("ATP", evidence.ligandName());
        assertEquals(1, evidence.queryContactResidueCount());
        assertEquals(1, evidence.matchedQueryContactResidueCount());
        assertEquals(1, evidence.identicalContactCount());
        assertEquals(1, evidence.sharedContactAnnotationCount());
        assertEquals(1.0, evidence.contactCoverage(), TOLERANCE);
    }

    private static LigandContactProvider provider(
            Map<String, Set<ResidueReference>> contacts
    ) {
        return (structureKey, ligand) ->
                contacts.getOrDefault(structureKey, Set.of());
    }

    private static ResidueReference reference(
            int residueNumber,
            String residueName
    ) {
        return new ResidueReference(
                "A",
                residueNumber,
                ' ',
                residueName
        );
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
                reference(residueNumber, residueName),
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
