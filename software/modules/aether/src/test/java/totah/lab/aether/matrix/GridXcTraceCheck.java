package totah.lab.aether.matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.gaia.geometry.Point3D;

/** Diagnostic: tr(P*S) of an imported frozen density under the Aether overlap. Test-scope only. */
public final class GridXcTraceCheck {
    private GridXcTraceCheck() {}

    public static void main(String[] args) throws Exception {
        var lines = Files.readAllLines(Path.of(args[0]));
        var nuclei = new ArrayList<NuclearCenter>();
        for (var line : lines.subList(1, lines.size())) {
            var c = line.split(",");
            nuclei.add(new NuclearCenter(new Point3D(Double.parseDouble(c[1]),
                    Double.parseDouble(c[2]), Double.parseDouble(c[3])), Double.parseDouble(c[0])));
        }
        var system = new QuantumSystem(nuclei, Integer.parseInt(args[1]), 1);
        var basis = BasisFamily.DEF2_SVP.forSystem(system);
        int n = basis.size();
        var buffer = ByteBuffer.wrap(Files.readAllBytes(Path.of(args[2]))).order(ByteOrder.LITTLE_ENDIAN);
        var entries = new ArrayList<Double>(n * n);
        for (int i = 0; i < n * n; i++) entries.add(buffer.getDouble());
        var density = DensityMatrix.fromRowMajor(system, basis, entries);
        var overlap = OverlapMatrix.compute(basis);
        double trace = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) trace += density.get(i, j) * overlap.get(j, i);
        System.out.println("{\"ao\":" + n + ",\"tracePS\":" + trace + "}");
    }
}
