package totah.lab.aether;

import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.LdaIterationMeasurement;

/** Explicit fixed-density component benchmark; no SCF settings are changed. */
public final class LdaIterationProbe {
    private LdaIterationProbe() {}
    public static void main(String[] args)throws Exception {
        var system=DiisReceiptReplay.systems().get(args[0]);
        LdaIterationMeasurement.measure(args[0],system,BasisFamily.DEF2_SVP.forSystem(system));
    }
}
