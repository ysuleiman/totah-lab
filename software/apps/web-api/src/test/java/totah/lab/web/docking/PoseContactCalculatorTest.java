package totah.lab.web.docking;

import org.junit.jupiter.api.Test;
import totah.lab.web.docking.PoseContactCalculator.PocketAtomPoint;
import totah.lab.web.docking.PoseContactCalculator.ResidueContact;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoseContactCalculatorTest {

    @Test
    void countsPairsAndKeepsMinimumDistancePerResidue() {
        List<PocketAtomPoint> pocketAtoms = List.of(
                new PocketAtomPoint(0.0, 0.0, 0.0, 11),
                new PocketAtomPoint(0.0, 2.0, 0.0, 11),
                new PocketAtomPoint(10.0, 0.0, 0.0, 22));

        // (1,0,0): 1.0 and ~2.24 from residue 11, 9.0 from residue 22.
        // (3.9,0,0): 3.9 from residue 11's first atom (within 4.0),
        //            ~4.39 from its second atom and 6.1 from residue
        //            22 (both outside).
        List<ResidueContact> contacts = PoseContactCalculator.compute(
                List.of(
                        new double[]{1.0, 0.0, 0.0},
                        new double[]{3.9, 0.0, 0.0}),
                pocketAtoms);

        assertEquals(1, contacts.size());
        ResidueContact contact = contacts.get(0);
        assertEquals(11, contact.residueId());
        assertEquals(3, contact.atomContactCount());
        assertEquals(1.0, contact.minDistance(), 1.0e-9);
    }

    @Test
    void cutoffIsInclusiveAtFourAngstrom() {
        List<ResidueContact> contacts = PoseContactCalculator.compute(
                List.of(new double[]{4.0, 0.0, 0.0}),
                List.of(new PocketAtomPoint(0.0, 0.0, 0.0, 7)));

        assertEquals(1, contacts.size());
        assertEquals(4.0, contacts.get(0).minDistance(), 1.0e-9);
    }

    @Test
    void contactsAreSortedByResidueIdAndEmptyInputsYieldNone() {
        List<ResidueContact> contacts = PoseContactCalculator.compute(
                List.of(new double[]{0.5, 0.0, 0.0}),
                List.of(new PocketAtomPoint(0.0, 0.0, 0.0, 9),
                        new PocketAtomPoint(0.0, 0.0, 0.5, 3)));

        assertEquals(List.of(3L, 9L),
                contacts.stream().map(ResidueContact::residueId).toList());

        assertTrue(PoseContactCalculator.compute(
                List.of(), List.of(
                        new PocketAtomPoint(0.0, 0.0, 0.0, 1))).isEmpty());
        assertTrue(PoseContactCalculator.compute(
                List.of(new double[]{0.0, 0.0, 0.0}),
                List.of()).isEmpty());
    }
}
