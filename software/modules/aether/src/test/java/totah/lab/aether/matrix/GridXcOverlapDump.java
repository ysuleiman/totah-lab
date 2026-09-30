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

/** Diagnostic: dump the Aether overlap as raw little-endian f8 row-major. Test-scope only. */
public final class GridXcOverlapDump {
    private GridXcOverlapDump() {}

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
        var overlap = OverlapMatrix.compute(basis);
        int n = overlap.size();
        var buffer = ByteBuffer.allocate(n * n * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) buffer.putDouble(overlap.get(i, j));
        Files.write(Path.of(args[2]), buffer.array());
        System.out.println("{\"ao\":" + n + ",\"receipt\":\"" + overlap.receipt().receiptHash() + "\"}");
    }
}
