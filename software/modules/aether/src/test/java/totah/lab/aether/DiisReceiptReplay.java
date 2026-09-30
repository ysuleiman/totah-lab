package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.QuantumSystem;

public final class DiisReceiptReplay {
    private DiisReceiptReplay() {}
    static Map<String,QuantumSystem> systems() throws IOException {
        var result=new TreeMap<>(SpclReceiptReplay.systems());result.putAll(CnoReceiptReplay.systems());
        var p=JkTestCases.densities();result.put("h2",p.get("h2_rhf").system());result.put("h4",p.get("h4_arbitrary").system());return result;
    }
    static RhfScfRun solve(String name) throws IOException {
        var system=systems().get(name);return RhfScfCalculator.solve(system,Sto3gBasis.load().forSystem(system),ScfPolicy.DIIS);
    }
    public static void main(String[] args) throws IOException {
        if(Runtime.version().feature()!=21)throw new IllegalStateException("Replay requires Java 21");
        System.out.write(solve(args[0]).receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
