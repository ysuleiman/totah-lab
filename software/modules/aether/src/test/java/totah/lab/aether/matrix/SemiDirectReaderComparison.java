package totah.lab.aether.matrix;

import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;

/** Pilot instrumentation only: includes mmap RSS cost; never claims a flushed cold OS cache. */
public final class SemiDirectReaderComparison {
    private SemiDirectReaderComparison(){}
    public static void readerOnly(QuantumSystem system,List<ContractedGaussian> basis,Path root,boolean mapped)throws Exception {
        var creation=EriDiskCache.openOrCreate(root,basis,8);if(creation.generated())throw new AssertionError("Reader-only pilot requires existing cache");
        var values=new ArrayList<Double>();for(int i=0;i<basis.size();i++)for(int j=0;j<basis.size();j++)values.add((i==j?1.0:.1)/basis.size());
        var density=DensityMatrix.fromRowMajor(system,basis,values);
        try(var jk=new SemiDirectJk(system,creation.cache(),mapped)) {
            for(int round=0;round<5;round++){var r=jk.calculate(density);System.out.println("READER_ONLY round="+round+" mapped="+mapped+" totalNanos="+r.elapsedNanos()+" "+r.scan()+" hash="+r.receiptHash());}
        }
    }
    public static void run(QuantumSystem system,List<ContractedGaussian> basis,Path root)throws Exception {
        var creation=EriDiskCache.openOrCreate(root,basis,8);var cache=creation.cache();
        System.out.println("CACHE identity="+cache.identity()+" bytes="+cache.payloadBytes()+" generationCount="+creation.eriGenerationCount()
                +" generationNanos="+creation.generationNanos()+" writeNanos="+creation.writeNanos()+" verificationNanos="+creation.verificationNanos()+" totalNanos="+creation.totalNanos());
        int n=basis.size();var values=new ArrayList<Double>();for(int i=0;i<n;i++)for(int j=0;j<n;j++)values.add((i==j?1.0:.1)/n);
        var density=DensityMatrix.fromRowMajor(system,basis,values);String hash=null;SemiDirectJk.Result last=null;
        try(var channelReader=new SemiDirectJk(system,cache,false);var mappedReader=new SemiDirectJk(system,cache,true)) {
        for(int round=0;round<5;round++)for(boolean mapped:round%2==0?new boolean[]{false,true}:new boolean[]{true,false}) {
            var jk=mapped?mappedReader:channelReader; {
                var result=jk.calculate(density);if(hash==null)hash=result.receiptHash();else if(!hash.equals(result.receiptHash()))throw new AssertionError("Reader changed J/K bits");
                last=result;System.out.println("READER round="+round+" backend="+(mapped?"MMAP":"FILE_CHANNEL")+" totalNanos="+result.elapsedNanos()+" "+result.scan()+" hash="+hash);
            }
        }
        }
        // Independent frozen lambda/sigma contraction order; mmap is confined to this test oracle.
        double maxJ=0,maxK=0;long[] counters=new long[2];
        try(var file=FileChannel.open(cache.directory().resolve("eri.bin"),StandardOpenOption.READ)) {
            var mapped=file.map(FileChannel.MapMode.READ_ONLY,0,cache.payloadBytes()).order(ByteOrder.BIG_ENDIAN);
            for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                final int a=i,b=j;
                double jr=JkCalculator.contract(density,(k,l)->mapped.getDouble(Math.toIntExact(EriDiskCache.offset(a,b,k,l))),counters,0);
                double kr=JkCalculator.contract(density,(k,l)->mapped.getDouble(Math.toIntExact(EriDiskCache.offset(a,k,b,l))),counters,1);
                maxJ=Math.max(maxJ,Math.abs(jr-last.coulomb().get(i,j)));maxK=Math.max(maxK,Math.abs(kr-last.exchange().get(i,j)));
            }
        }
        if(maxJ>1e-10||maxK>1e-10)throw new AssertionError("Packed contraction disagreement");
        System.out.println("CONTRACTION_ORACLE maxJ="+maxJ+" maxK="+maxK+" SCREENING_ONLY");
    }
}
