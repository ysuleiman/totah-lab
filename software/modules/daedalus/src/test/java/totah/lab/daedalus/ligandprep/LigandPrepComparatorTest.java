package totah.lab.daedalus.ligandprep;

import org.junit.jupiter.api.Test;
import totah.lab.daedalus.ligandprep.LigandPrepComparator.LigandPrepComparison;
import totah.lab.daedalus.ligandprep.PdbqtLigandReader.PdbqtLigand;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static totah.lab.daedalus.ligandprep.PdbqtLigandReaderTest.atomLine;

class LigandPrepComparatorTest {

    @Test
    void coordinateMatchedPairReportsDeltas() {
        // Same coordinates, different file order (torsion-tree orders
        // differ between the writers): alignment must follow
        // coordinates, not line order.
        LigandPrepComparison comparison = LigandPrepComparator.compare(
                ligand(List.of(
                        atomLine(1, "C1", 0, 0, 0, 0.05, "C"),
                        atomLine(2, "O1", 1.4, 0, 0, -0.30, "OA")),
                        2),
                ligand(List.of(
                        atomLine(1, "O", 1.4, 0, 0, -0.31, "OA"),
                        atomLine(2, "C", 0, 0, 0, 0.04, "C")),
                        3)
        );

        assertTrue(comparison.atomCountsMatch());
        assertEquals(2, comparison.matchedHeavyAtoms());
        assertEquals(0.02, comparison.totalChargeDelta(), 1e-9);
        assertEquals(0.01, comparison.meanAbsChargeDelta(), 1e-9);
        assertEquals(1.0, comparison.ad4TypeAgreement(), 1e-9);
        assertEquals(1, comparison.torsdofDelta());
        assertEquals(0.0, comparison.maxCoordinateDelta(), 1e-9);
    }

    @Test
    void shiftedAtomBeyondToleranceStaysUnmatched() {
        LigandPrepComparison comparison = LigandPrepComparator.compare(
                ligand(List.of(
                        atomLine(1, "C1", 0, 0, 0, 0.05, "C"),
                        atomLine(2, "O1", 1.4, 0, 0, -0.30, "OA")),
                        0),
                ligand(List.of(
                        atomLine(1, "C", 0, 0, 0, 0.04, "C"),
                        atomLine(2, "O", 1.4, 0, 0.1, -0.31, "OA")),
                        0)
        );

        // The O moved by 0.1 Å: only the carbon matches.
        assertEquals(1, comparison.matchedHeavyAtoms());
        assertEquals(2, comparison.meekoHeavyAtoms());
        assertEquals(0.01, comparison.meanAbsChargeDelta(), 1e-9);
        assertEquals(1.0, comparison.ad4TypeAgreement(), 1e-9);
    }

    @Test
    void typeDisagreementCountsAgainstAgreement() {
        LigandPrepComparison comparison = LigandPrepComparator.compare(
                ligand(List.of(
                        atomLine(1, "C1", 0, 0, 0, 0.0, "C"),
                        atomLine(2, "O1", 1.4, 0, 0, 0.0, "OA")), 0),
                ligand(List.of(
                        atomLine(1, "C", 0, 0, 0, 0.0, "C"),
                        atomLine(2, "C2", 1.4, 0, 0, 0.0, "C")), 0)
        );

        assertEquals(2, comparison.matchedHeavyAtoms());
        assertEquals(0.5, comparison.ad4TypeAgreement(), 1e-9);
    }

    @Test
    void hydrogensAreExcludedFromAlignment() {
        // Meeko merges non-polar hydrogens; ours keeps them. Heavy
        // atoms still align by coordinates.
        LigandPrepComparison comparison = LigandPrepComparator.compare(
                ligand(List.of(
                        atomLine(1, "C1", 0, 0, 0, 0.05, "C"),
                        atomLine(2, "H1", 0, 1, 0, 0.02, "HD"),
                        atomLine(3, "O1", 1.4, 0, 0, -0.30, "OA")), 1),
                ligand(List.of(
                        atomLine(1, "C", 0, 0, 0, 0.04, "C"),
                        atomLine(2, "O", 1.4, 0, 0, -0.31, "OA")), 1)
        );

        assertTrue(comparison.atomCountsMatch());
        assertEquals(2, comparison.ourHeavyAtoms());
        assertEquals(2, comparison.matchedHeavyAtoms());
        assertEquals(1.0, comparison.ad4TypeAgreement(), 1e-9);
    }

    @Test
    void nothingMatchedLeavesPerAtomMetricsAbsent() {
        LigandPrepComparison comparison = LigandPrepComparator.compare(
                ligand(List.of(
                        atomLine(1, "C1", 0, 0, 0, 0.0, "C")), 0),
                ligand(List.of(
                        atomLine(1, "C", 5, 5, 5, 0.0, "C")), 0)
        );

        assertEquals(0, comparison.matchedHeavyAtoms());
        assertNull(comparison.meanAbsChargeDelta());
        assertNull(comparison.ad4TypeAgreement());
        assertNull(comparison.maxCoordinateDelta());
        assertTrue(comparison.atomCountsMatch());
    }

    private static PdbqtLigand ligand(List<String> atomLines, int torsdof) {
        try {
            List<String> lines = new java.util.ArrayList<>();
            lines.add("ROOT");
            lines.addAll(atomLines);
            lines.add("ENDROOT");
            lines.add("TORSDOF " + torsdof);
            return PdbqtLigandReader.parse(lines);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
