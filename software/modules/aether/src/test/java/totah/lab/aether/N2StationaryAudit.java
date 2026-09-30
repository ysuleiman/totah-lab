package totah.lab.aether;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.*;

/** Diagnostic export only; preserves the unmodified plain and DIIS numerical trajectories. */
public final class N2StationaryAudit {
    private interface Entry { double get(int i,int j); }
    private static void matrix(StringBuilder b,String policy,int iteration,String label,int n,int cols,Entry e) {
        for(int i=0;i<n;i++)for(int j=0;j<cols;j++)b.append(policy).append(',').append(iteration).append(',').append(label).append(',').append(i).append(',').append(j).append(',').append(e.get(i,j)).append('\n');
    }
    public static void main(String[] args)throws Exception {
        var folder=Path.of(args[0]);Files.createDirectories(folder);
        var system=DiisReceiptReplay.systems().get("n2");var basis=Sto3gBasis.load().forSystem(system);int n=basis.size();
        var plain=RhfScfCalculator.solve(system,basis);var diis=RhfScfCalculator.solve(system,basis,ScfPolicy.DIIS);
        if(!diis.receipt().protocol().contains("start=2;"))throw new IllegalStateException("Historical export requires the Milestone-10.1 DIIS implementation; do not overwrite preserved evidence with a later protocol");
        var s=OverlapMatrix.compute(basis);var core=new CoreHamiltonianCalculator(system,basis).calculate();
        var zero=DensityMatrix.fromRowMajor(system,basis,Collections.nCopies(n*n,0.0));
        var guess=OneShotRhfCalculator.solve(zero,s,core,JkCalculator.calculate(zero,new ElectronRepulsionCalculator(system,basis).calculate()));
        var data=new StringBuilder("policy,iteration,quantity,i,j,value\n");
        matrix(data,"initial",0,"S",n,n,s::get);matrix(data,"initial",0,"C",n,n,guess.coefficients()::get);
        matrix(data,"initial",0,"eps",n,1,(i,j)->guess.energies().get(i));
        matrix(data,"initial",0,"P",n,n,plain.initialDensity().orElseThrow().density()::get);
        var metrics=new StringBuilder("policy,iteration,energy,delta_energy,density_residual\n");
        for(var r:plain.iterations()) {
            matrix(data,"plain",r.number(),"P",n,n,r.inputDensity().density()::get);matrix(data,"plain",r.number(),"nextP",n,n,r.outputDensity().density()::get);
            matrix(data,"plain",r.number(),"F",n,n,r.orbitals().fock()::get);matrix(data,"plain",r.number(),"C",n,n,r.orbitals().coefficients()::get);
            matrix(data,"plain",r.number(),"eps",n,1,(i,j)->r.orbitals().energies().get(i));
            var e=r.receipt();metrics.append("plain,").append(r.number()).append(',').append(e.totalEnergy()).append(',').append(e.deltaEnergy()).append(',').append(e.densityResidual()).append('\n');
        }
        for(var r:diis.iterations()) {
            matrix(data,"diis",r.number(),"P",n,n,r.density()::get);matrix(data,"diis",r.number(),"nextP",n,n,r.nextDensity()::get);
            matrix(data,"diis",r.number(),"F",n,n,r.physicalOrbitals().fock()::get);matrix(data,"diis",r.number(),"C",n,n,r.physicalOrbitals().coefficients()::get);
            matrix(data,"diis",r.number(),"eps",n,1,(i,j)->r.physicalOrbitals().energies().get(i));
            metrics.append("diis,").append(r.number()).append(',').append(r.energy().totalHartree()).append(',').append(r.deltaEnergy()).append(',').append(r.densityResidual()).append('\n');
        }
        Files.writeString(folder.resolve("n2-matrices.csv"),data);Files.writeString(folder.resolve("n2-trajectories.csv"),metrics);
        Files.writeString(folder.resolve("n2-plain.receipt"),plain.receipt().toString());Files.writeString(folder.resolve("n2-diis-bad.receipt"),diis.receipt().toString());
    }
}
