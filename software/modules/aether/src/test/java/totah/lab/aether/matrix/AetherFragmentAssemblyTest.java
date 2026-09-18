package totah.lab.aether.matrix;

import java.util.*;
import org.junit.jupiter.api.Test;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherFragmentAssemblyTest {
    private static FragmentPair pair() {
        return new FragmentPair(new MolecularFragment("A",new QuantumSystem(List.of(new NuclearCenter(new Point3D(0,0,0),1),new NuclearCenter(new Point3D(1.4,0,0),1)),0,1)),
                new MolecularFragment("B",new QuantumSystem(List.of(new NuclearCenter(new Point3D(0,5,0),1),new NuclearCenter(new Point3D(1.4,5,0),1)),0,1)));
    }
    private static List<FragmentInteractionCalculator.Component> components(FragmentPair pair) {
        var result=new ArrayList<FragmentInteractionCalculator.Component>();
        for(String method:List.of("RHF","PBE"))for(int i=0;i<3;i++) {
            var system=i==0?pair.complex():i==1?pair.a().system():pair.b().system();
            result.add(new FragmentInteractionCalculator.Component(method,List.of("AB","A_GHOST_B","B_GHOST_A").get(i),"CONVERGED",OptionalDouble.of(i==0?-2.1:-1),"synthetic-test-receipt",
                    IntegralMatrixData.systemHash(system),"synthetic-cache","synthetic-cache",false,true,"TEST_ONLY",List.of(),""));
        }
        return result;
    }
    @Test void maxIterationsMakesPbeUnavailableButKeepsIndependentD3() {
        var pair=pair();var rows=components(pair);var old=rows.get(3);
        rows.set(3,new FragmentInteractionCalculator.Component(old.method(),old.role(),"MAX_ITERATIONS",OptionalDouble.of(-2.1),old.receiptHash(),old.systemHash(),old.requestedCacheIdentity(),old.canonicalCacheIdentity(),false,true,"TEST_ONLY",List.of(),""));
        var result=FragmentInteractionCalculator.assemble(pair,rows,OptionalDouble.of(-.01),List.of("TEST_D3_AB","TEST_D3_A","TEST_D3_B"),List.of());
        assertTrue(result.rhfCpHartree().isPresent());assertTrue(result.pbeCpHartree().isEmpty());assertTrue(result.pbeD3CpHartree().isEmpty());
        assertEquals(-.01,result.d3DeltaHartree().orElseThrow());
    }
    @Test void allMethodsAssembleSeparatelyAndWrongSystemIsRejected() {
        var pair=pair();var rows=components(pair);
        var result=FragmentInteractionCalculator.assemble(pair,rows,OptionalDouble.of(-.01),List.of("TEST_D3_AB","TEST_D3_A","TEST_D3_B"),List.of());
        assertEquals(-.1,result.rhfCpHartree().orElseThrow(),1e-15);assertEquals(-.11,result.pbeD3CpHartree().orElseThrow(),1e-15);
        var old=rows.getFirst();rows.set(0,new FragmentInteractionCalculator.Component(old.method(),old.role(),old.scfStatus(),old.energyHartree(),old.receiptHash(),"WRONG_SYSTEM",old.requestedCacheIdentity(),old.canonicalCacheIdentity(),false,true,"TEST_ONLY",List.of(),""));
        assertThrows(IllegalArgumentException.class,()->FragmentInteractionCalculator.assemble(pair,rows,OptionalDouble.empty(),List.of(),List.of()));
    }
}
