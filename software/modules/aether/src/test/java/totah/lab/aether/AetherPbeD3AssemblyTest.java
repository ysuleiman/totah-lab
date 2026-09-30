package totah.lab.aether;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;
public class AetherPbeD3AssemblyTest {
 @TempDir Path cache;
 @Test void typedAdditionAndProvenanceFailClosed()throws Exception {
  var system=D3TestSupport.system("h2o");var pbe=PbeScf.solve(system,BasisFamily.DEF2_SVP.forSystem(system),new PbeScf.Options(cache),x->{});
  assertTrue(pbe.convergedState().isPresent());String before=pbe.receiptHash();var d3=D3Dispersion.load();var correction=d3.calculate(system);
  var total=PbeD3Energy.combine(system,pbe,correction);assertSame(pbe,total.pbe());assertEquals(before,pbe.receiptHash());
  assertEquals(pbe.convergedState().orElseThrow().totalHartree()+correction.totalHartree(),total.totalHartree());
  assertTrue(total.threeBodyHartree().isEmpty());
  assertThrows(IllegalArgumentException.class,()->PbeD3Energy.combine(system,pbe,d3.calculate(D3TestSupport.system("dms"))));
  var moved=new QuantumSystem(system.nuclei().stream().map(n->{var p=n.centerBohr();return new NuclearCenter(new Point3D(p.x()+.2,p.y(),p.z()),n.charge());}).toList(),system.molecularCharge(),1);
  assertThrows(IllegalArgumentException.class,()->PbeD3Energy.combine(moved,pbe,correction));
  var incomplete=new PbeScf.Result(RhfScfResult.Status.MAX_ITERATIONS,pbe.iterations(),pbe.state(),pbe.cache(),pbe.gridMeasurements(),pbe.jNanos(),pbe.eigensolveNanos(),pbe.totalNanos(),pbe.receiptHash());
  assertThrows(IllegalArgumentException.class,()->PbeD3Energy.combine(system,incomplete,correction));
 }
 @Test void ghostCentersNeverContributeDispersion()throws Exception {
  var fragments=D3TestSupport.fragments("water_dimer");var ghosts=GhostBasis.withDonor(fragments.a().system(),fragments.b().system(),BasisFamily.DEF2_SVP);var d3=D3Dispersion.load();
  var real=d3.calculate(fragments.a().system());var withGhosts=d3.calculate(ghosts.system());
  assertEquals(real.receiptHash(),withGhosts.receiptHash());assertEquals(3,withGhosts.pairs().size());assertEquals(15,d3.calculate(fragments.complex()).pairs().size());
 }
}
