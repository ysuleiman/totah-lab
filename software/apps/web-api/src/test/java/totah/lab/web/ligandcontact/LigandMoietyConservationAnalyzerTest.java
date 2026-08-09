package totah.lab.web.ligandcontact;

import org.junit.jupiter.api.Test;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.web.ligandcontact.ComplexLigandContactExtractor
        .ResidueMoietyContact;
import totah.lab.web.ligandcontact.LigandMoietyConservationAnalyzer
        .Aggregate;
import totah.lab.web.ligandcontact.LigandMoietyConservationAnalyzer
        .LigandMoietyConservationReport;
import totah.lab.web.ligandcontact.LigandMoietyConservationAnalyzer
        .Row;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandMoietyConservationAnalyzerTest {

    private final LigandMoietyConservationAnalyzer analyzer =
            new LigandMoietyConservationAnalyzer();

    @Test
    void reportsSameMoietyAndSwitchesPerAlignedPair() {
        LigandMoietyConservationReport report = analyzer.analyze(
                "7A",
                "7B",
                "SAM",
                List.of(
                        contact(10, "ALA", SamMoiety.SULFONIUM, 3.0),
                        contact(20, "PHE", SamMoiety.ADENINE, 2.5)
                ),
                List.of(
                        contact(10, "ALA", SamMoiety.SULFONIUM, 3.2),
                        contact(20, "PHE", SamMoiety.RIBOSE, 2.4),
                        contact(30, "ASP", SamMoiety.METHIONINE, 2.9)
                ),
                new SequenceAlignment(
                        0.9,
                        List.of(
                                new AlignedResiduePair(
                                        10, 10, "ALA", "ALA"),
                                new AlignedResiduePair(
                                        20, 20, "PHE", "PHE")
                        )
                )
        );

        assertEquals(3, report.rows().size());

        Row conserved = report.rows().get(0);
        assertTrue(conserved.sameFacingMoiety());
        assertEquals(SamMoiety.SULFONIUM,
                conserved.queryFacingMoiety());

        Row switched = report.rows().get(1);
        assertFalse(switched.sameFacingMoiety());
        assertEquals(SamMoiety.ADENINE, switched.queryFacingMoiety());
        assertEquals(SamMoiety.RIBOSE, switched.candidateFacingMoiety());

        Row gap = report.rows().get(2);
        assertFalse(gap.sequenceConsistent());
        assertNull(gap.queryResidueNumber());
        assertEquals(30, gap.candidateResidueNumber());

        Aggregate aggregate = report.aggregate();
        assertEquals(2, aggregate.sharedContactCount());
        assertEquals(1, aggregate.sameMoietyCount());
        assertEquals(1, aggregate.moietySwitchCount());
        assertEquals(Map.of(
                SamMoiety.SULFONIUM, 1,
                SamMoiety.ADENINE, 1
        ), aggregate.queryContactsByMoiety());
        assertEquals(Map.of(
                SamMoiety.SULFONIUM, 1,
                SamMoiety.RIBOSE, 1,
                SamMoiety.METHIONINE, 1
        ), aggregate.candidateContactsByMoiety());
        assertEquals(0.2,
                aggregate.meanFacingDistanceDifference(), 1.0e-9);
        assertEquals(0.2,
                aggregate.medianFacingDistanceDifference(), 1.0e-9);
    }

    private static ResidueMoietyContact contact(
            int residueNumber,
            String residueName,
            SamMoiety facing,
            double facingDistance
    ) {
        Map<SamMoiety, Double> minima =
                new EnumMap<>(SamMoiety.class);
        minima.put(facing, facingDistance);
        return new ResidueMoietyContact(
                "A",
                residueNumber,
                residueName,
                minima,
                facing,
                facingDistance,
                true
        );
    }
}
