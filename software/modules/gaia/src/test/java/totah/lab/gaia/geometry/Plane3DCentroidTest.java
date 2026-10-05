package totah.lab.gaia.geometry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Plane3DCentroidTest {
    @Test void rejectsNullEmptyAndNullMember() {
        assertThrows(NullPointerException.class,()->Plane3D.centroidOf(null));
        assertThrows(IllegalArgumentException.class,()->Plane3D.centroidOf(List.of()));
        assertThrows(NullPointerException.class,()->Plane3D.centroidOf(Arrays.asList(new Point3D(0,0,0),null)));
    }
    @Test void rejectsNonfiniteAtPointBoundaryAndAccumulationOverflow() {
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->Plane3D.centroidOf(List.of(new Point3D(bad,0,0))));
        assertThrows(IllegalArgumentException.class,()->Plane3D.centroidOf(List.of(new Point3D(Double.MAX_VALUE,0,0),new Point3D(Double.MAX_VALUE,0,0))));
    }
    @Test void coincidentCollinearAndSingletonDoNotRequirePlane() {
        var p=new Point3D(2,0,0);assertEquals(p,Plane3D.centroidOf(List.of(p)));
        assertEquals(p,Plane3D.centroidOf(List.of(p,p,p)));
        assertEquals(p,Plane3D.centroidOf(List.of(new Point3D(0,0,0),p,new Point3D(4,0,0))));
    }
    @Test void ordinaryOrderingTranslationAndPreservation() {
        var p=new ArrayList<>(List.of(new Point3D(0,0,0),new Point3D(3,0,0),new Point3D(0,3,0)));
        var original=List.copyOf(p);var c=Plane3D.centroidOf(p);
        assertEquals(new Point3D(1,1,0),c);assertEquals(original,p);
        assertEquals(Plane3D.fit(p).centroid(),c);Collections.reverse(p);assertEquals(c,Plane3D.centroidOf(p));
        var shift=new Vector3D(10,20,30);assertEquals(c.add(shift),Plane3D.centroidOf(p.stream().map(x->x.add(shift)).toList()));
    }
}
