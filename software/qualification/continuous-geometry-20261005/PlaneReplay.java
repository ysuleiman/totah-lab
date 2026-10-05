import java.util.*;
import totah.lab.gaia.geometry.*;
/** Independent-JVM historical solver comparison, raw hexadecimal output. */
public class PlaneReplay {
    public static void main(String[] args) {
        var random=new Random(732091L);
        for(int k=0;k<128;k++) {
            var points=new ArrayList<Point3D>();
            for(int i=0;i<3+k%13;i++)points.add(new Point3D(random.nextDouble()*10-5,random.nextDouble()*10-5,random.nextDouble()*10-5));
            emit(points);
        }
        emit(List.of(new Point3D(1,0,0),new Point3D(-1,0,0),new Point3D(0,1,0),new Point3D(0,-1,0),new Point3D(0,0,1),new Point3D(0,0,-1)));
        emit(List.of(new Point3D(0,0,0),new Point3D(1,0,0),new Point3D(2,0,0)));
        emit(List.of(new Point3D(0,0,0),new Point3D(0,0,0),new Point3D(0,0,0)));
        emit(List.of(new Point3D(0,0,0)));
    }
    static void emit(List<Point3D> points) {
        try {var p=Plane3D.fit(points);System.out.println(List.of(p.centroid().x(),p.centroid().y(),p.centroid().z(),p.normal().x(),p.normal().y(),p.normal().z()).stream().map(Double::toHexString).toList());}
        catch(Exception e){System.out.println(e.getClass().getName()+":"+e.getMessage());}
    }
}
