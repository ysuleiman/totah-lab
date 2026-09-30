package totah.lab.aether;
import java.nio.file.*;
import java.util.*;
import totah.lab.aether.matrix.D3Dispersion;
public final class D3EvidenceProbe {
 public static void main(String[] args)throws Exception {
  if(Runtime.version().feature()!=21)throw new IllegalStateException("Java21 required");
  Path out=Path.of(args[0]);Files.createDirectories(out);var d3=D3Dispersion.load();var receipt=new StringBuilder("D3-M16;SCREENING_ONLY\n"+D3Dispersion.PROTOCOL+"\n");
  var rows=new ArrayList<String>();rows.add("system,pair_count,pair_error,total_error,c6_error,c8_error,cold_nanos,median_warm_nanos");
  for(var entry:D3TestSupport.index().entrySet()){
   String name=entry.getKey();var system=D3TestSupport.system(name);var r=d3.calculate(system);double pair=0,c6=0,c8=0;var lines=D3TestSupport.read(name+".csv").lines().skip(1).toList();
   for(int i=0;i<lines.size();i++){var c=lines.get(i).split(",");var p=r.pairs().get(i);pair=Math.max(pair,Math.abs(p.hartree()-Double.parseDouble(c[5])));c6=Math.max(c6,Math.abs(p.c6()-Double.parseDouble(c[3])));c8=Math.max(c8,Math.abs(p.c8()-Double.parseDouble(c[4])));}
   double total=Math.abs(r.totalHartree()-Double.parseDouble(entry.getValue()[5]));long[] times=new long[101];
   for(int k=0;k<times.length;k++){var next=d3.calculate(system);if(!r.receiptHash().equals(next.receiptHash()))throw new AssertionError("Replay "+name);times[k]=next.elapsedNanos();}Arrays.sort(times);
   rows.add(name+","+r.pairs().size()+","+pair+","+total+","+c6+","+c8+","+r.elapsedNanos()+","+times[50]);
   receipt.append(name).append('\n').append(r.receiptHash()).append('\n').append(r.coordinationNumbers()).append('\n').append(r.pairs()).append('\n').append(r.totalHartree()).append('\n');
   if(pair>2e-12||total>2e-12||c6>2e-8||c8>1e-6)throw new AssertionError("Oracle mismatch "+name);
  }
  Files.write(out.resolve("dispersion.csv"),rows);Files.writeString(out.resolve("dispersion.receipt"),receipt.toString());
 }
}
