package totah.lab.aether.matrix;

import java.util.*;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.*;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherConstructionEvidenceTest {
    @Test void immutableSolverEvidenceAndProvenanceGuards()throws Exception {
        var system=new QuantumSystem(List.of(new NuclearCenter(new Point3D(0,0,0),1),new NuclearCenter(new Point3D(0,0,1.4),1)),0,1);
        var basis=BasisFamily.DEF2_SVP.forSystem(system);var s=OverlapMatrix.compute(basis);var h=new CoreHamiltonianCalculator(system,basis).calculate();
        var solution=OneShotNumerics.solve(DiisRhfScf.matrix(s.size(),s::get),DiisRhfScf.matrix(s.size(),h::get));
        var orbitals=ValidatedOrbitals.fromSolve(system,s,solution,h.receipt().receiptHash());double old=orbitals.coefficient(0,0);
        solution.coefficients().setEntry(0,0,99);assertEquals(old,orbitals.coefficient(0,0));
        var fast=ConstructedDensity.build(system,s,orbitals,ConstructedDensity.Mode.CONSTRUCTION);
        var audit=ConstructedDensity.build(system,s,orbitals,ConstructedDensity.Mode.AUDIT);
        assertEquals(fast.density().densityHash(),audit.density().densityHash());assertTrue(fast.idempotencyResidual().isEmpty());assertTrue(audit.idempotencyResidual().isPresent());
        var reversed=new ArrayList<>(basis);Collections.reverse(reversed);var wrongS=OverlapMatrix.compute(reversed);
        assertThrows(IllegalArgumentException.class,()->ConstructedDensity.build(system,wrongS,orbitals,ConstructedDensity.Mode.CONSTRUCTION));
        var wrongSystem=new QuantumSystem(system.nuclei(),2,1);
        assertThrows(IllegalArgumentException.class,()->ConstructedDensity.build(wrongSystem,s,orbitals,ConstructedDensity.Mode.CONSTRUCTION));
    }
}
