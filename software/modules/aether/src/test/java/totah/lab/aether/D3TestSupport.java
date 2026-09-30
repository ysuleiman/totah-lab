package totah.lab.aether;
import java.nio.charset.StandardCharsets;
import java.util.*;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;
public final class D3TestSupport {
    public static String read(String file)throws Exception{try(var in=D3TestSupport.class.getResourceAsStream("reference/d3/"+file)){return new String(Objects.requireNonNull(in,file).readAllBytes(),StandardCharsets.US_ASCII);}}
    public static Map<String,String[]> index()throws Exception{var map=new TreeMap<String,String[]>();for(var line:read("index.csv").lines().skip(1).toList()){var c=line.split(",");map.put(c[0],c);}return map;}
    public static QuantumSystem system(String name)throws Exception {
        var nuclei=new ArrayList<NuclearCenter>();for(var line:read(name+".atoms").lines().toList()){var c=line.split(",");nuclei.add(new NuclearCenter(new Point3D(Double.parseDouble(c[1]),Double.parseDouble(c[2]),Double.parseDouble(c[3])),Double.parseDouble(c[0])));}
        return new QuantumSystem(nuclei,Integer.parseInt(index().get(name)[1]),1);
    }
    public static FragmentPair fragments(String name)throws Exception{
        var all=system(name);var c=index().get(name);int cut=Integer.parseInt(c[2]);if(cut<=0)throw new IllegalArgumentException("Not a dimer");
        return new FragmentPair(new MolecularFragment(name+":A",new QuantumSystem(all.nuclei().subList(0,cut),Integer.parseInt(c[3]),1)),new MolecularFragment(name+":B",new QuantumSystem(all.nuclei().subList(cut,all.nuclei().size()),Integer.parseInt(c[4]),1)));
    }
}
