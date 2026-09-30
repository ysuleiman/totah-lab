package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.*;

final class DftFixtures {
    private DftFixtures(){}
    static String read(String name)throws IOException {
        String expected=switch(name) {
            case "dft-density.csv"->"d1179606fc6a87aaaae3a36c023c12fbe56f7ba01b4d5eb369bb9c10ed5ab208";
            case "dft-exchange.csv"->"44bc019cf7072ba0b2d37bfb657e20e79f288405c31a348da8f2b22cab9a71c0";
            case "dft-pz.csv"->"0f1e91e4f859ea9b37fdec9609008daf92ee36d542055c85d337ebd42b6ec9e9";
            case "ks.csv"->"5a442c162e0d5ed993b34d8dfbe1663a1cc066712677447d0d0be978d2505561";
            case "lda-scalar.csv"->"ab58a0fca88460e15385a70a7d090448954c99b140e277472a9b8b1ad8ad4452";
            default->throw new IOException("Unpinned DFT fixture "+name);
        };
        try(var in=DftFixtures.class.getResourceAsStream("reference/"+name)) {
            if(in==null)throw new IOException("Missing DFT fixture "+name);byte[] bytes=in.readAllBytes();
            if(!totah.lab.aether.provenance.ContentHash.sha256(bytes).equals(expected))throw new IOException("DFT fixture hash mismatch");
            return new String(bytes,StandardCharsets.UTF_8);
        }
    }
    static Map<String,DensityMatrix> densities()throws IOException {
        var values=new TreeMap<String,List<Double>>();
        for(var line:read("dft-density.csv").lines().skip(1).toList()){var c=line.split(",");values.computeIfAbsent(c[0],k->new ArrayList<>()).add(Double.valueOf(c[3]));}
        var systems=DiisReceiptReplay.systems();var result=new TreeMap<String,DensityMatrix>();var library=Sto3gBasis.load();
        for(var e:values.entrySet())result.put(e.getKey(),DensityMatrix.fromRowMajor(systems.get(e.getKey()),library.forSystem(systems.get(e.getKey())),e.getValue()));
        return result;
    }
}
