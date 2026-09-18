package totah.lab.athena.fragment.quantum;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import totah.lab.aether.matrix.FragmentInteractionCalculator;
import totah.lab.aether.model.FragmentPair;
import totah.lab.aether.provenance.ContentHash;

/** The only structural-to-physics execution boundary; unavailable chemistry never enters Aether. */
public final class FragmentFeatureService {
    private FragmentFeatureService() {}
    public static FragmentPhysicalFeatures calculate(FragmentCalculationPlan plan,Path cache,Consumer<String> progress) {
        var problems=new ArrayList<String>();problems.addAll(plan.left().unavailableReasons());problems.addAll(plan.right().unavailableReasons());
        Optional<FragmentInteractionCalculator.Result> result=Optional.empty();
        if(plan.left().quantum().isPresent()&&plan.right().quantum().isPresent())try {
            result=Optional.of(FragmentInteractionCalculator.calculate(new FragmentPair(plan.left().quantum().orElseThrow(),plan.right().quantum().orElseThrow()),cache,progress));
            problems.addAll(result.orElseThrow().failures());
            for(var c:result.orElseThrow().components())if(!c.scfStatus().equals("CONVERGED"))problems.add(c.method()+"/"+c.role()+":"+c.scfStatus());
        }catch(IOException|IllegalArgumentException|ArithmeticException exception){problems.add("CALCULATION_UNAVAILABLE:"+exception.getClass().getSimpleName()+":"+exception.getMessage());}
        var rhf=result.isPresent()?result.orElseThrow().rhfCpHartree():OptionalDouble.empty();
        var pbe=result.isPresent()?result.orElseThrow().pbeCpHartree():OptionalDouble.empty();
        var d3=result.isPresent()?result.orElseThrow().d3DeltaHartree():OptionalDouble.empty();
        var combined=result.isPresent()?result.orElseThrow().pbeD3CpHartree():OptionalDouble.empty();
        var validity=FragmentPhysicalFeatures.validity(plan);
        String hash=ContentHash.sha256("athena-physical-features-18-1\n"+plan.receiptHash()+"\n"+result.map(FragmentInteractionCalculator.Result::receiptHash).orElse("UNAVAILABLE")
                +"\n"+validity+"\n"+problems+"\nSCREENING_ONLY");
        return new FragmentPhysicalFeatures(plan,rhf,pbe,d3,combined,validity,result,problems,hash);
    }

    public record Aggregate(String channel,String interactionClass,List<String> componentIds,List<String> componentReceipts,
                            int contactCount,int unavailableContactCount,OptionalDouble sumRhfCp,OptionalDouble sumPbeCp,
                            OptionalDouble sumD3,OptionalDouble sumPbeD3Cp,OptionalDouble minimumPbeD3Cp,
                            int rhfAvailableCount,int pbeAvailableCount,int d3AvailableCount,int pbeD3AvailableCount) {
        public Aggregate{componentIds=List.copyOf(componentIds);componentReceipts=List.copyOf(componentReceipts);}
    }
    public static List<Aggregate> aggregate(List<FragmentPhysicalFeatures> input) {
        var groups=new TreeMap<String,List<FragmentPhysicalFeatures>>();var seen=new HashSet<String>();
        for(var row:input) {
            if(!seen.add(row.plan().id()))throw new IllegalArgumentException("Duplicate calculation in aggregation");
            groups.computeIfAbsent(row.plan().channel()+"/ALL",k->new ArrayList<>()).add(row);
            groups.computeIfAbsent(row.plan().channel()+"/"+row.interactionClass(),k->new ArrayList<>()).add(row);
        }
        var output=new ArrayList<Aggregate>();
        for(var group:groups.entrySet()) {
            var rows=group.getValue().stream().sorted(Comparator.comparing(r->r.plan().id())).toList();
            double[] sums=new double[4];int[] counts=new int[4];double minimum=Double.POSITIVE_INFINITY;
            for(var row:rows) {
                var values=List.of(row.rhfCp(),row.pbeCp(),row.d3Delta(),row.pbeD3Cp());
                for(int i=0;i<4;i++)if(values.get(i).isPresent()){sums[i]+=values.get(i).getAsDouble();counts[i]++;if(!Double.isFinite(sums[i]))throw new ArithmeticException("Nonfinite aggregate");}
                if(row.pbeD3Cp().isPresent())minimum=Math.min(minimum,row.pbeD3Cp().getAsDouble());
            }
            var key=group.getKey().split("/");
            output.add(new Aggregate(key[0],key[1],rows.stream().map(r->r.plan().id()).toList(),rows.stream().map(FragmentPhysicalFeatures::receiptHash).toList(),
                    rows.size(),(int)rows.stream().filter(r->r.rhfCp().isEmpty()||r.pbeCp().isEmpty()||r.d3Delta().isEmpty()||r.pbeD3Cp().isEmpty()).count(),value(sums[0],counts[0]),value(sums[1],counts[1]),value(sums[2],counts[2]),value(sums[3],counts[3]),
                    counts[3]==0?OptionalDouble.empty():OptionalDouble.of(minimum),counts[0],counts[1],counts[2],counts[3]));
        }
        return List.copyOf(output);
    }
    private static OptionalDouble value(double sum,int count){return count==0?OptionalDouble.empty():OptionalDouble.of(sum);}
}
