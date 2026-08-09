package totah.lab.daedalus.ligandprep;

import org.junit.jupiter.api.Test;
import totah.lab.daedalus.ligandprep.LigandPrepComparator.LigandPrepComparison;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void identicalTorsdofWithDifferentRotorBondsIsAMismatch() {
        // Same TORSDOF (1), but the rotatable bond is a different bond:
        // identity sets must catch it even though the counts agree.
        LigandPrepComparison comparison = LigandPrepComparator.compare(
                ligandWithBranches(List.of(
                        atomLine(1, "C1", 0, 0, 0, 0.0, "C"),
                        atomLine(2, "C2", 1.5, 0, 0, 0.0, "C"),
                        atomLine(3, "C3", 3.0, 0, 0, 0.0, "C")),
                        List.of(new int[]{1, 2}), 1),
                ligandWithBranches(List.of(
                        atomLine(1, "C1", 0, 0, 0, 0.0, "C"),
                        atomLine(2, "C2", 1.5, 0, 0, 0.0, "C"),
                        atomLine(3, "C3", 3.0, 0, 0, 0.0, "C")),
                        List.of(new int[]{2, 3}), 1)
        );

        assertEquals(0, comparison.torsdofDelta());
        assertEquals(1, comparison.rotorsOurs());
        assertEquals(1, comparison.rotorsMeeko());
        assertEquals(0, comparison.rotorsMatched());
        assertTrue(LigandPrepComparator.rotorSetsDiffer(comparison));
    }

    @Test
    void identicalRotorSetsMatchAcrossFileOrder() {
        LigandPrepComparison comparison = LigandPrepComparator.compare(
                ligandWithBranches(List.of(
                        atomLine(1, "C1", 0, 0, 0, 0.0, "C"),
                        atomLine(2, "C2", 1.5, 0, 0, 0.0, "C")),
                        List.of(new int[]{1, 2}), 1),
                // Same bond, reversed serial order in the other file.
                ligandWithBranches(List.of(
                        atomLine(1, "C2", 1.5, 0, 0, 0.0, "C"),
                        atomLine(2, "C1", 0, 0, 0, 0.0, "C")),
                        List.of(new int[]{1, 2}), 1)
        );

        assertEquals(1, comparison.rotorsMatched());
        assertFalse(LigandPrepComparator.rotorSetsDiffer(comparison));
    }

    private static PdbqtModel ligandWithBranches(
            List<String> atomLines, List<int[]> branches, int torsdof) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("ROOT");
        lines.addAll(atomLines);
        lines.add("ENDROOT");
        for (int[] branch : branches) {
            lines.add("BRANCH " + branch[0] + " " + branch[1]);
            lines.add("ENDBRANCH " + branch[0] + " " + branch[1]);
        }
        lines.add("TORSDOF " + torsdof);
        return readModel(lines);
    }

    private static PdbqtModel ligand(List<String> atomLines, int torsdof) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("ROOT");
        lines.addAll(atomLines);
        lines.add("ENDROOT");
        lines.add("TORSDOF " + torsdof);
        return readModel(lines);
    }

    private static PdbqtModel readModel(List<String> lines) {
        try {
            return new PdbqtReader()
                    .read(new java.io.StringReader(
                            String.join("\n", lines)))
                    .firstModel();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String atomLine(
            int serial,
            String name,
            double x,
            double y,
            double z,
            double charge,
            String ad4Type
    ) {
        return String.format(
                java.util.Locale.ROOT,
                "ATOM  %5d %-4s %3s %1s%4d    "
                        + "%8.3f%8.3f%8.3f%6.2f%6.2f    %6.3f %-2s",
                serial, name, "LIG", "L", 1,
                x, y, z, 1.0, 0.0, charge, ad4Type);
    }
}
