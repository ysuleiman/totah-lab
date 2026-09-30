package totah.lab.aether;
import java.nio.file.*;
import java.util.*;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
public final class D3PbeProbe {
 public static void main(String[] args)throws Exception{
  String name=args[0];Path out=Path.of(args[1]),cache=Path.of(args[2]);Files.createDirectories(out);var d3=D3Dispersion.load();
  try(var heap=new HeapObservation("M16 "+name)){
   var main=solve(name,D3TestSupport.system(name),d3,cache,out);
   if(Integer.parseInt(D3TestSupport.index().get(name)[2])>0){var f=D3TestSupport.fragments(name);var a=solve(name+"-A",f.a().system(),d3,cache,out);var b=solve(name+"-B",f.b().system(),d3,cache,out);var interaction=PbeD3Interaction.combine(f,main,a,b);
    Files.writeString(out.resolve(name+"-interaction.txt"),"PBE="+interaction.pbeHartree()+"\nD3="+interaction.dispersionHartree()+"\nPBE_D3="+interaction.totalHartree()+"\nCP="+interaction.counterpoiseConvention()+"\nreceipt="+interaction.receiptHash()+"\nSCREENING_ONLY\n");}
   Files.writeString(out.resolve(name+"-heap.txt"),"peakHeap="+heap.peakUsedHeap()+"\n");
  }
 }
 private static PbeD3Energy solve(String name,QuantumSystem s,D3Dispersion d3,Path cache,Path out)throws Exception{
  var r=PbeScf.solve(s,BasisFamily.DEF2_SVP.forSystem(s),new PbeScf.Options(cache),x->System.out.println(name+" "+x));
  Files.writeString(out.resolve(name+".receipt"),r.receiptHash()+"\n"+r.iterations()+"\n");
  if(r.convergedState().isEmpty())throw new AssertionError(name+" "+r.convergenceStatus());
  var dispersion=d3.calculate(s);var value=PbeD3Energy.combine(s,r,dispersion);
  Files.writeString(out.resolve(name+".txt"),"PBE="+value.pbeHartree()+"\nD3_PAIRWISE="+value.pairwiseHartree()+"\nD3_THREE_BODY=NOT_IMPLEMENTED\nD3_TOTAL="+value.dispersionHartree()+"\nPBE_D3="+value.totalHartree()+"\niterations="+r.iterations().size()+"\npbeNanos="+r.totalNanos()+"\ndispersionNanos="+dispersion.elapsedNanos()+"\nreceipt="+value.receiptHash()+"\nSCREENING_ONLY\n");return value;
 }
}
