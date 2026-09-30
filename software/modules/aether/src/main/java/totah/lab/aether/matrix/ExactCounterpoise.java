package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.*;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.FragmentPair;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** The same five RHF components and fixed-geometry counterpoise subtraction under M14 execution. */
public final class ExactCounterpoise {
    private ExactCounterpoise() {}
    public static Result calculate(FragmentPair fragments,BasisFamily family,ExactScf.Options options)throws IOException {
        if(options.method()!=ExactScf.Method.RHF)throw new IllegalArgumentException("Counterpoise requires RHF in M14");
        long started=System.nanoTime();var complex=fragments.complex();
        var components=new ArrayList<Component>();
        components.add(new Component(InteractionEnergyResult.Role.COMPLEX,ExactScf.solve(complex,family.forSystem(complex),options)));
        components.add(new Component(InteractionEnergyResult.Role.A_OWN,ExactScf.solve(fragments.a().system(),family.forSystem(fragments.a().system()),options)));
        components.add(new Component(InteractionEnergyResult.Role.B_OWN,ExactScf.solve(fragments.b().system(),family.forSystem(fragments.b().system()),options)));
        components.add(new Component(InteractionEnergyResult.Role.A_WITH_GHOST_B,ExactScf.solve(GhostBasis.withDonor(fragments.a().system(),fragments.b().system(),family),options)));
        components.add(new Component(InteractionEnergyResult.Role.B_WITH_GHOST_A,ExactScf.solve(GhostBasis.withDonor(fragments.b().system(),fragments.a().system(),family),options)));
        var unc=combine(components.get(0),components.get(1),components.get(2));var cp=combine(components.get(0),components.get(3),components.get(4));
        var hash=ContentHash.accumulator().line("aether-counterpoise-exact-14-1").line(options.protocol()).line(family.name())
                .line(InteractionEnergyCalculator.fragmentIdentity(fragments.a())).line(InteractionEnergyCalculator.fragmentIdentity(fragments.b()));
        for(var c:components)hash.line(c.role().name()).line(c.calculation().receiptHash());
        hash.line(unc.toString()).line(cp.toString()).line("SCREENING_ONLY;RHF-no-dispersion;not-complete-binding-energy");
        return new Result(components,unc,cp,hash.finish(),System.nanoTime()-started);
    }
    private static OptionalDouble combine(Component complex,Component a,Component b) {
        if(complex.calculation().convergedState().isEmpty()||a.calculation().convergedState().isEmpty()||b.calculation().convergedState().isEmpty())return OptionalDouble.empty();
        return OptionalDouble.of(InteractionEnergyCalculator.subtract(complex.calculation().convergedState().orElseThrow().totalHartree(),
                a.calculation().convergedState().orElseThrow().totalHartree(),b.calculation().convergedState().orElseThrow().totalHartree()));
    }
    public record Component(InteractionEnergyResult.Role role,ExactScf.Result calculation) {}
    public record Result(List<Component> components,OptionalDouble uncorrectedHartree,OptionalDouble counterpoiseHartree,String receiptHash,long elapsedNanos) {
        public Result {components=List.copyOf(components);}
        public InteractionEnergyResult.Status evaluationStatus(){return uncorrectedHartree.isPresent()&&counterpoiseHartree.isPresent()
                ?InteractionEnergyResult.Status.EVALUATED:InteractionEnergyResult.Status.SCF_NOT_CONVERGED;}
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
}
