package totah.lab.aether;

import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import static org.junit.jupiter.api.Assertions.*;

/** Separate setup measurements for the smaller systems, avoiding another complete SCF run. */
class AetherDef2ProfileTest {
    @Test void smallerSystemSetupProfile() throws Exception {
        for(String name:System.getProperty("aether.def2.profileSystems","h2 h2o nh3 ch4 co n2 h2s ph3 hcl ch3cl").split(" ")) {
            var system=DiisReceiptReplay.systems().get(name);var basis=Def2SvpBasis.load().forSystem(system);
            if(Boolean.getBoolean("aether.def2.aoOnly")) {
                var grid=MolecularGrid.build(system,new GridDefinition(120,590));long start=System.nanoTime();var ao=new AoGrid(grid,basis);
                assertEquals(basis.size(),ao.basisSize());
                System.out.println("M13 AO_PROFILE "+name+" gridPoints="+grid.size()+" aoNanos="+(System.nanoTime()-start)+" aoHash="+ao.receiptHash());
                continue;
            }
            var run=RhfScfCalculator.solve(system,basis,1,ScfPolicy.DIIS);
            var metrics=run.basisPerformance().orElseThrow();assertEquals(basis.size(),metrics.basisFunctions());
            long start=System.nanoTime();var grid=MolecularGrid.build(system,new GridDefinition(120,590));
            long gridNanos=System.nanoTime()-start;start=System.nanoTime();var ao=new AoGrid(grid,basis);long aoNanos=System.nanoTime()-start;
            assertEquals(basis.size(),ao.basisSize());
            System.out.println("M13 SETUP "+name+" "+metrics+" gridPoints="+grid.size()+" gridNanos="+gridNanos+" aoNanos="+aoNanos);
        }
    }
}
