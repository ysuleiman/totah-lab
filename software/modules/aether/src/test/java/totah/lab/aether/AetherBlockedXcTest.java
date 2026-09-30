package totah.lab.aether;

import org.junit.jupiter.api.Test;
import totah.lab.aether.matrix.*;
import static org.junit.jupiter.api.Assertions.*;

class AetherBlockedXcTest {
    @Test void identicalCanonicalGridAndPointwiseSumsAcrossBlockBoundaries()throws Exception {
        var densities=DftFixtures.densities();
        for(String name:new String[]{"h2","h2o"}) {
            var p=densities.get(name);var definition=new GridDefinition(120,590);
            var grid=MolecularGrid.build(p.system(),definition);var ao=new AoGrid(grid,p.functions());
            for(var functional:LdaFunctional.values()) {
                var dense=XcIntegration.evaluate(ao,p,functional);
                for(int block:new int[]{128,512,2048}) for(int workers:new int[]{1,8}) {
                    try(
                    var blocked=new BlockedXc(p.system(),p.functions(),definition,block,workers)) {
                    var actual=blocked.evaluate(p,functional);
                    assertEquals(grid.receiptHash(),blocked.gridReceiptHash());
                    assertEquals(dense.integratedElectrons(),actual.integratedElectrons());
                    assertEquals(dense.energyHartree(),actual.energyHartree());
                    for(int i=0;i<p.size();i++)for(int j=0;j<p.size();j++)assertEquals(dense.potential().get(i,j),actual.potential().get(i,j));
                    assertEquals(actual.receiptHash(),blocked.evaluate(p,functional).receiptHash());
                    assertEquals((workers==1?1:2*workers)*block*(8L*(p.size()+4)+48L),actual.performance().blockArrayBytes());
                    }
                }
            }
        }
    }
    @Test void def2CartesianGridMatchesDenseForTheSameSuppliedDensity()throws Exception {
        var system=DftFixtures.densities().get("h2o").system();
        var basis=totah.lab.aether.basis.BasisFamily.DEF2_SVP.forSystem(system);
        var entries=new java.util.ArrayList<Double>();int n=basis.size();
        for(int i=0;i<n;i++)for(int j=0;j<n;j++)entries.add((i==j?1.0:.03)/n);
        var p=DensityMatrix.fromRowMajor(system,basis,entries);var definition=new GridDefinition(120,590);
        var dense=XcIntegration.evaluate(new AoGrid(MolecularGrid.build(system,definition),basis),p,LdaFunctional.EXCHANGE_PZ81);
        try(var blocked=new BlockedXc(system,basis,definition,512,8)) {
            var actual=blocked.evaluate(p,LdaFunctional.EXCHANGE_PZ81);
            assertEquals(dense.integratedElectrons(),actual.integratedElectrons());
            assertEquals(dense.energyHartree(),actual.energyHartree());
            for(int i=0;i<n;i++)for(int j=0;j<n;j++)assertEquals(dense.potential().get(i,j),actual.potential().get(i,j));
        }
    }
    @Test void incompatibleSystemFailsClosed()throws Exception {
        var p=DftFixtures.densities();var h=p.get("h2");
        var blocked=new BlockedXc(h.system(),h.functions(),new GridDefinition(40,110),128);
        assertThrows(IllegalArgumentException.class,()->blocked.evaluate(p.get("h2o"),LdaFunctional.EXCHANGE));
    }
    @Test void coulombOnlyIsBitIdenticalToReferenceJ()throws Exception {
        for(var p:DftFixtures.densities().values()) {
            var eri=new ElectronRepulsionCalculator(p.system(),p.functions()).calculate();
            var reference=JkCalculator.calculate(p,eri);var actual=PackedCoulomb.calculate(p,eri);
            for(int i=0;i<p.size();i++)for(int j=0;j<p.size();j++)assertEquals(reference.coulomb().get(i,j),actual.coulomb().get(i,j));
            assertEquals(reference.performanceCounters().jAccumulations(),actual.eriSlotsRead());
        }
    }
}
