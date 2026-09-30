package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.FragmentPair;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Shares only exact full-basis ERIs; constructs every molecular state independently. */
public final class SemiDirectCounterpoise {
    private SemiDirectCounterpoise(){}
    public static Result calculate(FragmentPair pair,BasisFamily family,SemiDirectScf.Options options,Consumer<String> progress)throws IOException {
        long start=System.nanoTime();var basis=family.forSystem(pair.complex());var rows=new ArrayList<Component>();
        // Prove both ghost function sets before computing any component.
        var ga=GhostBasis.withDonor(pair.a().system(),pair.b().system(),family);
        var gb=GhostBasis.withDonor(pair.b().system(),pair.a().system(),family);
        SemiDirectScf.requireSameFunctions(ga.functions(),basis);SemiDirectScf.requireSameFunctions(gb.functions(),basis);
        add(rows,InteractionEnergyResult.Role.COMPLEX,progress,p->SemiDirectScf.solve(pair.complex(),basis,options,p));
        add(rows,InteractionEnergyResult.Role.A_WITH_GHOST_B,progress,p->SemiDirectScf.solve(ga,basis,options,p));
        add(rows,InteractionEnergyResult.Role.B_WITH_GHOST_A,progress,p->SemiDirectScf.solve(gb,basis,options,p));
        String full=rows.getFirst().calculation().cache().cache().identity();
        for(var c:rows)if(!full.equals(c.calculation().cache().cache().identity()))throw new IOException("Full-basis cache identity differs");
        add(rows,InteractionEnergyResult.Role.A_OWN,progress,p->SemiDirectScf.solve(pair.a().system(),family.forSystem(pair.a().system()),options,p));
        add(rows,InteractionEnergyResult.Role.B_OWN,progress,p->SemiDirectScf.solve(pair.b().system(),family.forSystem(pair.b().system()),options,p));
        double unc=InteractionEnergyCalculator.subtract(energy(rows,InteractionEnergyResult.Role.COMPLEX),energy(rows,InteractionEnergyResult.Role.A_OWN),energy(rows,InteractionEnergyResult.Role.B_OWN));
        double cp=InteractionEnergyCalculator.subtract(energy(rows,InteractionEnergyResult.Role.COMPLEX),energy(rows,InteractionEnergyResult.Role.A_WITH_GHOST_B),energy(rows,InteractionEnergyResult.Role.B_WITH_GHOST_A));
        var hash=ContentHash.accumulator().line("aether-SEMI_DIRECT_CACHE-CP-14.1-1;explicit-AB-basis-order")
                .line(InteractionEnergyCalculator.fragmentIdentity(pair.a())).line(InteractionEnergyCalculator.fragmentIdentity(pair.b())).line(family.name());
        for(var c:rows)hash.line(c.role().name()).line(c.calculation().receiptHash());
        hash.line(ContentHash.number(unc)).line(ContentHash.number(cp)).line("SCREENING_ONLY;RHF-no-dispersion;not-complete-binding-energy");
        return new Result(rows,unc,cp,hash.finish(),System.nanoTime()-start);
    }
    @FunctionalInterface private interface Calculation{SemiDirectScf.Result run(Consumer<String> progress)throws IOException;}
    private static void add(List<Component> rows,InteractionEnergyResult.Role role,Consumer<String> progress,Calculation calculate)throws IOException {
        progress.accept("CP_START "+role);var result=calculate.run(s->progress.accept(role+" "+s));
        if(result.convergedState().isEmpty())throw new ArithmeticException("CP "+role+" "+result.convergenceStatus());
        rows.add(new Component(role,result));progress.accept("CP_FINISH "+role+" seconds="+result.totalNanos()/1e9);
    }
    private static double energy(List<Component> rows,InteractionEnergyResult.Role role){return rows.stream().filter(c->c.role()==role).findFirst().orElseThrow().calculation().convergedState().orElseThrow().totalHartree();}
    public record Component(InteractionEnergyResult.Role role,SemiDirectScf.Result calculation){}
    public record Result(List<Component> components,double uncorrectedHartree,double counterpoiseHartree,String receiptHash,long totalNanos) {
        public Result{components=List.copyOf(components);}
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
}
