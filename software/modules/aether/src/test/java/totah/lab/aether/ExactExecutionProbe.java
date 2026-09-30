package totah.lab.aether;

import java.nio.file.*;
import java.util.*;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.provenance.ContentHash;

/** Serial M14 measurement driver. Writes only separately versioned evidence. */
public final class ExactExecutionProbe {
    private ExactExecutionProbe() {}
    public static void main(String[] args)throws Exception {
        String mode=args[0],name=args[1];
        var out=Path.of(System.getProperty("aether.exact.output","validation/milestone-14/parallel"));Files.createDirectories(out);
        if(mode.equals("blocks")){blocks();return;}
        if(mode.equals("contractions")){contractions(name);return;}
        if(mode.equals("density")) {
            var system=DiisReceiptReplay.systems().get(name);
            if(system==null)system=InteractionReceiptReplay.pairs().get(name).complex();
            DensityConstructionMeasurement.measure(name,system,BasisFamily.DEF2_SVP.forSystem(system));return;
        }
        var options=new ExactScf.Options(mode.equals("LDA")?ExactScf.Method.LDA_PZ81:ExactScf.Method.RHF,
                ExactScf.Integrals.valueOf(args.length>2?args[2]:"DIRECT_EXACT"),512,ConstructedDensity.Mode.CONSTRUCTION,8);
        try(var memory=new HeapObservation("M14 "+mode+" "+name)) {
            if(mode.equals("CP")) {
                var pair=InteractionReceiptReplay.pairs().get(name);var result=ExactCounterpoise.calculate(pair,BasisFamily.DEF2_SVP,options);
                var text=new StringBuilder("M14 CP "+name+"\nreceipt="+result.receiptHash()+"\n");
                var replay=new StringBuilder(result.receiptHash()).append('\n');
                for(var component:result.components()) {
                    text.append(component.role()).append(' ').append(component.calculation().convergenceStatus()).append(' ').append(component.calculation().performance()).append('\n');
                    replay.append(component.role()).append('\n').append(component.calculation().receiptHash()).append('\n').append(component.calculation().iterations()).append('\n');
                }
                Files.writeString(out.resolve(name+"-cp-"+options.integrals()+".receipt"),replay);
                Files.writeString(out.resolve(name+"-cp-"+options.integrals()+".txt"),text);
                double max=0;int entries=0;
                for(String line:reference("def2-cp-"+name).lines().skip(1).toList()) {
                    var c=line.split(",");double value;
                    if(c[0].equals("INTERACTION"))value=c[1].equals("uncorrected")?result.uncorrectedHartree().orElseThrow():result.counterpoiseHartree().orElseThrow();
                    else {var state=result.components().stream().filter(x->x.role().name().equals(c[0])).findFirst().orElseThrow().calculation().convergedState().orElseThrow();
                        value=switch(c[1]){case "converged"->1;case "total"->state.totalHartree();case "electronic"->state.electronicHartree();case "nuclear"->state.nuclearHartree();default->throw new AssertionError(c[1]);};}
                    double error=Math.abs(value-Double.parseDouble(c[2]));if(error>1e-8)throw new AssertionError(line+" actual="+value);max=Math.max(max,error);entries++;
                }
                text.append("uncorrected=").append(result.uncorrectedHartree()).append("\ncp=").append(result.counterpoiseHartree()).append("\nmaxReferenceError=").append(max)
                        .append("\nentries=").append(entries).append("\nelapsedNanos=").append(result.elapsedNanos()).append("\npeakHeap=").append(memory.peakUsedHeap()).append('\n');
                Files.writeString(out.resolve(name+"-cp-"+options.integrals()+".txt"),text);System.out.println(text);
            } else {
                var system=DiisReceiptReplay.systems().get(name);var basis=BasisFamily.DEF2_SVP.forSystem(system);
                var result=ExactScf.solve(system,basis,options);
                Files.writeString(out.resolve(name+"-"+mode+"-"+options.integrals()+".receipt"),result.receiptHash()+"\n"+result.convergenceStatus()+"\n"+result.reason()+"\n"+result.iterations()+"\n");
                var state=result.convergedState().orElseThrow(()->new AssertionError(result.convergenceStatus()+" "+result.reason()));
                var errors=new TreeMap<String,Double>();int entries=0;
                for(String line:reference("def2-energy-"+name).lines().skip(1).toList()) {
                    var c=line.split(",");if(!c[0].equals(mode))continue;int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]);
                    double value=switch(c[1]) {case "converged"->1;case "total"->state.totalHartree();case "electronic"->state.electronicHartree();
                        case "density"->state.density().get(i,j);case "Fock"->state.fock().get(i,j);case "orbital"->state.orbitals().energy(i);
                        case "electrons"->state.xc().orElseThrow().integratedElectrons();case "Exc"->state.xc().orElseThrow().energyHartree();default->throw new AssertionError(c[1]);};
                    double error=Math.abs(value-Double.parseDouble(c[4]));if(error>1e-8)throw new AssertionError(line+" actual="+value);errors.merge(c[1],error,Math::max);entries++;
                }
                String text="M14 "+name+" "+mode+" "+options.integrals()+"\nAO="+basis.size()+"\nstatus="+result.convergenceStatus()+"\niterations="+result.iterations().size()
                        +"\nenergy="+state.totalHartree()+"\nentries="+entries+"\nerrors="+errors+"\nperformance="+result.performance()+"\npeakHeap="+memory.peakUsedHeap()+"\nreceipt="+result.receiptHash()+"\n";
                Files.writeString(out.resolve(name+"-"+mode+"-"+options.integrals()+".txt"),text);
                System.out.println(text);
            }
        }
    }
    private static String reference(String name)throws Exception {
        byte[] bytes;String expected;
        try(var in=ExactExecutionProbe.class.getResourceAsStream("reference/"+name+".csv")){bytes=Objects.requireNonNull(in).readAllBytes();}
        try(var in=ExactExecutionProbe.class.getResourceAsStream("reference/"+name+".sha256")){expected=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII).trim();}
        if(!ContentHash.sha256(bytes).equals(expected))throw new AssertionError("Reference hash mismatch");
        return new String(bytes,java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static void blocks()throws Exception {
        var system=DiisReceiptReplay.systems().get("h2o");var basis=BasisFamily.DEF2_SVP.forSystem(system);int n=basis.size();
        var row=new ArrayList<Double>();for(int i=0;i<n;i++)for(int j=0;j<n;j++)row.add(i==j?1.0/n:0.0);
        var p=DensityMatrix.fromRowMajor(system,basis,row);
        for(int round=0;round<4;round++)for(int block:new int[]{128,512,2048}) {
            var evaluator=new BlockedXc(system,basis,new GridDefinition(120,590),block);var result=evaluator.evaluate(p,LdaFunctional.EXCHANGE_PZ81);
            System.out.println("BLOCK_TRIAL round="+round+" block="+block+" "+result.performance()+" energy="+result.energyHartree());
        }
    }
    private static void contractions(String name)throws Exception {
        var system=DiisReceiptReplay.systems().get(name);var basis=BasisFamily.DEF2_SVP.forSystem(system);int n=basis.size();
        var values=new ArrayList<Double>();for(int i=0;i<n;i++)for(int j=0;j<n;j++)values.add((i==j?1.0:.1)/n);
        var p=DensityMatrix.fromRowMajor(system,basis,values);var eri=new ElectronRepulsionCalculator(system,basis).calculate();
        JkCalculator.Result reference=null;
        for(int round=0;round<5;round++) {
            reference=JkCalculator.calculate(p,eri);var j=PackedCoulomb.calculate(p,eri);
            for(int i=0;i<n;i++)for(int k=0;k<n;k++)if(reference.coulomb().get(i,k)!=j.coulomb().get(i,k))throw new AssertionError("Coulomb changed");
            System.out.println("J_ONLY_TRIAL "+name+" round="+round+" jkNanos="+reference.performanceCounters().elapsedNanos()+" jOnlyNanos="+j.elapsedNanos());
        }
        var direct=new DirectExactJk(system,basis).calculate(p,DirectExactJk.Contraction.COULOMB_AND_EXCHANGE);double maxJ=0,maxK=0;
        for(int i=0;i<n;i++)for(int k=0;k<n;k++) {
            maxJ=Math.max(maxJ,Math.abs(reference.coulomb().get(i,k)-direct.coulomb().get(i,k)));
            maxK=Math.max(maxK,Math.abs(reference.exchange().get(i,k)-direct.exchange().orElseThrow().get(i,k)));
        }
        if(maxJ>1e-10||maxK>1e-10)throw new AssertionError("Direct contraction mismatch");
        System.out.println("DIRECT_CONTRACTION "+name+" maxJ="+maxJ+" maxK="+maxK+" "+direct.performance());
    }
}
