package totah.lab.aether.matrix;

import java.io.IOException;
import totah.lab.aether.model.QuantumSystem;

/** Pure-DFT J-only view of the frozen binary ERI cache. Never allocates or evaluates K. */
public final class CachedCoulomb implements AutoCloseable {
    public static final String PROTOCOL="aether-cached-Coulomb-15-1;canonical-pair-triangle;COULOMB_ONLY;no-K";
    private final EriDiskCache cache;private final EriDiskCache.Reader reader;private final EriDiskCache.Pairs pairs;
    private final String systemHash,basisHash;
    public CachedCoulomb(QuantumSystem system,EriDiskCache cache)throws IOException {
        this.cache=cache;systemHash=IntegralMatrixData.systemHash(system);basisHash=IntegralMatrixData.basisGeometryHash(cache.basis());pairs=new EriDiskCache.Pairs(cache.dimension());reader=cache.reader();
    }
    public synchronized Result calculate(DensityMatrix p)throws IOException {
        OccupiedDensityCalculator.requireEqual(systemHash,p.systemHash(),"J-only system");OccupiedDensityCalculator.requireEqual(basisHash,p.basisGeometryHash(),"J-only ordered basis");
        long started=System.nanoTime();double[] j=new double[pairs.i.length];long[] counts={0};
        var scan=reader.scan((first,data)->{
            int a=EriDiskCache.pairRow(first),b=(int)(first-EriDiskCache.triangle(a));
            while(data.hasRemaining()) {
                double v=data.getDouble();if(!Double.isFinite(v))throw new ArithmeticException("Nonfinite ERI");
                j[a]+=(pairs.i[b]==pairs.j[b]?1:2)*p.get(pairs.i[b],pairs.j[b])*v;counts[0]++;
                if(a!=b){j[b]+=(pairs.i[a]==pairs.j[a]?1:2)*p.get(pairs.i[a],pairs.j[a])*v;counts[0]++;}
                if(++b>a){a++;b=0;}
            }
        });
        var result=new ExecutionMatrix(ExecutionMatrix.Kind.COULOMB,cache.basis(),systemHash,p.densityHash(),PROTOCOL,
                cache.identity()+"\n"+cache.payloadSha256(),(i,k)->j[(int)EriDiskCache.triangle(Math.max(i,k))+Math.min(i,k)]);
        return new Result(result,scan,counts[0],System.nanoTime()-started);
    }
    @Override public void close()throws IOException{reader.close();}
    public record Result(ExecutionMatrix coulomb,EriDiskCache.Scan scan,long accumulations,long elapsedNanos){}
}
