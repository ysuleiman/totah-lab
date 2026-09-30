package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;

/** Isolates the identical occupied density sum plus legacy checks versus construction evidence. */
public final class DensityConstructionMeasurement {
    private DensityConstructionMeasurement() {}
    public static void measure(String name,QuantumSystem system,List<ContractedGaussian> basis) {
        var s=OverlapMatrix.compute(basis);var h=new CoreHamiltonianCalculator(system,basis).calculate();
        var solution=OneShotNumerics.solve(DiisRhfScf.matrix(s.size(),s::get),DiisRhfScf.matrix(s.size(),h::get));
        var orbitals=ValidatedOrbitals.fromSolve(system,s,solution,h.receipt().receiptHash());
        for(int round=0;round<4;round++) {
            long start=System.nanoTime();var old=OccupiedDensityCalculator.construct(system,s,s.size(),s.size(),orbitals::coefficient,orbitals::energy);long oldNanos=System.nanoTime()-start;
            var current=ConstructedDensity.build(system,s,orbitals,ConstructedDensity.Mode.CONSTRUCTION);
            if(!old.density().densityHash().equals(current.density().densityHash()))throw new AssertionError("Density changed");
            System.out.println("DENSITY_TRIAL "+name+" ao="+s.size()+" round="+round+" oldNanos="+oldNanos+" newNanos="+current.elapsedNanos()+" measuredIdempotency="+old.idempotency());
        }
    }
}
