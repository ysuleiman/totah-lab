package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.util.*;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;

public final class PbeTestSupport {
    public static String reference(String stem)throws Exception {
        byte[] bytes;String hash;
        try(var in=PbeTestSupport.class.getResourceAsStream("reference/pbe15/"+stem+".csv")){bytes=Objects.requireNonNull(in,stem).readAllBytes();}
        try(var in=PbeTestSupport.class.getResourceAsStream("reference/pbe15/"+stem+".sha256")){hash=new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.US_ASCII).trim();}
        if(!ContentHash.sha256(bytes).equals(hash))throw new AssertionError("Reference checksum "+stem);return new String(bytes,StandardCharsets.US_ASCII);
    }
    public static QuantumSystem system(String name,String transform)throws Exception {
        var s=DiisReceiptReplay.systems().get(name);if(transform.equals("native"))return s;
        return new QuantumSystem(s.nuclei().stream().map(n->{var p=n.centerBohr();return new NuclearCenter(transform.equals("rotated")?AetherDef2GridTest.rotate(p):new Point3D(p.x()+.31,p.y()-1.27,p.z()+.44),n.charge());}).toList(),s.molecularCharge(),s.multiplicity());
    }
    public static DensityMatrix density(String reference,QuantumSystem system,List<ContractedGaussian> basis) {
        int n=basis.size();double[][] p=new double[n][n];
        for(var line:reference.lines().skip(1).toList()){var c=line.split(",");if(c[0].equals("density"))p[Integer.parseInt(c[1])][Integer.parseInt(c[2])]=Double.parseDouble(c[3]);}
        var values=new ArrayList<Double>();for(int i=0;i<n;i++)for(int j=0;j<n;j++)values.add((p[i][j]+p[j][i])*.5);
        return DensityMatrix.fromRowMajor(system,basis,values);
    }
}
