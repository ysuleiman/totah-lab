// Review only: call the existing qualified geometry without changing it.
import totah.lab.gaia.geometry.*;
import java.util.*;
public class ExistingGeometryProbe {
 public static void main(String[] args) {
  Scanner s=new Scanner(System.in).useLocale(Locale.ROOT);
  while(s.hasNextDouble()) {
   Point3D[] p=new Point3D[4];
   for(int i=0;i<4;i++)p[i]=new Point3D(s.nextDouble(),s.nextDouble(),s.nextDouble());
   try {double d=Dihedral.measureDegrees(p[0],p[1],p[2],p[3]); System.out.println(Double.toString(d));}
   catch(IllegalArgumentException e){System.out.println("UNDEFINED");}
  }
 }
}
