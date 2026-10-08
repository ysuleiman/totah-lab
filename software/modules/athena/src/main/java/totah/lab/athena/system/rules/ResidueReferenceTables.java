package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.mnemosyne.EvidenceExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static totah.lab.athena.system.rules.EventPayload.*;

/** The two pinned Top8000 numerical profiles only. No residue perception or coordinate geometry. */
final class ResidueReferenceTables {
    static final String ROOT="residue-validation-v1/";
    static final String MANIFEST_SHA="df2997743d0c9ef1505fa2dfa0045ce2a1af8c33222f14d8fd4f0336505830c2";
    static final List<String> CLASSES=List.of("GENERAL","GLYCINE","CIS_PROLINE","TRANS_PROLINE","PRE_PROLINE","ILE_VAL");
    static final List<String> RAMA_FILES=List.of("general-noGPIVpreP","gly-sym","cispro","transpro","prepro-noGP","ileval-nopreP");
    private final double[][] rama;
    private final Map<String,float[]> rotamer;
    private final JsonNode sources;
    private ResidueReferenceTables(double[][] rama,Map<String,float[]> rotamer,JsonNode sources){this.rama=rama;this.rotamer=Map.copyOf(rotamer);this.sources=sources.deepCopy();}
    interface Reader { byte[] read(String file) throws IOException; }
    static byte[] resource(String file)throws IOException {
        try(var in=ResidueReferenceTables.class.getResourceAsStream(ROOT+file)) {
            if(in==null)throw new IOException("Missing pinned residue reference artifact: "+file);
            return in.readAllBytes();
        }
    }
    static ResidueReferenceTables load()throws IOException{return load(ResidueReferenceTables::resource);}
    static ResidueReferenceTables load(Reader reader)throws IOException {
        byte[] manifest=reader.read("SOURCES.json");
        if(!EvidenceExchange.sha256(manifest).equals(MANIFEST_SHA))throw new IOException("Reference manifest changed");
        var sources=JSON.readTree(manifest);var data=new HashMap<String,byte[]>();
        for(var s:sources){String file=s.path("file").asText();byte[] b=reader.read(file);if(!EvidenceExchange.sha256(b).equals(s.path("sha256").asText()))throw new IOException("Reference bytes changed: "+file);data.put(file,b);}
        String header=new String(data.get("reference/cctbx--mmtbx__validation__ramachandran__rama8000_tables.h"),StandardCharsets.UTF_8);
        var tables=new double[6][32400];var names=List.of("general","glycine","cis_pro","trans_pro","pre_pro","ile_val");
        for(int i=0;i<6;i++) {
            var matcher=Pattern.compile("const double linear_table_"+names.get(i)+"\\[\\] = \\{([^}]+)}",Pattern.DOTALL).matcher(header);
            if(!matcher.find())throw new IOException("Missing exact compiled Rama table");
            var values=matcher.group(1).split(",");if(values.length!=32400)throw new IOException("Incomplete compiled table");
            for(int j=0;j<values.length;j++){double q=Double.parseDouble(values[j].trim());if(!Double.isFinite(q)||q<0||q>1)throw new IOException("Invalid compiled score");tables[i][j]=q;}
        }
        var rot=new HashMap<String,float[]>();for(var name:List.of("SER","THR","VAL"))rot.put(name,parseRotamer(new String(data.get(rotamerFile(name)),StandardCharsets.UTF_8)));
        return new ResidueReferenceTables(tables,rot,sources);
    }
    static String ramaFile(int c){require(c>=0&&c<6,"Unknown Rama class");return "reference/reference_data--Top8000__Top8000_ramachandran_pct_contour_grids__rama8000-"+RAMA_FILES.get(c)+".data";}
    static String rotamerFile(String name){require(Set.of("SER","THR","VAL").contains(name),"Unsupported rotamer domain");return "reference/reference_data--Top8000__Top8000_rotamer_pct_contour_grids__rota8000-"+name.toLowerCase(Locale.ROOT)+".data";}
    static float[] parseRotamer(String text)throws IOException {
        var lines=text.lines().toList();if(lines.size()<5||!lines.get(1).equals("# Number of dimensions: 1")||!lines.get(3).equals("#   x1: 0.0 360.0 360 true")||!lines.get(4).contains("Value is last"))throw new IOException("Wrong rotamer grid header");
        var table=new float[360];var seen=new BitSet(360);
        for(int i=5;i<lines.size();i++) {
            var fields=lines.get(i).trim().split("\\s+");if(fields.length!=2)throw new IOException("Invalid grid record");
            try {
                double x=Double.parseDouble(fields[0]),q=Double.parseDouble(fields[1]);int bin=(int)Math.floor(x);
                if(!Double.isFinite(x)||x!=bin+0.5||bin<0||bin>=360||!Double.isFinite(q)||q<0||q>1||seen.get(bin))throw new IOException("Invalid/duplicate grid record");
                seen.set(bin);table[bin]=(float)q; // Upstream flex.float stores binary32, including source-defined sparse zeros.
            }catch(NumberFormatException e){throw new IOException("Invalid grid number",e);}
        }return table;
    }
    JsonNode sources(){return sources.deepCopy();}
    static String bits(double value){return String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(value));}
    private static String bits(float value){return String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits(value));}
    static double allowed(int c){require(c>=0&&c<6,"Unknown Rama class");return c==0?0.0005:c==2?0.002:0.001;}
    static String category(double q,double allowed){require(Double.isFinite(q)&&q>=0&&q<=1,"Invalid reference score");return q>=0.02?"FAVORED":q>=allowed?"ALLOWED":"OUTLIER";}
    static int ramaClass(String identity,boolean nextPro,Double omega) {
        require(ResidueContextSource.IDENTITIES.contains(identity),"Unresolved/unrecognized canonical identity");
        if(identity.equals("GLY"))return 1;
        if(identity.equals("PRO")){require(omega!=null&&Double.isFinite(omega)&&Math.abs(omega)<=180,"Unresolved PRO omega");return omega>-90&&omega<90?2:3;}
        if(nextPro)return 4;
        return Set.of("ILE","VAL").contains(identity)?5:0;
    }
    record Score(double value,String category,ObjectNode interpolation) { }
    private record Axis(double input,double ranged,double lower,double upper,int low,int high) {
        ObjectNode json(){var n=JSON.createObjectNode();n.put("input",input);n.put("ranged",ranged);n.put("lowerCoordinate",lower);n.put("upperCoordinate",upper);n.put("lowerBin",low);n.put("upperBin",high);return n;}
    }
    private static Axis axis(double input) {
        require(Double.isFinite(input)&&Math.abs(input)<=180,"Only coordinate-derived bounded torsions admitted");
        double v=input;while(v>180)v-=360;while(v< -180)v+=360;
        double low=Math.floor(v);if((int)low%2==0)low-=1;
        double high=Math.ceil(v);if((int)high%2==0)high+=1;if(low==high)high+=2;
        return new Axis(input,v,low,high,bin(low),bin(high));
    }
    private static int bin(double v){int b=(int)((v+179)/2.0);if(b>179)b-=180;if(b<0)b+=180;return b;}
    private static double interpolate(double x,double x1,double x2,double y1,double y2){double dx=x2-x1,dy=y2-y1;return y1+dy*(x-x1)/dx;}
    Score rama(int c,double phi,double psi) {
        require(c>=0&&c<6,"Unknown Rama class");var x=axis(phi);var y=axis(psi);var t=rama[c];
        double ll=t[x.low*180+y.low],hl=t[x.high*180+y.low],lh=t[x.low*180+y.high],hh=t[x.high*180+y.high];
        double a=interpolate(x.ranged,x.lower,x.upper,ll,hl),b=interpolate(x.ranged,x.lower,x.upper,lh,hh);
        double q=interpolate(y.ranged,y.lower,y.upper,a,b);
        var n=JSON.createObjectNode();n.set("phi",x.json());n.set("psi",y.json());var corners=n.putArray("corners");
        int[][] positions={{x.low,y.low},{x.high,y.low},{x.low,y.high},{x.high,y.high}};
        for(var pos:positions){double v=t[pos[0]*180+pos[1]];var corner=corners.addObject();corner.put("phiBin",pos[0]);corner.put("psiBin",pos[1]);corner.put("value",v);corner.put("binary64Hex",bits(v));}
        n.put("scoreBinary64Hex",bits(q));return new Score(q,category(q,allowed(c)),n);
    }
    Score rotamer(String residue,double chi) {
        require(rotamer.containsKey(residue)&&Double.isFinite(chi)&&chi>=-180&&chi<=360,"Unsupported rotamer numeric input");
        // 360 is retained solely for the pinned upstream characterization probe. Public input is a measured torsion.
        int home=Math.min((int)Math.floor(chi),359);double center=home+0.5;
        int neighbor=chi<center?home-1:home+1;double w=Math.abs(chi-center);
        int hi=Math.floorMod(home,360),ni=Math.floorMod(neighbor,360);float a=rotamer.get(residue)[hi],b=rotamer.get(residue)[ni];
        double q=0;q+=(1.0-w)*(double)a;q+=w*(double)b;
        var n=JSON.createObjectNode();n.put("input",chi);n.put("homeLogicalBin",home);n.put("neighborLogicalBin",neighbor);n.put("homeWrappedBin",hi);n.put("neighborWrappedBin",ni);n.put("homeCenter",center);n.put("neighborWeight",w);n.put("homeStoredBinary32Hex",bits(a));n.put("neighborStoredBinary32Hex",bits(b));n.put("homeValue",(double)a);n.put("neighborValue",(double)b);n.put("scoreBinary64Hex",bits(q));
        return new Score(q,category(q,0.003),n);
    }
}
