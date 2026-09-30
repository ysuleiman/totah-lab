package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.FragmentPair;
import totah.lab.aether.model.MolecularFragment;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import static totah.lab.aether.matrix.InteractionEnergyResult.Role;

/** Fixed-geometry supermolecular RHF and Boys-Bernardi counterpoise; no deformation energy. */
public final class InteractionEnergyCalculator {
    public static final String IMPLEMENTATION="aether-rhf-interaction-1";
    public static final String PROTOCOL="Java21;STO-3G;s/p;RHF;hartree;bohr;fixed-fragment-geometries;"
            +"Eint=(E_AB-E_A_own)-E_B_own;Ecp=(E_AB-E_A_ghostB)-E_B_ghostA;"
            +"ghosts=basis-only;real-first-then-ghosts;fragment-charges-retained;"
            +"DIIS-start32;history8;core-guess;energy=1e-12;density=1e-10;all-required-components-converged;"
            +"SCREENING_ONLY;RHF-no-dispersion;not-complete-binding-energy";
    private InteractionEnergyCalculator() {}
    public static InteractionEnergyResult calculate(FragmentPair fragments) throws IOException {
        return calculate(fragments,RhfScfCalculator.DEFAULT_MAX_ITERATIONS);
    }
    /** An explicit reduced cap supports fail-closed audits; thresholds and accelerator are unchanged. */
    public static InteractionEnergyResult calculate(FragmentPair fragments,int maximumIterations) throws IOException {
        return calculate(fragments,BasisFamily.STO_3G,maximumIterations);
    }
    public static InteractionEnergyResult calculate(FragmentPair fragments,BasisFamily family) throws IOException {
        return calculate(fragments,family,RhfScfCalculator.DEFAULT_MAX_ITERATIONS);
    }
    public static InteractionEnergyResult calculate(FragmentPair fragments,BasisFamily family,int maximumIterations) throws IOException {
        java.util.Objects.requireNonNull(fragments);
        if(maximumIterations<1)throw new IllegalArgumentException("Iteration cap must be positive");
        var library=java.util.Objects.requireNonNull(family);var complex=fragments.complex();var components=new ArrayList<InteractionEnergyResult.Component>();
        components.add(nativeComponent(Role.COMPLEX,complex,library,maximumIterations));
        components.add(nativeComponent(Role.A_OWN,fragments.a().system(),library,maximumIterations));
        components.add(nativeComponent(Role.B_OWN,fragments.b().system(),library,maximumIterations));
        var ag=GhostBasis.withDonor(fragments.a().system(),fragments.b().system(),family);
        var bg=GhostBasis.withDonor(fragments.b().system(),fragments.a().system(),family);
        components.add(new InteractionEnergyResult.Component(Role.A_WITH_GHOST_B,RhfScfCalculator.solve(ag,maximumIterations),ag.identity()));
        components.add(new InteractionEnergyResult.Component(Role.B_WITH_GHOST_A,RhfScfCalculator.solve(bg,maximumIterations),bg.identity()));
        return assemble(fragments,components,maximumIterations,family);
    }
    private static InteractionEnergyResult.Component nativeComponent(Role role,QuantumSystem system,BasisFamily library,int cap) throws IOException {
        var basis=library.forSystem(system);
        return new InteractionEnergyResult.Component(role,RhfScfCalculator.solve(system,basis,cap,ScfPolicy.DIIS),IntegralMatrixData.basisGeometryHash(basis));
    }
    // Internal assembly checks identities even though the public calculator creates all components itself.
    static InteractionEnergyResult assemble(FragmentPair fragments,List<InteractionEnergyResult.Component> components,int cap) throws IOException {
        return assemble(fragments,components,cap,BasisFamily.STO_3G);
    }
    private static InteractionEnergyResult assemble(FragmentPair fragments,List<InteractionEnergyResult.Component> components,int cap,BasisFamily family) throws IOException {
        if(components.size()!=Role.values().length)throw new IllegalArgumentException("Five interaction components are required");
        var library=java.util.Objects.requireNonNull(family);var source=new StringBuilder();
        for(int i=0;i<components.size();i++) {
            var c=components.get(i);if(c.role()!=Role.values()[i])throw new IllegalArgumentException("Interaction component ordering mismatch");
            QuantumSystem expected=switch(c.role()) {
                case COMPLEX->fragments.complex();case A_OWN,A_WITH_GHOST_B->fragments.a().system();case B_OWN,B_WITH_GHOST_A->fragments.b().system();
            };
            var run=c.calculation();OccupiedDensityCalculator.requireEqual(IntegralMatrixData.systemHash(expected),run.receipt().systemHash(),"interaction component system");
            GhostBasis ghostContext=null;var basis=library.forSystem(expected);String context=IntegralMatrixData.basisGeometryHash(basis);
            if(c.role()==Role.A_WITH_GHOST_B||c.role()==Role.B_WITH_GHOST_A) {
                var donor=c.role()==Role.A_WITH_GHOST_B?fragments.b().system():fragments.a().system();
                var ghost=GhostBasis.withDonor(expected,donor,family);ghostContext=ghost;basis=ghost.functions();context=ghost.identity();
                if(!run.receipt().protocol().contains("ghost-basis-v1="+context+";"))throw new IllegalArgumentException("Missing ghost provenance");
            }
            OccupiedDensityCalculator.requireEqual(IntegralMatrixData.basisGeometryHash(basis),run.receipt().basisGeometryHash(),"interaction component basis/order");
            OccupiedDensityCalculator.requireEqual(context,c.basisContextIdentity(),"interaction basis context");
            if(run.receipt().policy()!=ScfPolicy.DIIS||run.receipt().maximumIterations()!=cap||!run.receipt().protocol().equals(DiisRhfScf.protocol(expected,basis,ghostContext)))
                throw new IllegalArgumentException("Incompatible interaction numerical protocol");
            source.append(c.role()).append('\n').append(context).append('\n').append(run.receipt().receiptHash()).append('\n');
        }
        var unc=combine(components.get(0),components.get(1),components.get(2));
        var cp=combine(components.get(0),components.get(3),components.get(4));
        var status=unc.isPresent()&&cp.isPresent()?InteractionEnergyResult.Status.EVALUATED:InteractionEnergyResult.Status.SCF_NOT_CONVERGED;
        String a=fragmentIdentity(fragments.a()),b=fragmentIdentity(fragments.b());
        String reason="Fixed-geometry RHF interaction; counterpoise separate; dispersion missing; SCREENING_ONLY";
        String protocol=family==BasisFamily.STO_3G?PROTOCOL:PROTOCOL.replace("STO-3G;s/p;","def2-SVP;s/p/d-Cartesian;")+";basis-sha256="+totah.lab.aether.basis.Def2SvpBasis.RESOURCE_SHA256;
        var id=IntegralMatrixData.identity(components.getFirst().calculation().receipt().basisGeometryHash(),IMPLEMENTATION,protocol,reason,
                "\n"+a+"\n"+b+"\n"+cap+"\n"+source,"aether-interaction-values-v1\n"+number(unc)+"\n"+number(cp)+"\n"+status);
        var receipt=new InteractionEnergyResult.Receipt(IMPLEMENTATION,protocol,a,b,
                components.stream().map(c->c.calculation().receipt().receiptHash()).toList(),status,unc,cp,id.calculationHash(),id.resultHash(),id.receiptHash());
        return new InteractionEnergyResult(fragments,components,receipt);
    }
    private static OptionalDouble combine(InteractionEnergyResult.Component complex,InteractionEnergyResult.Component a,InteractionEnergyResult.Component b) {
        if(complex.calculation().convergedState().isEmpty()||a.calculation().convergedState().isEmpty()||b.calculation().convergedState().isEmpty())return OptionalDouble.empty();
        return OptionalDouble.of(subtract(complex.calculation().convergedState().orElseThrow().energy().totalHartree(),a.calculation().convergedState().orElseThrow().energy().totalHartree(),
                b.calculation().convergedState().orElseThrow().energy().totalHartree()));
    }
    static double subtract(double complex,double a,double b) {
        double result=(complex-a)-b;
        if(!Double.isFinite(result))throw new ArithmeticException("Nonfinite interaction energy");return result;
    }
    private static String number(OptionalDouble value) { return value.isPresent()?ContentHash.number(value.getAsDouble()):"UNAVAILABLE"; }
    static String fragmentIdentity(MolecularFragment fragment) {
        return ContentHash.sha256("aether-fragment-v1\n"+fragment.id().length()+":"+fragment.id()+"\n"+IntegralMatrixData.systemHash(fragment.system()));
    }
}
