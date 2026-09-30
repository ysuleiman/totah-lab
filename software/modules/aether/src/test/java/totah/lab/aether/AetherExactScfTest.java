package totah.lab.aether;

import java.util.*;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import static org.junit.jupiter.api.Assertions.*;

class AetherExactScfTest {
    @Test void referenceAndDirectConvergeToSameRHFAndLdaState()throws Exception {
        var system=DftFixtures.densities().get("h2o").system();var basis=Def2SvpBasis.load().forSystem(system);
        for(var method:new ExactScf.Method[]{ExactScf.Method.RHF,ExactScf.Method.LDA_PZ81}) {
            ExactScf.Result reference=null;
            for(var policy:ExactScf.Integrals.values()) {
                var result=ExactScf.solve(system,basis,new ExactScf.Options(method,policy,512,ConstructedDensity.Mode.CONSTRUCTION,8));
                assertEquals(RhfScfResult.Status.CONVERGED,result.convergenceStatus(),result.reason());
                for(var row:result.iterations()) {
                    assertEquals(ConstructedDensity.IdempotencyStatus.GUARANTEED_BY_CONSTRUCTION_EXACT_ARITHMETIC,row.idempotencyStatus());
                    assertTrue(row.idempotencyResidual().isEmpty());assertEquals(10,row.tracePS(),1e-10);
                }
                var state=result.state().orElseThrow();
                if(reference==null)reference=result;
                else {
                    var expected=reference.state().orElseThrow();assertEquals(expected.totalHartree(),state.totalHartree(),1e-9);
                    for(int i=0;i<basis.size();i++) {
                        assertEquals(expected.orbitals().energy(i),state.orbitals().energy(i),1e-8);
                        for(int j=0;j<basis.size();j++) {
                            assertEquals(expected.density().get(i,j),state.density().get(i,j),1e-8);
                            assertEquals(expected.fock().get(i,j),state.fock().get(i,j),1e-8);
                        }
                    }
                }
                System.out.println("M14 WATER "+method+" "+policy+" iterations="+result.iterations().size()+" energy="+state.totalHartree()+" "+result.performance());
            }
        }
    }
    @Test void auditMeasuresRatherThanInventingZeroAndPreservesDensity()throws Exception {
        var p=DftFixtures.densities().get("h2");
        var normal=ExactScf.solve(p.system(),p.functions(),new ExactScf.Options(ExactScf.Method.RHF,ExactScf.Integrals.PACKED_REFERENCE,512,ConstructedDensity.Mode.CONSTRUCTION));
        var audit=ExactScf.solve(p.system(),p.functions(),new ExactScf.Options(ExactScf.Method.RHF,ExactScf.Integrals.PACKED_REFERENCE,512,ConstructedDensity.Mode.AUDIT));
        assertEquals(RhfScfResult.Status.CONVERGED,audit.convergenceStatus());
        assertEquals(normal.state().orElseThrow().density().densityHash(),audit.state().orElseThrow().density().densityHash());
        for(var row:audit.iterations()){assertEquals(ConstructedDensity.IdempotencyStatus.NUMERICALLY_AUDITED,row.idempotencyStatus());assertTrue(row.idempotencyResidual().isPresent());}
        assertNotEquals(audit.receiptHash(),normal.receiptHash());
    }
}
