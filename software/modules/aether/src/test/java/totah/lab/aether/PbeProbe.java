package totah.lab.aether;

import java.nio.file.*;
import java.util.*;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;

/** New M15 driver: no writes to prior milestone evidence. */
public final class PbeProbe {
    public static void main(String[] args)throws Exception {
        String name=args[0],transform=args[1];int nr=Integer.parseInt(args[2]),na=Integer.parseInt(args[3]);Path output=Path.of(args[4]),cache=Path.of(args[5]);Files.createDirectories(output);
        String stem=name+"-"+transform+"-"+nr+"-"+na;
        boolean reverse=transform.equals("permuted");var system=PbeTestSupport.system(name,reverse?"native":transform);
        var basis=new ArrayList<>(BasisFamily.DEF2_SVP.forSystem(system));if(reverse)Collections.reverse(basis);
        try(var memory=new HeapObservation("M15 "+stem)) {
            var r=PbeScf.solve(system,basis,new PbeScf.Options(cache,new GridDefinition(nr,na),256,8),System.out::println);
            if(r.convergenceStatus()!=RhfScfResult.Status.CONVERGED){Files.writeString(output.resolve(stem+".failure"),r.convergenceStatus()+"\n"+r.iterations());throw new AssertionError(r.convergenceStatus());}
            var state=r.convergedState().orElseThrow();var errors=new TreeMap<String,Double>();int entries=0;
            for(var line:PbeTestSupport.reference(name+"-"+(reverse?"native":transform)+"-"+nr+"-"+na).lines().skip(1).toList()) {
                var c=line.split(",");int i=Integer.parseInt(c[1]),j=Integer.parseInt(c[2]);int ai=reverse?basis.size()-1-i:i,aj=reverse?basis.size()-1-j:j;
                double v=switch(c[0]){case "total"->state.totalHartree();case "electronic"->state.electronicHartree();case "Exc"->state.xc().energyHartree();case "electrons"->state.xc().integratedElectrons();case "density"->state.density().get(ai,aj);case "Fock"->state.fock().get(ai,aj);case "Vxc"->state.xc().potential().get(ai,aj);case "orbital"->state.orbitals().energy(i);default->throw new AssertionError(c[0]);};
                double error=Math.abs(v-Double.parseDouble(c[3]));errors.merge(c[0],error,Math::max);entries++;
            }
            long ao=0,derivative=0,rho=0,xc=0,vxc=0,blocks=0;
            for(var p:r.gridMeasurements()){ao+=p.aoNanos();derivative+=p.derivativeNanos();rho+=p.densityGradientNanos();xc+=p.functionalNanos();vxc+=p.potentialNanos();blocks=Math.max(blocks,p.blockStorageEstimateBytes());}
            var text="M15 KS_PBE "+stem+"\nstatus="+r.convergenceStatus()+"\nenergy="+state.totalHartree()+"\nelectronic="+state.electronicHartree()+"\nExc="+state.xc().energyHartree()+"\nelectrons="+state.xc().integratedElectrons()+"\nerrors="+errors+"\nentries="+entries+"\naoCount="+basis.size()+"\niterations="+r.iterations().size()
                    +"\ncacheGenerated="+r.cache().generated()+"\ncacheSetupNanos="+r.cache().totalNanos()+"\naoNanos="+ao+"\nderivativeNanos="+derivative+"\nrhoGradientNanos="+rho+"\nfunctionalNanos="+xc+"\nVxcNanos="+vxc+"\nJNanos="+r.jNanos()+"\neigensolveNanos="+r.eigensolveNanos()+"\ntotalNanos="+r.totalNanos()+"\nblockStorageEstimate="+blocks+"\npeakHeap="+memory.peakUsedHeap()+"\nreceipt="+r.receiptHash()+"\nSCREENING_ONLY\n";
            Files.writeString(output.resolve(stem+".txt"),text);Files.writeString(output.resolve(stem+".receipt"),r.receiptHash()+"\n"+r.iterations()+"\n");System.out.println(text);
            for(var e:errors.entrySet())if(e.getValue()>1e-8)throw new AssertionError("Reference mismatch "+e);
        }
    }
}
