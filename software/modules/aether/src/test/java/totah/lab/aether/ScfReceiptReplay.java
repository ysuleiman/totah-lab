package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import totah.lab.aether.matrix.RhfScfCalculator;
import totah.lab.aether.matrix.RhfScfResult;

public final class ScfReceiptReplay {
    private ScfReceiptReplay() {}
    static RhfScfResult solve(String system, int cap) throws IOException {
        var p=JkTestCases.densities().get(system.equals("h2") ? "h2_rhf" : "h4_arbitrary");
        return RhfScfCalculator.solve(p.system(),p.functions(),cap);
    }
    public static void main(String[] args) throws IOException {
        if(Runtime.version().feature()!=21) throw new IllegalStateException("Replay requires Java 21");
        var r=solve(args.length==0?"h2":args[0],args.length<2?RhfScfCalculator.DEFAULT_MAX_ITERATIONS:Integer.parseInt(args[1]));
        System.out.write(r.receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
