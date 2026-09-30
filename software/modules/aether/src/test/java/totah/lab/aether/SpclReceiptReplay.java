package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.DensityMatrix;
import totah.lab.aether.matrix.RhfScfCalculator;
import totah.lab.aether.matrix.RhfScfResult;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;

public final class SpclReceiptReplay {
    private SpclReceiptReplay() {}
    static String fixture(String name,String hash) throws IOException {
        byte[] bytes;
        try(var input=SpclReceiptReplay.class.getResourceAsStream("reference/"+name)) {
            if(input==null) throw new IOException("Missing SPCl fixture: "+name);
            bytes=input.readAllBytes();
        }
        if(!ContentHash.sha256(bytes).equals(hash)) throw new IOException("SPCl fixture hash mismatch: "+name);
        return new String(bytes,StandardCharsets.UTF_8);
    }
    static Map<String,QuantumSystem> systems() throws IOException {
        var nuclei=new TreeMap<String,List<NuclearCenter>>();var charges=new TreeMap<String,Integer>();
        for(String line:fixture("spcl-geometry.csv","86cc5137a4f72e2400ded8995d0aff54e44c76d7e6fe0085f4fbf6583d121533").lines().skip(1).toList()) {
            var c=line.split(",");var list=nuclei.computeIfAbsent(c[0],key->new ArrayList<>());
            if(list.size()!=Integer.parseInt(c[1])) throw new IOException("SPCl nucleus order mismatch");
            int charge=Integer.parseInt(c[6]);Integer previous=charges.putIfAbsent(c[0],charge);
            if(previous!=null && previous!=charge) throw new IOException("Inconsistent molecular charge");
            list.add(new NuclearCenter(new Point3D(Double.parseDouble(c[3]),Double.parseDouble(c[4]),Double.parseDouble(c[5])),Double.parseDouble(c[2])));
        }
        var result=new TreeMap<String,QuantumSystem>();nuclei.forEach((name,list)->result.put(name,new QuantumSystem(list,charges.get(name),1)));return result;
    }
    static Map<String,DensityMatrix> densities() throws IOException {
        var systems=systems();var library=Sto3gBasis.load();var rows=new TreeMap<String,List<Double>>();
        for(String line:fixture("spcl-density.csv","1df3c7f49da128578592d73339ce419a28f7106d6de10acd42ec51eb7fe90244").lines().skip(1).toList()) {
            var c=line.split(",");var values=rows.computeIfAbsent(c[0],key->new ArrayList<>());
            int n=library.forSystem(systems.get(c[0])).size();
            if(Integer.parseInt(c[1])*n+Integer.parseInt(c[2])!=values.size()) throw new IOException("SPCl density ordering mismatch");
            values.add(Double.parseDouble(c[3]));
        }
        var result=new TreeMap<String,DensityMatrix>();rows.forEach((name,values)->result.put(name,DensityMatrix.fromRowMajor(systems.get(name),library.forSystem(systems.get(name)),values)));return result;
    }
    static RhfScfResult solve(String name) throws IOException {
        var system=systems().get(name);return RhfScfCalculator.solve(system,Sto3gBasis.load().forSystem(system));
    }
    public static void main(String[] args) throws IOException {
        if(Runtime.version().feature()!=21) throw new IllegalStateException("Replay requires Java 21");
        System.out.write(solve(args.length==0?"ph3":args[0]).receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
