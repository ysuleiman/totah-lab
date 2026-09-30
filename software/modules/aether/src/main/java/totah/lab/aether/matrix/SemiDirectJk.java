package totah.lab.aether.matrix;

import java.io.IOException;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** One contiguous cache scan per density; no per-integral file access or tensor expansion. */
public final class SemiDirectJk implements AutoCloseable {
    public static final String PROTOCOL="aether-SEMI_DIRECT_CACHE-JK-14.1-1;canonical-packed-scan;"
            +"unique-eightfold-permutations;upper-output-triangle;ordered-sums;"+DensityMatrix.CONVENTION;
    private final EriDiskCache cache;
    private final EriDiskCache.Reader reader;
    private final String systemHash,basisHash;
    private final EriDiskCache.Pairs pairs;
    public SemiDirectJk(QuantumSystem system,EriDiskCache cache)throws IOException {this(system,cache,false);}
    SemiDirectJk(QuantumSystem system,EriDiskCache cache,boolean mapped)throws IOException {
        this.cache=cache;systemHash=IntegralMatrixData.systemHash(system);basisHash=IntegralMatrixData.basisGeometryHash(cache.basis());
        pairs=new EriDiskCache.Pairs(cache.dimension());reader=mapped?cache.experimentalMappedReader():cache.reader();
    }
    public synchronized Result calculate(DensityMatrix density)throws IOException {
        OccupiedDensityCalculator.requireEqual(systemHash,density.systemHash(),"cached J/K system");
        OccupiedDensityCalculator.requireEqual(basisHash,density.basisGeometryHash(),"cached J/K basis/geometry/AO ordering");
        long start=System.nanoTime();int n=cache.dimension();double[][] j=new double[n][n],k=new double[n][n];
        int[][] permutations=new int[8][4];long[] counts=new long[2];
        var scan=reader.scan((first,data)->{
            int a=EriDiskCache.pairRow(first),b=(int)(first-EriDiskCache.triangle(a));
            while(data.hasRemaining()) {
                double value=data.getDouble();if(!Double.isFinite(value))throw new ArithmeticException("Nonfinite cached ERI");
                int count=DirectExactJk.permutations(pairs.i[a],pairs.j[a],pairs.i[b],pairs.j[b],permutations);
                for(int p=0;p<count;p++) {
                    var v=permutations[p];
                    if(v[0]<=v[1]){add(j,v[0],v[1],density.get(v[2],v[3])*value);counts[0]++;}
                    if(v[0]<=v[2]){add(k,v[0],v[2],density.get(v[1],v[3])*value);counts[1]++;}
                }
                if(++b>a){a++;b=0;}
            }
        });
        var jm=new ExecutionMatrix(ExecutionMatrix.Kind.COULOMB,cache.basis(),systemHash,density.densityHash(),PROTOCOL,cache.identity()+"\n"+cache.payloadSha256(),(i,l)->j[i][l]);
        var km=new ExecutionMatrix(ExecutionMatrix.Kind.EXCHANGE,cache.basis(),systemHash,density.densityHash(),PROTOCOL,cache.identity()+"\n"+cache.payloadSha256(),(i,l)->k[i][l]);
        String hash=ContentHash.sha256(PROTOCOL+"\n"+cache.identity()+"\n"+cache.payloadSha256()+"\n"+systemHash+"\n"+density.densityHash()+"\n"+jm.receiptHash()+"\n"+km.receiptHash()+"\nSCREENING_ONLY");
        return new Result(jm,km,hash,scan,counts[0],counts[1],System.nanoTime()-start);
    }
    private static void add(double[][] a,int i,int j,double term) {
        double next=a[i][j]+term;if(!Double.isFinite(term)||!Double.isFinite(next))throw new ArithmeticException("Nonfinite cached J/K");a[i][j]=next;
    }
    @Override public void close()throws IOException{reader.close();}
    public record Result(ExecutionMatrix coulomb,ExecutionMatrix exchange,String receiptHash,EriDiskCache.Scan scan,
                         long jAccumulations,long kAccumulations,long elapsedNanos) {
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
}
