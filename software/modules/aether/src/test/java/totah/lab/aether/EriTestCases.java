package totah.lab.aether;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.matrix.ElectronRepulsionCalculator;
import totah.lab.aether.matrix.ElectronRepulsionTensor;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.gaia.geometry.Point3D;

import static totah.lab.aether.KineticTestCases.*;
import static totah.lab.aether.NuclearTestCases.hydrogen;

final class EriTestCases {
    private EriTestCases() {}

    static ElectronRepulsionTensor calculate(List<ContractedGaussian> basis) {
        var nuclei = basis.stream().map(f -> hydrogen(f.terms().getFirst().primitive().centerBohr())).toList();
        return new ElectronRepulsionCalculator(new QuantumSystem(nuclei, 0, nuclei.size() % 2 + 1), basis).calculate();
    }

    static List<ContractedGaussian> fourDistinct() {
        return List.of(single(0.7, ORIGIN), single(1.3, new Point3D(0.4, -0.7, 1.1)),
                single(0.5, new Point3D(-0.8, 0.5, 0.2)), single(2, new Point3D(1.2, 0.3, -0.6)));
    }

    static Map<String, ElectronRepulsionTensor> referenceCases() throws IOException {
        var result = new TreeMap<String, ElectronRepulsionTensor>();
        KineticTestCases.referenceCases().forEach((name, t) -> result.put(name, calculate(t.functions())));
        result.put("near_coincident", calculate(List.of(single(1, ORIGIN), single(1, new Point3D(1e-8, 0, 0)))));
        result.put("large_separation", calculate(List.of(single(1, ORIGIN), single(1, new Point3D(10000, 0, 0)))));
        result.put("four_distinct", calculate(fourDistinct()));
        return result;
    }
}
