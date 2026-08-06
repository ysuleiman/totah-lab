package totah.lab.web.ligandcontact;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.evidence.LigandContact;
import totah.lab.athena.pocket.evidence.LigandContactStatus;
import totah.lab.athena.pocket.evidence.LigandContactType;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence;
import totah.lab.hermes.biohub.model.BiohubPocketEvidence.ResidueContact;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer
        .Aggregate;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer
        .LigandContactConservationReport;
import totah.lab.web.ligandcontact.LigandContactConservationAnalyzer
        .Row;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandContactConservationAnalyzerTest {

    private final LigandContactConservationAnalyzer analyzer =
            new LigandContactConservationAnalyzer();

    @Test
    void joinsEvidenceWithAlignmentAndClassifiesPairs() {
        LigandContactConservationReport report = analyzer.analyze(
                "7A",
                "7B",
                queryEvidence(),
                candidateEvidence(),
                alignment()
        );

        Row asp = rowAt(report, 98);
        assertTrue(asp.queryDirectContact());
        assertTrue(asp.candidateDirectContact());
        assertEquals(2.54, asp.queryMinimumDistance());
        assertEquals(2.49, asp.candidateMinimumDistance());
        assertEquals(64, asp.queryAtomPairCount());
        assertEquals(MatchType.IDENTICAL, asp.matchType());
        assertTrue(asp.sequenceConsistent());

        assertEquals(MatchType.CONSERVATIVE, rowAt(report, 40).matchType());
        assertEquals(
                MatchType.CHEMISTRY_COMPATIBLE,
                rowAt(report, 126).matchType()
        );
        assertEquals(MatchType.IDENTICAL, rowAt(report, 78).matchType());
    }

    @Test
    void excludesAlignedPairsWithoutEvidenceOnEitherSide() {
        LigandContactConservationReport report = analyzer.analyze(
                "7A",
                "7B",
                queryEvidence(),
                candidateEvidence(),
                alignment()
        );

        assertTrue(report.rows().stream()
                .noneMatch(row -> Integer.valueOf(1)
                        .equals(row.queryResidueNumber())));
    }

    @Test
    void aggregatesDirectionalContactCounts() {
        LigandContactConservationReport report = analyzer.analyze(
                "7A",
                "7B",
                queryEvidence(),
                candidateEvidence(),
                alignment()
        );

        Aggregate aggregate = report.aggregate();
        assertEquals(4, aggregate.queryContactCount());
        assertEquals(4, aggregate.matchedContactCount());
        assertEquals(4, aggregate.sharedContactCount());
        assertEquals(2, aggregate.identicalSharedCount());
        assertEquals(1, aggregate.conservativeSharedCount());
        assertEquals(1, aggregate.nonConservativeSharedCount());
        assertEquals(0, aggregate.unmatchedQueryContactCount());
        assertEquals(0, aggregate.queryContactsAlignedToNonContact());
        assertEquals(5, aggregate.candidateContactCount());
        assertEquals(1, aggregate.candidateOnlyContactCount());
        assertEquals(0, aggregate.candidateContactsAlignedToNonContact());
        assertEquals(1.0, aggregate.queryContactCoverage());
        assertEquals(0.8, aggregate.candidateContactCoverage(), 1.0e-9);
        assertEquals(0.1075, aggregate.meanDistanceDifference(), 1.0e-9);
        assertEquals(0.065, aggregate.medianDistanceDifference(), 1.0e-9);
    }

    @Test
    void reportsGapAndNonContactOutcomesSeparately() {
        BiohubPocketEvidence query = evidence(List.of(
                contact(10, "ASP", 2.5, 30, true),
                contact(11, "THR", 3.0, 10, true)
        ));
        BiohubPocketEvidence candidate = evidence(List.of(
                contact(10, "ASP", 2.6, 30, true)
        ));
        // 10 aligned; query 11 lost to a gap
        SequenceAlignment alignment = new SequenceAlignment(
                1.0,
                List.of(new AlignedResiduePair(10, 10, "ASP", "ASP"))
        );

        LigandContactConservationReport report =
                analyzer.analyze("7A", "7B", query, candidate, alignment);

        Aggregate aggregate = report.aggregate();
        assertEquals(1, aggregate.sharedContactCount());
        assertEquals(1, aggregate.unmatchedQueryContactCount());
        assertEquals(0.5, aggregate.queryContactCoverage(), 1.0e-9);

        Row gap = report.rows().stream()
                .filter(row -> Integer.valueOf(11)
                        .equals(row.queryResidueNumber()))
                .findFirst()
                .orElseThrow();
        assertFalse(gap.sequenceConsistent());
        assertNull(gap.candidateResidueNumber());
        assertNull(gap.matchType());
    }

    @Test
    void countsQueryContactsAlignedToNonContactPartners() {
        BiohubPocketEvidence query = evidence(List.of(
                contact(10, "ASP", 2.5, 30, true)
        ));
        BiohubPocketEvidence candidate = evidence(List.of(
                contact(10, "ASP", 5.5, 2, false)
        ));
        SequenceAlignment alignment = new SequenceAlignment(
                1.0,
                List.of(new AlignedResiduePair(10, 10, "ASP", "ASP"))
        );

        Aggregate aggregate = analyzer
                .analyze("7A", "7B", query, candidate, alignment)
                .aggregate();

        assertEquals(1, aggregate.matchedContactCount());
        assertEquals(0, aggregate.sharedContactCount());
        assertEquals(1, aggregate.queryContactsAlignedToNonContact());
        // the aligned candidate residue is in the shell but is not a
        // direct contact, so it is not candidate-only nor a contact
        assertEquals(0, aggregate.candidateContactsAlignedToNonContact());
    }

    @Test
    void detectsEquivalentEvidenceArtifacts() {
        BiohubPocketEvidence sam = queryEvidence();
        BiohubPocketEvidence identical = evidence(
                sam.residues().stream()
                        .map(contact -> new ResidueContact(
                                contact.chain(),
                                contact.residueNumber(),
                                contact.residueName(),
                                contact.minimumDistance() + 0.005,
                                contact.contactingAtomPairCount(),
                                contact.directContact()
                        ))
                        .toList()
        );

        assertTrue(LigandContactConservationAnalyzer
                .equivalent(sam, identical, 0.01));

        BiohubPocketEvidence shifted = evidence(List.of(
                contact(98, "ASP", 2.9, 64, true)
        ));
        assertFalse(LigandContactConservationAnalyzer
                .equivalent(sam, shifted, 0.01));
    }

    @Test
    void populatesCanonicalContactsFromBiohubEvidence() {
        LigandContactConservationReport report = analyzer.analyze(
                "7A",
                "7B",
                queryEvidence(),
                candidateEvidence(),
                alignment()
        );

        // 5 query + 5 candidate evidence residues, in side order.
        assertEquals(10, report.contacts().size());

        LigandContact asp = report.contacts().get(0);
        assertEquals(LigandContactStatus.AVAILABLE, asp.status());
        assertEquals("7A", asp.pocketReference());
        assertEquals("SAM", asp.ligandCcd());
        assertEquals("A", asp.residue().chainId());
        assertEquals(98, asp.residue().residueNumber());
        assertEquals("ASP", asp.residue().residueName());
        assertEquals(2.54, asp.minimumDistance());
        assertEquals(LigandContactType.DIRECT, asp.contactType());
        assertEquals("BIOHUB", asp.evidenceSource());

        // The shell member (beyond the direct-contact cutoff) is a
        // SHELL contact, not a direct one.
        LigandContact shell = report.contacts().stream()
                .filter(contact ->
                        contact.residue().residueNumber() == 200)
                .findFirst()
                .orElseThrow();
        assertEquals(LigandContactType.SHELL, shell.contactType());
        assertEquals(5.0, shell.minimumDistance());

        LigandContact candidateSide = report.contacts().get(5);
        assertEquals("7B", candidateSide.pocketReference());
        assertEquals("SAM", candidateSide.ligandCcd());
    }

    private static Row rowAt(
            LigandContactConservationReport report,
            int queryResidueNumber
    ) {
        return report.rows().stream()
                .filter(row -> Integer.valueOf(queryResidueNumber)
                        .equals(row.queryResidueNumber()))
                .findFirst()
                .orElseThrow();
    }

    private static BiohubPocketEvidence queryEvidence() {
        return evidence(List.of(
                contact(98, "ASP", 2.54, 64, true),
                contact(40, "LEU", 3.77, 27, true),
                contact(126, "ALA", 3.73, 26, true),
                contact(78, "GLY", 2.71, 63, true),
                contact(200, "SER", 5.0, 3, false)
        ));
    }

    private static BiohubPocketEvidence candidateEvidence() {
        return evidence(List.of(
                contact(98, "ASP", 2.49, 64, true),
                contact(40, "MET", 3.52, 27, true),
                contact(126, "PRO", 3.65, 29, true),
                contact(78, "GLY", 2.76, 60, true),
                contact(210, "LYS", 3.0, 5, true)
        ));
    }

    private static SequenceAlignment alignment() {
        return new SequenceAlignment(
                0.8,
                List.of(
                        new AlignedResiduePair(1, 1, "MET", "MET"),
                        new AlignedResiduePair(40, 40, "LEU", "MET"),
                        new AlignedResiduePair(78, 78, "GLY", "GLY"),
                        new AlignedResiduePair(98, 98, "ASP", "ASP"),
                        new AlignedResiduePair(126, 126, "ALA", "PRO")
                )
        );
    }

    private static BiohubPocketEvidence evidence(
            List<ResidueContact> contacts
    ) {
        return new BiohubPocketEvidence(
                "SAM",
                "esmfold2",
                6.0,
                4.5,
                null,
                null,
                contacts
        );
    }

    private static ResidueContact contact(
            int residueNumber,
            String residueName,
            double minimumDistance,
            int atomPairs,
            boolean directContact
    ) {
        return new ResidueContact(
                "A",
                residueNumber,
                residueName,
                minimumDistance,
                atomPairs,
                directContact
        );
    }
}
