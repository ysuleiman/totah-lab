package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;

public final class Def2ReceiptReplay {
    private Def2ReceiptReplay() {}
    static String receipt(String mode,String name) throws IOException {
        if(mode.equals("CP"))return InteractionReceiptReplay.receipts(InteractionEnergyCalculator.calculate(InteractionReceiptReplay.pairs().get(name),BasisFamily.DEF2_SVP));
        var system=DiisReceiptReplay.systems().get(name);var basis=Def2SvpBasis.load().forSystem(system);
        return mode.equals("RHF")?RhfScfCalculator.solve(system,basis,ScfPolicy.DIIS).receipt().toString():KohnShamCalculator.solve(system,basis,new GridDefinition(120,590),LdaFunctional.EXCHANGE_PZ81).receipt().toString();
    }
    public static void main(String[] args) throws IOException {
        if(Runtime.version().feature()!=21)throw new IllegalStateException("Java 21 required");
        System.out.write(receipt(args[0],args[1]).getBytes(StandardCharsets.UTF_8));
    }
}
