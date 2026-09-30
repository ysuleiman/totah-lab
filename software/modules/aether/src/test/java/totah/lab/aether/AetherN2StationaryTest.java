package totah.lab.aether;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherN2StationaryTest {
    @Test void delayedDiisPreservesPlainWarmupAndFindsValidatedRoot() throws Exception {
        var system=DiisReceiptReplay.systems().get("n2");var basis=Sto3gBasis.load().forSystem(system);
        var plain=RhfScfCalculator.solve(system,basis);var diis=RhfScfCalculator.solve(system,basis,ScfPolicy.DIIS);
        var end=diis.convergedState().orElseThrow();assertEquals(plain.convergedState().orElseThrow().energy().totalHartree(),end.energy().totalHartree(),1e-8);
        assertTrue(diis.receipt().protocol().contains("start=32;"));
        for(int i=0;i<31;i++) {
            assertEquals(plain.iterations().get(i).inputDensity().density().densityHash(),diis.iterations().get(i).density().densityHash());
            assertFalse(diis.iterations().get(i).update().orElseThrow().extrapolated());
        }
        assertTrue(end.energyCriterionPassed());assertTrue(end.densityCriterionPassed());
        var s=OverlapMatrix.compute(basis);double pi=0;
        for(int i:new int[]{3,4,8,9})for(int j=0;j<basis.size();j++)pi+=end.density().get(i,j)*s.get(j,i);
        assertEquals(4,pi,1e-8);
    }
    @Test void n2RotationAndPermutationDoNotSelectBadRoot() throws Exception {
        var system=DiisReceiptReplay.systems().get("n2");var basis=Sto3gBasis.load().forSystem(system);
        var reference=DiisReceiptReplay.solve("n2").convergedState().orElseThrow();
        var reversed=new ArrayList<>(basis);Collections.reverse(reversed);
        var permutation=RhfScfCalculator.solve(system,reversed,ScfPolicy.DIIS).convergedState().orElseThrow();
        assertEquals(reference.energy().totalHartree(),permutation.energy().totalHartree(),1e-8);
        for(int i=0;i<basis.size();i++)for(int j=0;j<basis.size();j++)assertEquals(reference.density().get(i,j),permutation.density().get(basis.size()-1-i,basis.size()-1-j),1e-8);
        var nuclei=system.nuclei().stream().map(n->{var p=n.centerBohr();return new NuclearCenter(new Point3D(.6*p.x()+2,.8*p.x()-1,p.z()+.3),n.charge());}).toList();
        var moved=new QuantumSystem(nuclei,0,1);var rotation=RhfScfCalculator.solve(moved,Sto3gBasis.load().forSystem(moved),ScfPolicy.DIIS).convergedState().orElseThrow();
        assertEquals(reference.energy().totalHartree(),rotation.energy().totalHartree(),1e-8);
    }
}
