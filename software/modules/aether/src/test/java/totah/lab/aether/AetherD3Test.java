package totah.lab.aether;
import org.junit.jupiter.api.*;
import java.util.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;
public class AetherD3Test {
 @TestFactory Collection<DynamicTest> oracle()throws Exception {
  var tests=new ArrayList<DynamicTest>();var d3=D3Dispersion.load();
  for(var entry:D3TestSupport.index().entrySet())tests.add(DynamicTest.dynamicTest(entry.getKey(),()->{
   String name=entry.getKey();var r=d3.calculate(D3TestSupport.system(name));String csv=D3TestSupport.read(name+".csv");
   assertEquals(entry.getValue()[6],ContentHash.sha256(csv));
   assertEquals(Double.parseDouble(entry.getValue()[5]),r.totalHartree(),2e-12);
   var lines=csv.lines().skip(1).toList();assertEquals(lines.size(),r.pairs().size());
   for(int k=0;k<lines.size();k++){var c=lines.get(k).split(",");var p=r.pairs().get(k);assertEquals(Integer.parseInt(c[0]),p.i());assertEquals(Integer.parseInt(c[1]),p.j());
    assertEquals(Double.parseDouble(c[2]),p.distanceBohr(),1e-13);assertEquals(Double.parseDouble(c[3]),p.c6(),2e-8);assertEquals(Double.parseDouble(c[4]),p.c8(),1e-6);assertEquals(Double.parseDouble(c[5]),p.hartree(),2e-12);}
   assertEquals(ScientificStatus.SCREENING_ONLY,r.status());assertTrue(r.threeBodyHartree().isEmpty());assertEquals(r.receiptHash(),d3.calculate(D3TestSupport.system(name)).receiptHash());
  }));return tests;
 }
 @TestFactory Collection<DynamicTest> interactionOracle()throws Exception {
  var tests=new ArrayList<DynamicTest>();var d3=D3Dispersion.load();
  for(var line:D3TestSupport.read("interaction.csv").lines().skip(1).toList()) {
   var c=line.split(",");tests.add(DynamicTest.dynamicTest("interaction "+c[0],()->{
    var f=D3TestSupport.fragments(c[0]);var ab=d3.calculate(f.complex());var a=d3.calculate(f.a().system());var b=d3.calculate(f.b().system());
    assertEquals(Double.parseDouble(c[1]),ab.totalHartree(),2e-12);assertEquals(Double.parseDouble(c[2]),a.totalHartree(),2e-12);assertEquals(Double.parseDouble(c[3]),b.totalHartree(),2e-12);
    assertEquals(Double.parseDouble(c[4]),(ab.totalHartree()-a.totalHartree())-b.totalHartree(),2e-12);
    if(c[0].equals("water_dimer")) {
     int cut=f.a().system().nuclei().size();double cross=ab.pairs().stream().filter(p->p.i()>=cut&&p.j()<cut).mapToDouble(D3Dispersion.Pair::hartree).sum();
     assertTrue(Math.abs(cross-Double.parseDouble(c[4]))>1e-15,"CN response changes intramolecular pair energies too");
    }
   }));
  }return tests;
 }
 @Test void coordinationAndDampingAnalyticLimits()throws Exception {
  var d3=D3Dispersion.load();double bohr=(6.62607015e-34/(2*StrictMath.PI))/(9.1093837015e-31*299792458.0*7.2973525693e-3)*1e10;
  double r=(4.0/3)*(.32/bohr)*2;
  var h=new NuclearCenter(new Point3D(0,0,0),1);
  var equal=d3.calculate(new QuantumSystem(List.of(h,new NuclearCenter(new Point3D(r,0,0),1)),0,1));
  assertEquals(.5,equal.coordinationNumbers().get(0),1e-15);assertEquals(.5,equal.coordinationNumbers().get(1),1e-15);
  var p=equal.pairs().getFirst();double radius=.4289*Math.sqrt(p.c8()/p.c6())+4.4407;
  assertEquals(1/(1+Math.pow(radius/r,6)),p.damping6(),1e-16);
  assertEquals(1/(1+Math.pow(radius/r,8)),p.damping8(),1e-16);
  for(double distance:new double[]{40,40.0001,60,60.0001}) {
   var q=d3.calculate(new QuantumSystem(List.of(h,new NuclearCenter(new Point3D(distance,0,0),1)),0,1));
   if(distance>40)assertEquals(0,q.coordinationNumbers().get(0));
   assertEquals(distance<=60?1:0,q.pairs().size());
  }
  var lone=d3.calculate(new QuantumSystem(List.of(h),1,1));assertEquals(0,lone.totalHartree());assertEquals(0,lone.coordinationNumbers().get(0));
 }
 @Test void invarianceAndClosedFailure()throws Exception {
  var d3=D3Dispersion.load();var s=D3TestSupport.system("benzene_dimer");var r=d3.calculate(s);var reverse=new ArrayList<>(s.nuclei());Collections.reverse(reverse);
  assertEquals(r.totalHartree(),d3.calculate(new QuantumSystem(reverse,0,1)).totalHartree(),1e-14);
  var moved=s.nuclei().stream().map(n->{var p=n.centerBohr();return new NuclearCenter(new Point3D(-p.y()+1,p.x()-2,p.z()+.7),n.charge());}).toList();
  assertEquals(r.totalHartree(),d3.calculate(new QuantumSystem(moved,0,1)).totalHartree(),1e-14);
  var one=new NuclearCenter(new Point3D(0,0,0),1);assertThrows(IllegalArgumentException.class,()->d3.calculate(new QuantumSystem(List.of(one,one),0,1)));
  assertThrows(UnsupportedOperationException.class,()->r.pairs().clear());
 }
}
