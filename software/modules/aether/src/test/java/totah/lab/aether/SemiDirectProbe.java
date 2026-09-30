package totah.lab.aether;

import java.nio.file.*;
import java.util.*;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.provenance.ContentHash;

/** Bounded M14.1 pilot/benchmark driver. M14 references and outputs are never rewritten. */
public final class SemiDirectProbe {
    public static void main(String[] args)throws Exception {
        String mode=args[0],name=args[1];Path output=Path.of(args[2]);Files.createDirectories(output);
        Path caches=Path.of(args[3]);var options=new SemiDirectScf.Options(caches);
        try(var memory=new HeapObservation("M14.1 "+mode+" "+name)) {
            if(mode.equals("READ_CHANNEL")||mode.equals("READ_MMAP")) {
                var system=DiisReceiptReplay.systems().get(name);SemiDirectReaderComparison.readerOnly(system,BasisFamily.DEF2_SVP.forSystem(system),caches,mode.equals("READ_MMAP"));return;
            }
            if(mode.equals("READERS")) {
                var system=DiisReceiptReplay.systems().get(name);SemiDirectReaderComparison.run(system,BasisFamily.DEF2_SVP.forSystem(system),caches);return;
            }
            if(mode.equals("RHF")) {
                var system=DiisReceiptReplay.systems().get(name);var result=SemiDirectScf.solve(system,BasisFamily.DEF2_SVP.forSystem(system),options,System.out::println);
                var state=result.convergedState().orElseThrow(()->new AssertionError(result.convergenceStatus()));
                var errors=new TreeMap<String,Double>();int entries=0;
                for(String line:reference("def2-energy-"+name).lines().skip(1).toList()) {
                    var c=line.split(",");if(!c[0].equals("RHF"))continue;int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]);
                    double value=switch(c[1]){case "converged"->1;case "total"->state.totalHartree();case "electronic"->state.electronicHartree();case "density"->state.density().get(i,j);case "Fock"->state.fock().get(i,j);case "orbital"->state.orbitals().energy(i);default->throw new AssertionError(c[1]);};
                    double error=Math.abs(value-Double.parseDouble(c[4]));if(error>1e-8)throw new AssertionError(line+" actual="+value);errors.merge(c[1],error,Math::max);entries++;
                }
                String text="M14.1 RHF "+name+"\nenergy="+state.totalHartree()+"\nerrors="+errors+"\nentries="+entries+"\n"+metrics(result)+"\npeakHeap="+memory.peakUsedHeap()+"\nreceipt="+result.receiptHash()+"\n";
                Files.writeString(output.resolve(name+"-RHF.txt"),text);Files.writeString(output.resolve(name+"-RHF.receipt"),result.receiptHash()+"\n"+result.iterations()+"\n");System.out.println(text);
            }else if(mode.equals("CP")) {
                var result=SemiDirectCounterpoise.calculate(InteractionReceiptReplay.pairs().get(name),BasisFamily.DEF2_SVP,options,System.out::println);
                double max=0;int entries=0;
                for(String line:reference("def2-cp-"+name).lines().skip(1).toList()) {
                    var c=line.split(",");double value;
                    if(c[0].equals("INTERACTION"))value=c[1].equals("uncorrected")?result.uncorrectedHartree():result.counterpoiseHartree();
                    else {var state=result.components().stream().filter(x->x.role().name().equals(c[0])).findFirst().orElseThrow().calculation().convergedState().orElseThrow();
                        value=switch(c[1]){case "converged"->1;case "total"->state.totalHartree();case "electronic"->state.electronicHartree();case "nuclear"->state.nuclearHartree();default->throw new AssertionError(c[1]);};}
                    double error=Math.abs(value-Double.parseDouble(c[2]));if(error>1e-8)throw new AssertionError(line+" actual="+value);max=Math.max(max,error);entries++;
                }
                var text=new StringBuilder("M14.1 CP "+name+"\nuncorrected="+result.uncorrectedHartree()+"\ncp="+result.counterpoiseHartree()+"\nmaxReferenceError="+max+"\nentries="+entries+"\ntotalNanos="+result.totalNanos()+"\n");
                var receipt=new StringBuilder(result.receiptHash()).append('\n');
                for(var c:result.components()){text.append(c.role()).append(' ').append(metrics(c.calculation())).append('\n');receipt.append(c.role()).append('\n').append(c.calculation().receiptHash()).append('\n').append(c.calculation().iterations()).append('\n');}
                text.append("peakHeap=").append(memory.peakUsedHeap()).append("\nreceipt=").append(result.receiptHash()).append('\n');
                Files.writeString(output.resolve(name+"-CP.txt"),text);Files.writeString(output.resolve(name+"-CP.receipt"),receipt);System.out.println(text);
            }else throw new IllegalArgumentException(mode);
        }
    }
    private static String metrics(SemiDirectScf.Result r) {
        var c=r.cache();var scans=r.scans();return "status="+r.convergenceStatus()+" ao="+c.cache().dimension()+" iterations="+r.iterations().size()
                +" cacheIdentity="+c.cache().identity()+" cacheBytes="+c.cache().payloadBytes()+" generated="+c.generated()+" eriGenerationCount="+c.eriGenerationCount()
                +" generationNanos="+c.generationNanos()+" writeNanos="+c.writeNanos()+" cacheVerificationNanos="+c.verificationNanos()+" cacheOpenNanos="+c.totalNanos()
                +" readNanos="+scans.stream().mapToLong(EriDiskCache.Scan::readNanos).sum()+" pageVerificationNanos="+scans.stream().mapToLong(EriDiskCache.Scan::verificationNanos).sum()
                +" bytesRead="+scans.stream().mapToLong(EriDiskCache.Scan::bytes).sum()+" jkNanos="+r.jkNanos()+" scfTotalNanos="+r.totalNanos();
    }
    private static String reference(String name)throws Exception {
        byte[] bytes;String hash;
        try(var in=SemiDirectProbe.class.getResourceAsStream("reference/"+name+".csv")){bytes=Objects.requireNonNull(in).readAllBytes();}
        try(var in=SemiDirectProbe.class.getResourceAsStream("reference/"+name+".sha256")){hash=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII).trim();}
        if(!ContentHash.sha256(bytes).equals(hash))throw new AssertionError("Frozen reference changed");return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
    }
}
