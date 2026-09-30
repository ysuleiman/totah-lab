package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.*;

public final class KsReceiptReplay {
    private KsReceiptReplay(){}
    static KohnShamResult solve(String name,int radial,int angular)throws IOException {
        var system=DiisReceiptReplay.systems().get(name);
        return KohnShamCalculator.solve(system,Sto3gBasis.load().forSystem(system),new GridDefinition(radial,angular),LdaFunctional.EXCHANGE_PZ81);
    }
    public static void main(String[] args)throws IOException {
        if(Runtime.version().feature()!=21)throw new IllegalStateException("Java 21 replay required");
        System.out.write(solve(args[0],Integer.parseInt(args[1]),Integer.parseInt(args[2])).receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
