package totah.lab.aether;

import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.*;

/** No timings or incidental object identities in the canonical replay payload. */
public final class ExactReceiptReplay {
    private ExactReceiptReplay() {}
    public static void main(String[] args)throws Exception {
        if(Runtime.version().feature()!=21)throw new IllegalStateException("Java 21 required");
        var options=new ExactScf.Options(args[0].equals("LDA")?ExactScf.Method.LDA_PZ81:ExactScf.Method.RHF,
                ExactScf.Integrals.DIRECT_EXACT,512,ConstructedDensity.Mode.CONSTRUCTION,8);
        if(args[0].equals("CP")) {
            var result=ExactCounterpoise.calculate(InteractionReceiptReplay.pairs().get(args[1]),BasisFamily.DEF2_SVP,options);
            if(result.counterpoiseHartree().isEmpty())throw new AssertionError("CP not converged");
            System.out.println(result.receiptHash());
            for(var c:result.components()){System.out.println(c.role());System.out.println(c.calculation().receiptHash());System.out.println(c.calculation().iterations());}
        } else {
            var system=DiisReceiptReplay.systems().get(args[1]);var result=ExactScf.solve(system,BasisFamily.DEF2_SVP.forSystem(system),options);
            if(result.convergedState().isEmpty())throw new AssertionError(result.reason());
            System.out.println(result.receiptHash());System.out.println(result.iterations());
        }
    }
}
