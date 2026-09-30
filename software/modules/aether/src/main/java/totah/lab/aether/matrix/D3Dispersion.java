package totah.lab.aether.matrix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Geometry-only, nonperiodic PBE-D3(BJ), explicitly pairwise (ATM is not implemented). */
public final class D3Dispersion {
    public static final String DATA_SHA256="ae2907bf5ab4e51b320944d9908a7ff6879431cf8872883f2409bfa53cd08c43";
    public static final String PROTOCOL="aether-D3-16-1;D3(BJ);PBE;s6=1;s8=0.7875;a1=0.4289;a2=4.4407;s9=0;ATM=NOT_IMPLEMENTED;"
            +"CN=logistic-k16;rcov=Pyykko2009*4/3;CN-cutoff=40-bohr;pair-cutoff=60-bohr;inclusive;"
            +"reference-weight=exp(-4*(CN-CNref)^2);underflow=max-CN-reference;simple-dftd3-1.2.1;"
            +"real-nuclei-only;nonperiodic;bohr;hartree;Java21-StrictMath;atomic-data-sha256="+DATA_SHA256;
    private final Map<Integer,AtomData> atoms;
    private final Map<String,Double> coefficients;
    private record AtomData(double radius,double r4r2,List<Double> references){}
    private D3Dispersion(Map<Integer,AtomData> atoms,Map<String,Double> coefficients){this.atoms=Map.copyOf(atoms);this.coefficients=Map.copyOf(coefficients);}
    /** Small verified atomic parameter table; no runtime external/native dependency. */
    public static D3Dispersion load() throws IOException {
        byte[] data;
        try(var in=D3Dispersion.class.getResourceAsStream("/totah/lab/aether/dispersion/d3-reference.csv")) {
            if(in==null)throw new IOException("Missing D3 atomic reference data");data=in.readAllBytes();
        }
        if(!ContentHash.sha256(data).equals(DATA_SHA256))throw new IOException("D3 atomic reference checksum mismatch");
        var atoms=new HashMap<Integer,AtomData>();var c6=new HashMap<String,Double>();
        // CODATA2018 conversion used by upstream mctc-lib (see D3_PROTOCOL.md).
        double bohr=(6.62607015e-34/(2*StrictMath.PI))/(9.1093837015e-31*299792458.0*7.2973525693e-3)*1e10;
        for(var line:new String(data,StandardCharsets.US_ASCII).split("\n")) {
            if(line.startsWith("#"))continue;var c=line.split(",");int z=Integer.parseInt(c[1]);
            if(c[0].equals("ATOM")) {
                var refs=new ArrayList<Double>();for(int i=4;i<c.length;i++)refs.add(Double.parseDouble(c[i]));
                atoms.put(z,new AtomData((4.0/3.0)*(Double.parseDouble(c[2])/bohr),StrictMath.sqrt(.5*Double.parseDouble(c[3])*StrictMath.sqrt(z)),List.copyOf(refs)));
            }else if(c[0].equals("C6"))c6.put(key(z,Integer.parseInt(c[2]),Integer.parseInt(c[3]),Integer.parseInt(c[4])),Double.parseDouble(c[5]));
            else throw new IOException("Unknown D3 reference row");
        }
        return new D3Dispersion(atoms,c6);
    }
    private static String key(int z,int w,int a,int b){return z+":"+w+":"+a+":"+b;}
    public record Pair(int i,int j,double distanceBohr,double c6,double c8,double damping6,double damping8,double energy6Hartree,double energy8Hartree) {
        public double hartree(){return energy6Hartree+energy8Hartree;}
    }
    public static final class Result {
        private final String systemHash,receiptHash;
        private final List<Double> coordination;
        private final List<Pair> pairs;
        private final double pairwise;
        private final long elapsed;
        private Result(String systemHash,List<Double> coordination,List<Pair> pairs,double pairwise,long elapsed,String receiptHash) {
            this.systemHash=systemHash;this.coordination=List.copyOf(coordination);this.pairs=List.copyOf(pairs);this.pairwise=pairwise;this.elapsed=elapsed;this.receiptHash=receiptHash;
        }
        public String systemHash(){return systemHash;} public String receiptHash(){return receiptHash;}
        public String protocol(){return PROTOCOL;} public List<Double> coordinationNumbers(){return coordination;}
        public List<Pair> pairs(){return pairs;} public double pairwiseHartree(){return pairwise;}
        public OptionalDouble threeBodyHartree(){return OptionalDouble.empty();}
        public double totalHartree(){return pairwise;} public long elapsedNanos(){return elapsed;}
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
    public Result calculate(QuantumSystem system) {
        Objects.requireNonNull(system);long start=System.nanoTime();int n=system.nuclei().size();var ad=new AtomData[n];var z=new int[n];var cn=new double[n];
        for(int i=0;i<n;i++){z[i]=(int)system.nuclei().get(i).charge();ad[i]=Objects.requireNonNull(atoms.get(z[i]),"Unsupported D3 element");}
        for(int i=0;i<n;i++)for(int j=0;j<i;j++) {
            double r=distance(system,i,j);if(r<=40){double value=1/(1+StrictMath.exp(-16*((ad[i].radius+ad[j].radius)/r-1)));cn[i]+=value;cn[j]+=value;}
        }
        double[][] weights=new double[n][];
        for(int i=0;i<n;i++) {
            var refs=ad[i].references;weights[i]=new double[refs.size()];double sum=0;
            for(int a=0;a<refs.size();a++){double d=cn[i]-refs.get(a);weights[i][a]=StrictMath.exp(-4*d*d);sum+=weights[i][a];}
            if(sum==0||!Double.isFinite(1/sum)){double max=Collections.max(refs);for(int a=0;a<refs.size();a++)weights[i][a]=refs.get(a)==max?1:0;}
            else for(int a=0;a<refs.size();a++)weights[i][a]/=sum;
        }
        var pairs=new ArrayList<Pair>();double total=0;
        for(int i=0;i<n;i++)for(int j=0;j<i;j++) {
            double r=distance(system,i,j);if(r>60)continue;double c6=0;
            for(int a=0;a<weights[i].length;a++)for(int b=0;b<weights[j].length;b++) {
                String k=z[i]>z[j]?key(z[i],z[j],a,b):key(z[j],z[i],b,a);
                c6+=weights[i][a]*weights[j][b]*coefficients.get(k);
            }
            double ratio=3*ad[i].r4r2*ad[j].r4r2,c8=c6*ratio,r0=.4289*StrictMath.sqrt(ratio)+4.4407;
            double r2=r*r,r6=r2*r2*r2,r8=r6*r2,d2=r0*r0,d6=d2*d2*d2,d8=d6*d2;
            var pair=new Pair(i,j,r,c6,c8,r6/(r6+d6),r8/(r8+d8),-c6/(r6+d6),-.7875*c8/(r8+d8));
            if(!Double.isFinite(pair.hartree()))throw new ArithmeticException("Nonfinite D3 pair");pairs.add(pair);total+=pair.hartree();
        }
        if(!Double.isFinite(total))throw new ArithmeticException("Nonfinite D3 total");
        String systemHash=IntegralMatrixData.systemHash(system);var hash=ContentHash.accumulator().line(PROTOCOL).line(systemHash);
        var counts=new ArrayList<Double>();for(double c:cn){counts.add(c);hash.line(ContentHash.number(c));}
        for(var pair:pairs)hash.line(pair.toString());hash.line(ContentHash.number(total)).line("SCREENING_ONLY");
        return new Result(systemHash,counts,pairs,total,System.nanoTime()-start,hash.finish());
    }
    private static double distance(QuantumSystem s,int i,int j) {
        var a=s.nuclei().get(i).centerBohr();var b=s.nuclei().get(j).centerBohr();double x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();double r=StrictMath.sqrt(x*x+y*y+z*z);
        if(!Double.isFinite(r)||r<=0)throw new IllegalArgumentException("Nonfinite or coincident D3 real centers");return r;
    }
}
