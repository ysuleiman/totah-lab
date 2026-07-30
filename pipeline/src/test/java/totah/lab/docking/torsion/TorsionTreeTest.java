package totah.lab.docking.torsion;


import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TorsionTreeTest {

    @Test
    void testRootAtomsAndBranches() {
        TorsionTree tree = new TorsionTree();
        // ROOT atoms
        tree.addRootAtom(1); // CA
        tree.addRootAtom(2); // HA
        assertTrue(tree.containsAtom(1));
        assertTrue(tree.containsAtom(2));
        assertFalse(tree.containsAtom(99));

        // Create branch CA-CB
        TorsionBranch chi1 = new TorsionBranch(
                1,
                3,
                List.of(3, 4)   // CB, SG
        );
        // Create child branch CB-CG
        TorsionBranch chi2 = new TorsionBranch(
                3,
                5,
                List.of(5, 6)   // CG, CD
        );
        chi1.getChildren().add(chi2);
        tree.addRootBranch(chi1);

        // Root atoms
        assertTrue(tree.containsAtom(1));
        assertTrue(tree.containsAtom(2));

        // First branch atoms
        assertTrue(tree.containsAtom(3));
        assertTrue(tree.containsAtom(4));

        // Nested branch atoms
        assertTrue(tree.containsAtom(5));
        assertTrue(tree.containsAtom(6));

        // Not present
        assertFalse(tree.containsAtom(10));
    }


    @Test
    void testFlattenBranches() {
        TorsionTree tree = new TorsionTree();
        TorsionBranch chi1 = new TorsionBranch(
                1,
                2,
                List.of(2, 3)
        );
        TorsionBranch chi2 = new TorsionBranch(
                2,
                4,
                List.of(4, 5)
        );
        chi1.getChildren().add(chi2);
        tree.addRootBranch(chi1);

        List<TorsionBranch> branches = tree.flattenBranches();
        assertEquals(2, branches.size());
        assertSame(chi1, branches.get(0));
        assertSame(chi2, branches.get(1));
    }
}
