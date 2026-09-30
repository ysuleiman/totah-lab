package totah.lab.aether.matrix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

/** Deterministic real-atom Becke partition of mapped radial x Lebedev quadrature. */
public final class MolecularGrid {
    public static final String RADIAL_SHA256="ff8aec876d18f02104fd1cb9a6903233b531b03fa3aea92213315bc4d9825da4";
    public static final String ANGULAR_SHA256="cfa887a478d09f2b0c7e3ea68a8eae5640477f024345951f1cdc1e5d9227b7d3";
    private final QuantumSystem system;private final GridDefinition definition;private final double[] x,y,z,w;private final String hash;
    public record Point(Point3D coordinateBohr,double volumeWeightBohr3){}
    private MolecularGrid(QuantumSystem system,GridDefinition definition)throws IOException {
        this.system=java.util.Objects.requireNonNull(system);this.definition=java.util.Objects.requireNonNull(definition);
        NuclearRepulsion.calculate(system); // Reject coincident distinct nuclei before partition denominators.
        var source=new GridPointSource(system,definition);int size=source.size();
        x=new double[size];y=new double[size];z=new double[size];w=new double[size];
        var digest=new NumericalEvidenceHash("aether-grid-points-v1");double[] block=new double[4*512];
        for(int start=0;start<size;start+=512) {
            int count=Math.min(512,size-start);source.fill(start,count,block);
            for(int j=0;j<count;j++) {
                int k=start+j;x[k]=block[4*j];y[k]=block[4*j+1];z[k]=block[4*j+2];w[k]=block[4*j+3];
                digest.add(x[k]);digest.add(y[k]);digest.add(z[k]);digest.add(w[k]);
            }
        }
        hash=identity(system,definition,digest.finish());
    }
    static String identity(QuantumSystem system,GridDefinition definition,String pointsHash) {
        return ContentHash.sha256(definition.protocol()+"\n"+IntegralMatrixData.systemHash(system)+"\n"+RADIAL_SHA256+"\n"+ANGULAR_SHA256+"\n"+pointsHash+"\nSCREENING_ONLY");
    }
    static List<double[]> rule(String name,String hash,int count)throws IOException {
        byte[] bytes;try(var stream=MolecularGrid.class.getResourceAsStream("/totah/lab/aether/grid/"+name)) {
            if(stream==null)throw new IOException("Missing quadrature rule");bytes=stream.readAllBytes();
        }
        if(!ContentHash.sha256(bytes).equals(hash))throw new IOException("Quadrature resource hash mismatch");
        var result=new ArrayList<double[]>();
        for(var line:new String(bytes,StandardCharsets.UTF_8).lines().skip(1).toList()) {
            var c=line.split(",");if(Integer.parseInt(c[0])!=count)continue;
            var row=new double[c.length-1];for(int i=0;i<row.length;i++){row[i]=Double.parseDouble(c[i+1]);if(!Double.isFinite(row[i]))throw new IOException("Nonfinite quadrature rule");}result.add(row);
        }
        if(result.size()!=count)throw new IOException("Quadrature cardinality mismatch");return List.copyOf(result);
    }
    public static MolecularGrid build(QuantumSystem system,GridDefinition definition)throws IOException{return new MolecularGrid(system,definition);}
    public int size(){return w.length;}
    public Point point(int i){return new Point(new Point3D(x[i],y[i],z[i]),w[i]);}
    double x(int i){return x[i];}double y(int i){return y[i];}double z(int i){return z[i];}double weight(int i){return w[i];}
    public QuantumSystem system(){return system;}public GridDefinition definition(){return definition;}
    public String receiptHash(){return hash;}public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
}
