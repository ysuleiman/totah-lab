package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import totah.lab.aether.matrix.*;
import totah.lab.aether.provenance.ContentHash;

public final class RhfStateReceiptReplay {
    private RhfStateReceiptReplay() {}
    static Map<String, DensityMatrix> inputs() throws IOException {
        byte[] bytes;
        try (var input = RhfStateReceiptReplay.class.getResourceAsStream("reference/rhf-state-input.csv")) {
            if (input == null) throw new IOException("Missing RHF supplied densities");
            bytes = input.readAllBytes();
        }
        if (!ContentHash.sha256(bytes).equals("1b4a8f116477178365639536586a411842ad9ed9d64d11f0b6dad61656394f4b")) throw new IOException("Density reference hash mismatch");
        var values = new TreeMap<String,List<Double>>();
        var old = JkTestCases.densities();
        var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        for (String line : lines.subList(1, lines.size())) {
            var c = line.split(",");
            var template = old.get(c[0].startsWith("h2") ? "h2_rhf" : "h4_arbitrary");
            var entries = values.computeIfAbsent(c[0], key -> new ArrayList<>());
            if (Integer.parseInt(c[1])*template.size()+Integer.parseInt(c[2]) != entries.size()) throw new IOException("Reference ordering mismatch");
            entries.add(Double.parseDouble(c[3]));
        }
        var result = new TreeMap<String,DensityMatrix>();
        values.forEach((name, entries) -> {
            var template = old.get(name.startsWith("h2") ? "h2_rhf" : "h4_arbitrary");
            result.put(name, DensityMatrix.fromRowMajor(template.system(), template.functions(), entries));
        });
        return result;
    }
    static Evidence evaluate(DensityMatrix p) {
        var orbitals = OneShotReceiptReplay.solve(p);
        var s = OverlapMatrix.compute(p.functions());
        var built = OccupiedDensityCalculator.build(p.system(), s, orbitals.coefficients(), orbitals.energies());
        var core = new CoreHamiltonianCalculator(p.system(),p.functions()).calculate();
        var externalEnergy = RhfEnergyCalculator.evaluate(p, core, orbitals.fock());
        // Exactly one independent assembly for the constructed P. No convergence loop.
        var builtFock = OneShotReceiptReplay.solve(built.density()).fock();
        return new Evidence(built, externalEnergy, RhfEnergyCalculator.evaluate(built, core, builtFock));
    }
    record Evidence(OccupiedDensityCalculator.Result built, RhfEnergyCalculator.Result external,
                    RhfEnergyCalculator.Result constructed) {
        String replay() { return built.receipt() + "\n" + external.receipt() + "\n" + constructed.receipt() + "\n"; }
    }
    public static void main(String[] args) throws IOException {
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Replay requires Java 21");
        System.out.write(evaluate(inputs().get(args.length == 0 ? "h2_fresh_rhf" : args[0])).replay().getBytes(StandardCharsets.UTF_8));
    }
}
