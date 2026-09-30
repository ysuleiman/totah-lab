package totah.lab.aether;

import totah.lab.aether.basis.Def2SvpBasis;
import totah.lab.aether.matrix.ReceiptMemoryProbe;

/** Run one actual molecular integral/grid memory measurement per fresh Java 21 JVM. */
public final class MemoryGateProbe {
    private MemoryGateProbe(){}
    public static void main(String[] args) throws Exception {
        if(Runtime.version().feature()!=21)throw new IllegalStateException("Java 21 required");
        String name=args[0];boolean cp=name.equals("water_dimer")||name.equals("ammonium_benzene");
        var system=cp?InteractionReceiptReplay.pairs().get(name).complex():DiisReceiptReplay.systems().get(name);
        ReceiptMemoryProbe.measure(name,system,Def2SvpBasis.load().forSystem(system),!cp);
    }
}
