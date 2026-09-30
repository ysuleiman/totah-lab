package totah.lab.aether.basis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;

/** Shared pinned-resource parser and shell construction, retaining source ordering. */
final class PinnedBasisData {
    private PinnedBasisData() {}
    static List<Row> loadRows(String resource, String expectedHash) throws IOException {
        byte[] bytes;
        try (var input = PinnedBasisData.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing basis resource: " + resource);
            bytes = input.readAllBytes();
        }
        if (!ContentHash.sha256(bytes).equals(expectedHash)) throw new IOException("basis resource hash mismatch: " + resource);
        var rows = new ArrayList<Row>();
        try {
            for (String line : new String(bytes, StandardCharsets.US_ASCII).lines().skip(1).toList()) {
                var c = line.split(",");
                rows.add(new Row(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]), Double.parseDouble(c[3]), Double.parseDouble(c[4])));
            }
        } catch (RuntimeException e) { throw new IOException("Invalid basis resource: " + resource, e); }
        return List.copyOf(rows);
    }
    static List<ContractedGaussian> atBohr(List<Row> rows,int atomicNumber,Point3D center) {
        var shells = rows.stream().filter(row -> row.atomicNumber == atomicNumber).map(Row::shell).distinct().toList();
        if (shells.isEmpty()) throw new IllegalArgumentException("Pinned basis supports H/C/N/O/P/S/Cl only");
        var functions=new ArrayList<ContractedGaussian>();
        for (int shell : shells) {
            var terms=new ArrayList<GaussianTerm>();int angular=-1;
            for(var row:rows) if(row.atomicNumber==atomicNumber && row.shell==shell) {
                terms.add(new GaussianTerm(new PrimitiveGaussian(center,row.exponent),row.coefficient));angular=row.angular;
            }
            for(var l:CartesianAngularMomentum.values())
                if(l.x()+l.y()+l.z()==angular) functions.add(new ContractedGaussian(terms,l));
            if(angular<0||angular>2) throw new IllegalStateException("Unsupported pinned angular momentum");
        }
        return List.copyOf(functions);
    }
    record Row(int atomicNumber,int shell,int angular,double exponent,double coefficient) {}
}
