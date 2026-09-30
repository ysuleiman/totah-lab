package totah.lab.aether.matrix;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import totah.lab.aether.HeapObservation;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.gaia.geometry.Point3D;

/**
 * M20C grid/XC + direct-J profiling harness only; delegates all physics to frozen production APIs.
 * Reads a frozen PySCF converged density (raw little-endian f8 row-major, exported by
 * validation/grid-xc-profile-20260918/tools/export_densities.py), imports it through the public
 * DensityMatrix.fromRowMajor path, and times BlockedPbe.evaluate or DirectExactJk.calculate.
 * Emits one JSON record per rep on stdout. Test-scope evidence, never production.
 */
public final class GridXcProfile {
    private GridXcProfile() {}

    public static void main(String[] args) throws Exception {
        Path fixture = Path.of(args[0]);
        int charge = Integer.parseInt(args[1]);
        Path densityBin = Path.of(args[2]);
        int expectedAo = Integer.parseInt(args[3]);
        String mode = args[4];
        int reps = Integer.parseInt(args[5]);
        int workers = Integer.parseInt(args[6]);

        var system = system(fixture, charge);
        var basis = BasisFamily.DEF2_SVP.forSystem(system);
        if (basis.size() != expectedAo) throw new AssertionError("AO mismatch " + basis.size() + " vs " + expectedAo);
        var density = loadDensity(system, basis, densityBin, expectedAo);
        System.out.println("{\"event\":\"imported\",\"ao\":" + expectedAo
                + ",\"densityHash\":\"" + density.densityHash() + "\"}");

        switch (mode) {
            case "grid" -> gridProfile(system, basis, density, reps, workers);
            case "jk" -> jkProfile(system, basis, density, reps, workers);
            default -> throw new IllegalArgumentException("mode must be grid or jk");
        }
    }

    private static QuantumSystem system(Path fixture, int charge) throws IOException {
        var lines = Files.readAllLines(fixture);
        var nuclei = new ArrayList<NuclearCenter>();
        for (var line : lines.subList(1, lines.size())) {
            var c = line.split(",");
            nuclei.add(new NuclearCenter(new Point3D(Double.parseDouble(c[1]),
                    Double.parseDouble(c[2]), Double.parseDouble(c[3])), Double.parseDouble(c[0])));
        }
        return new QuantumSystem(nuclei, charge, 1);
    }

    private static DensityMatrix loadDensity(QuantumSystem system, List<totah.lab.aether.basis.ContractedGaussian> basis,
                                             Path bin, int n) throws IOException {
        var bytes = Files.readAllBytes(bin);
        if (bytes.length != (long) n * n * 8) throw new AssertionError("density size mismatch");
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        var entries = new ArrayList<Double>(n * n);
        for (int i = 0; i < n * n; i++) entries.add(buffer.getDouble());
        return DensityMatrix.fromRowMajor(system, basis, entries);
    }

    private static void gridProfile(QuantumSystem system, List<totah.lab.aether.basis.ContractedGaussian> basis,
                                    DensityMatrix density, int reps, int workers) throws Exception {
        try (var heap = new HeapObservation("gridxc-" + workers + "w");
             var pbe = new BlockedPbe(system, basis, new GridDefinition(120, 590), 256, workers)) {
            for (int rep = 1; rep <= reps; rep++) {
                var r = pbe.evaluate(density);
                var p = r.performance();
                System.out.println("{\"event\":\"grid\",\"rep\":" + rep
                        + ",\"gridPoints\":" + p.gridPoints()
                        + ",\"blockSize\":" + p.blockSize()
                        + ",\"workers\":" + workers
                        + ",\"blockStorageEstimateBytes\":" + p.blockStorageEstimateBytes()
                        + ",\"aoNanos\":" + p.aoNanos()
                        + ",\"derivativeNanos\":" + p.derivativeNanos()
                        + ",\"densityGradientNanos\":" + p.densityGradientNanos()
                        + ",\"functionalNanos\":" + p.functionalNanos()
                        + ",\"potentialNanos\":" + p.potentialNanos()
                        + ",\"totalNanos\":" + p.totalNanos()
                        + ",\"energyHartree\":" + r.energyHartree()
                        + ",\"integratedElectrons\":" + r.integratedElectrons()
                        + ",\"roundoffClampedPoints\":" + r.roundoffClampedPoints()
                        + ",\"lowDensityTailPoints\":" + r.lowDensityTailPoints()
                        + ",\"densityGradientHash\":\"" + r.densityGradientHash() + "\""
                        + ",\"gridHash\":\"" + r.gridHash() + "\""
                        + ",\"receiptHash\":\"" + r.receiptHash() + "\"}");
            }
        }
    }

    private static void jkProfile(QuantumSystem system, List<totah.lab.aether.basis.ContractedGaussian> basis,
                                  DensityMatrix density, int reps, int workers) throws Exception {
        try (var heap = new HeapObservation("directJ-" + workers + "w");
             var jk = new DirectExactJk(system, basis, workers)) {
            for (int rep = 1; rep <= reps; rep++) {
                var r = jk.calculate(density, DirectExactJk.Contraction.COULOMB_ONLY);
                var p = r.performance();
                System.out.println("{\"event\":\"jk\",\"rep\":" + rep
                        + ",\"dimension\":" + p.dimension()
                        + ",\"uniqueQuartets\":" + p.uniqueQuartets()
                        + ",\"primitiveQuartets\":" + p.primitiveQuartets()
                        + ",\"jAccumulations\":" + p.jAccumulations()
                        + ",\"integralNanos\":" + p.integralNanos()
                        + ",\"accumulationNanos\":" + p.accumulationNanos()
                        + ",\"totalNanos\":" + p.totalNanos()
                        + ",\"receiptHash\":\"" + r.receiptHash() + "\"}");
            }
        }
    }
}
