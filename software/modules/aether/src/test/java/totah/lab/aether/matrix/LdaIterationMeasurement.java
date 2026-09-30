package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;

/** Fixed-input one-shot timing, not an SCF trajectory or a converged-state claim. */
public final class LdaIterationMeasurement {
    private LdaIterationMeasurement() {}
    public static void measure(String name,QuantumSystem system,List<ContractedGaussian> basis)throws IOException {
        var overlap=OverlapMatrix.compute(basis);var core=new CoreHamiltonianCalculator(system,basis).calculate();
        var s=DiisRhfScf.matrix(basis.size(),overlap::get);
        var guess=OneShotNumerics.solve(s,DiisRhfScf.matrix(basis.size(),core::get));
        var orbitals=ValidatedOrbitals.fromSolve(system,overlap,guess,core.receipt().receiptHash());
        var density=ConstructedDensity.build(system,overlap,orbitals,ConstructedDensity.Mode.CONSTRUCTION).density();
        try(var direct=new DirectExactJk(system,basis,8);
            var grid=new BlockedXc(system,basis,new GridDefinition(120,590),512,8)) {
            for(int round=0;round<5;round++) {
                Step both,only;
                if(round%2==0) {
                    both=step(system,basis,overlap,core,s,density,direct,grid,DirectExactJk.Contraction.COULOMB_AND_EXCHANGE);
                    only=step(system,basis,overlap,core,s,density,direct,grid,DirectExactJk.Contraction.COULOMB_ONLY);
                } else {
                    only=step(system,basis,overlap,core,s,density,direct,grid,DirectExactJk.Contraction.COULOMB_ONLY);
                    both=step(system,basis,overlap,core,s,density,direct,grid,DirectExactJk.Contraction.COULOMB_AND_EXCHANGE);
                }
                if(!both.densityHash().equals(only.densityHash())||!both.rhoHash().equals(only.rhoHash())
                        ||Double.doubleToLongBits(both.energy())!=Double.doubleToLongBits(only.energy()))
                    throw new AssertionError("Discarded K changed the LDA state");
                for(int i=0;i<basis.size();i++)for(int j=0;j<basis.size();j++)
                    if(Double.doubleToLongBits(both.j().get(i,j))!=Double.doubleToLongBits(only.j().get(i,j))
                            ||Double.doubleToLongBits(both.fock().get(i,j))!=Double.doubleToLongBits(only.fock().get(i,j)))
                        throw new AssertionError("J/Fock changed");
                if(only.kAccumulations()!=0)throw new AssertionError("Unexpected exchange work");
                System.out.println("LDA_ITERATION_TRIAL "+name+" ao="+basis.size()+" round="+round
                        +" withDiscardedKContractionNanos="+both.jkNanos()+" jOnlyContractionNanos="+only.jkNanos()
                        +" withDiscardedKStepNanos="+both.totalNanos()+" jOnlyStepNanos="+only.totalNanos()
                        +" stateBitIdentical=true density="+only.densityHash()+" SCREENING_ONLY");
            }
        }
    }
    private static Step step(QuantumSystem system,List<ContractedGaussian> basis,OverlapMatrix overlap,
                             CoreHamiltonianMatrix core,org.apache.commons.math3.linear.RealMatrix s,DensityMatrix density,DirectExactJk direct,
                             BlockedXc grid,DirectExactJk.Contraction contraction) {
        long start=System.nanoTime();var jk=direct.calculate(density,contraction);long jkNanos=System.nanoTime()-start;
        var xc=grid.evaluate(density,LdaFunctional.EXCHANGE_PZ81);
        // The baseline deliberately computes and discards K; both paths use the same pure-LDA equation.
        var f=new ExecutionMatrix(ExecutionMatrix.Kind.FOCK,basis,density.systemHash(),density.densityHash(),
                "M14-fixed-input-LDA-timing",core.receipt().receiptHash()+jk.receiptHash()+xc.receiptHash(),
                (i,j)->MeanFieldArithmetic.ksFock(core.get(i,j),jk.coulomb().get(i,j),xc.potential().get(i,j)));
        var solved=OneShotNumerics.solve(s,DiisRhfScf.matrix(basis.size(),f::get));
        var c=ValidatedOrbitals.fromSolve(system,overlap,solved,f.receiptHash());
        var next=ConstructedDensity.build(system,overlap,c,ConstructedDensity.Mode.CONSTRUCTION);
        double energy=MeanFieldArithmetic.ksEnergy(density,core::get,jk.coulomb()::get,xc.energyHartree());
        return new Step(jk.coulomb(),f,next.density().densityHash(),xc.densityGridHash(),energy,jkNanos,
                System.nanoTime()-start,jk.performance().kAccumulations());
    }
    private record Step(ExecutionMatrix j,ExecutionMatrix fock,String densityHash,String rhoHash,double energy,
                        long jkNanos,long totalNanos,long kAccumulations) {}
}
