package totah.lab.topology;

import org.junit.jupiter.api.Test;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpatialClashChecker buckets atoms on a grid and flags candidate positions
 * closer than a threshold to any bucketed atom.
 */
public class SpatialClashCheckerTest {

    private static Atom atomAt(double x, double y, double z) {
        return Atom.builder()
                .name("C")
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.fromSymbol("C"))
                .build();
    }

    @Test
    public void emptyCheckerReportsNoClash() {
        SpatialClashChecker checker = new SpatialClashChecker(2.0);
        assertFalse(checker.hasClash(new Point3D(0, 0, 0), 1.0),
                "empty checker must never clash");
    }

    @Test
    public void atomWithinThresholdClashes() {
        SpatialClashChecker checker = new SpatialClashChecker(2.0);
        checker.addAtom(atomAt(0.5, 0.5, 0.5));
        assertTrue(checker.hasClash(new Point3D(1.0, 0.5, 0.5), 1.0),
                "0.5 A separation must clash against a 1.0 A threshold");
    }

    @Test
    public void atomBeyondThresholdDoesNotClash() {
        SpatialClashChecker checker = new SpatialClashChecker(2.0);
        checker.addAtom(atomAt(0.5, 0.5, 0.5));
        assertFalse(checker.hasClash(new Point3D(3.0, 0.5, 0.5), 1.0),
                "2.5 A separation must not clash against a 1.0 A threshold");
    }

    @Test
    public void atomExactlyAtThresholdDoesNotClash() {
        SpatialClashChecker checker = new SpatialClashChecker(2.0);
        checker.addAtom(atomAt(0.5, 0.5, 0.5));
        assertFalse(checker.hasClash(new Point3D(1.5, 0.5, 0.5), 1.0),
                "the threshold comparison must be strict (<), not <=");
    }

    @Test
    public void clashIsDetectedAcrossGridCellBoundary() {
        SpatialClashChecker checker = new SpatialClashChecker(2.0);
        checker.addAtom(atomAt(1.9, 0.5, 0.5));  // bucket x=0
        assertTrue(checker.hasClash(new Point3D(2.1, 0.5, 0.5), 1.0),
                "atoms in the neighboring bucket must still be found");
    }

    @Test
    public void negativeCoordinatesAreBucketedCorrectly() {
        SpatialClashChecker checker = new SpatialClashChecker(2.0);
        checker.addAtom(atomAt(-0.5, -0.5, -0.5));
        assertTrue(checker.hasClash(new Point3D(0.4, -0.5, -0.5), 1.0),
                "0.9 A separation near the origin must clash");
        assertFalse(checker.hasClash(new Point3D(-0.5, -0.5, -3.5), 1.0),
                "3.0 A separation must not clash");
    }

    @Test
    public void farAwayAtomsAreIgnored() {
        SpatialClashChecker checker = new SpatialClashChecker(2.0);
        checker.addAtom(atomAt(100, 100, 100));
        assertFalse(checker.hasClash(new Point3D(0, 0, 0), 1.0),
                "atoms many buckets away must not clash");
    }

    @Test
    public void clashIsFoundWhenAtomXAndZShareNoBucket() {
        SpatialClashChecker checker = new SpatialClashChecker(2.0);
        checker.addAtom(atomAt(4.2, 0.5, 0.5));
        // Real distance is 0.4 A: this must clash, but the atom is bucketed
        // under z = floor(4.2/2) = 2 while the query scans z in {-1,0,1}
        assertTrue(checker.hasClash(new Point3D(3.8, 0.5, 0.5), 1.0),
                "0.4 A separation must clash regardless of bucket layout");
    }
}
