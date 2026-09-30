package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.RhfScfCalculator;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;

public final class CnoReceiptReplay {
    private CnoReceiptReplay() {}
    static Map<String,QuantumSystem> systems() throws IOException {
        byte[] bytes;
        try(var input=CnoReceiptReplay.class.getResourceAsStream("reference/cno-geometry.csv")) {
            if(input==null)throw new IOException("Missing CNO geometry reference");bytes=input.readAllBytes();
        }
        if(!ContentHash.sha256(bytes).equals("83a73d31ba424060d01a5048e173ae5e5a50fa6d9091a756c7d235da9e2943f4"))throw new IOException("CNO geometry hash mismatch");
        var nuclei=new TreeMap<String,List<NuclearCenter>>();
        for(String line:new String(bytes,StandardCharsets.UTF_8).lines().skip(1).toList()) {
            var c=line.split(",");var list=nuclei.computeIfAbsent(c[0],x->new ArrayList<>());
            if(list.size()!=Integer.parseInt(c[1]))throw new IOException("CNO nucleus ordering mismatch");
            list.add(new NuclearCenter(new Point3D(Double.parseDouble(c[3]),Double.parseDouble(c[4]),Double.parseDouble(c[5])),Double.parseDouble(c[2])));
        }
        var result=new TreeMap<String,QuantumSystem>();nuclei.forEach((name,list)->result.put(name,new QuantumSystem(list,0,1)));return result;
    }
    public static void main(String[] args) throws IOException {
        if(Runtime.version().feature()!=21)throw new IllegalStateException("Replay requires Java 21");
        var system=systems().get(args.length==0?"h2o":args[0]);
        System.out.write(RhfScfCalculator.solve(system,Sto3gBasis.load().forSystem(system)).receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
