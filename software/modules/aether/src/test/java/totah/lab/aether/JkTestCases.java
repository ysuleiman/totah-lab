package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.Sto3gHydrogen;
import totah.lab.aether.matrix.DensityMatrix;
import totah.lab.aether.matrix.ElectronRepulsionCalculator;
import totah.lab.aether.matrix.JkCalculator;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;

final class JkTestCases {
    private JkTestCases() {}

    static Map<String, DensityMatrix> densities() throws IOException {
        byte[] bytes;
        try (var input = JkTestCases.class.getResourceAsStream("reference/jk-density.csv")) {
            if (input == null) throw new IOException("Missing supplied density fixture");
            bytes = input.readAllBytes();
        }
        if (!ContentHash.sha256(bytes).equals("fd0d30bf3b259e065c10ae538d0a7b2f4cc71afae8ec94b2f829480b3bcb1fe9")) {
            throw new IOException("Supplied density fixture hash mismatch");
        }
        var h = Sto3gHydrogen.load();
        Map<String, List<ContractedGaussian>> bases = Map.of("h2", KineticTestCases.h2(), "h4",
                EriTestCases.fourDistinct().stream().map(f -> h.atBohr(f.terms().getFirst().primitive().centerBohr())).toList());
        var values = new TreeMap<String, List<Double>>();
        var systems = new TreeMap<String, String>();
        var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        if (!lines.getFirst().equals("case,system,row,column,density")) throw new IOException("Invalid density header");
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            var basis = bases.get(c[1]);
            if (basis == null) throw new IOException("Unknown density system");
            var entries = values.computeIfAbsent(c[0], key -> new ArrayList<>());
            String previous = systems.putIfAbsent(c[0], c[1]);
            if ((previous != null && !previous.equals(c[1]))
                    || Integer.parseInt(c[2]) * basis.size() + Integer.parseInt(c[3]) != entries.size()) {
                throw new IOException("Noncontiguous or duplicate density fixture entry");
            }
            entries.add(Double.parseDouble(c[4]));
        }
        var result = new TreeMap<String, DensityMatrix>();
        values.forEach((name, entries) -> {
            var basis = bases.get(systems.get(name));
            var nuclei = basis.stream().map(f -> NuclearTestCases.hydrogen(f.terms().getFirst().primitive().centerBohr())).toList();
            result.put(name, DensityMatrix.fromRowMajor(new QuantumSystem(nuclei, 0, 1), basis, entries));
        });
        return result;
    }

    static List<Double> rowMajor(DensityMatrix density) {
        var values = new ArrayList<Double>();
        for (int i = 0; i < density.size(); i++) for (int j = 0; j < density.size(); j++) values.add(density.get(i, j));
        return values;
    }

    static JkCalculator.Result calculate(DensityMatrix density) {
        return JkCalculator.calculate(density, new ElectronRepulsionCalculator(density.system(), density.functions()).calculate());
    }
}
