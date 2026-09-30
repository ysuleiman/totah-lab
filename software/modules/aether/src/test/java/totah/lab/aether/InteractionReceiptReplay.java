package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;

public final class InteractionReceiptReplay {
    private InteractionReceiptReplay() {}
    static String fixture(String filename,String hash)throws IOException {
        try(var in=InteractionReceiptReplay.class.getResourceAsStream("reference/"+filename)) {
            if(in==null)throw new IOException("Missing interaction fixture");var bytes=in.readAllBytes();
            if(!ContentHash.sha256(bytes).equals(hash))throw new IOException("Interaction fixture hash mismatch");return new String(bytes,StandardCharsets.UTF_8);
        }
    }
    static Map<String,FragmentPair> pairs()throws IOException {
        var atoms=new TreeMap<String,ArrayList<NuclearCenter>>();var charges=new TreeMap<String,Integer>();
        for(String line:fixture("interaction-geometry.csv","973a7f69660991f7dfd2b85233446d146fc273d0fe37ea6eb7c3ce62d59d1102").lines().skip(1).toList()) {
            var c=line.split(",");String key=c[0]+":"+c[1];var list=atoms.computeIfAbsent(key,k->new ArrayList<>());
            if(list.size()!=Integer.parseInt(c[2]))throw new IOException("Fragment atom order mismatch");
            int charge=Integer.parseInt(c[7]);var previous=charges.putIfAbsent(key,charge);if(previous!=null&&previous!=charge)throw new IOException("Fragment charge mismatch");
            list.add(new NuclearCenter(new Point3D(Double.parseDouble(c[4]),Double.parseDouble(c[5]),Double.parseDouble(c[6])),Integer.parseInt(c[3])));
        }
        var result=new TreeMap<String,FragmentPair>();
        for(var key:atoms.keySet())if(key.endsWith(":A")) {
            String name=key.substring(0,key.length()-2);String other=name+":B";
            result.put(name,new FragmentPair(new MolecularFragment(key,new QuantumSystem(atoms.get(key),charges.get(key),1)),new MolecularFragment(other,new QuantumSystem(atoms.get(other),charges.get(other),1))));
        }
        return result;
    }
    static String receipts(InteractionEnergyResult result) {
        var text=new StringBuilder(result.receipt().toString()).append('\n');
        for(var c:result.components())text.append(c.role()).append('\n').append(c.basisContextIdentity()).append('\n').append(c.calculation().receipt()).append('\n');
        return text.toString();
    }
    public static void main(String[] args)throws IOException {
        if(Runtime.version().feature()!=21)throw new IllegalStateException("Java 21 required");
        System.out.write(receipts(InteractionEnergyCalculator.calculate(pairs().get(args[0]))).getBytes(StandardCharsets.UTF_8));
    }
}
