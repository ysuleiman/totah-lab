package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.*;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.*;
import totah.lab.gaia.geometry.Point3D;

/** GGA extension of the frozen grid source: bounded AO/derivative blocks, ordered reductions. */
public final class BlockedPbe implements AutoCloseable {
    private final List<ContractedGaussian> basis;private final double[][] products;
    private final GridPointSource points;private final ExactWorkers workers;
    private final int blockSize;private final String systemHash,basisHash,gridHash,protocol;
    private static final class Block {
        final double[] coordinates,ao,dx,dy,dz,matrix;final DensityGradient[] rho;final PbeFunctional.Value[] xc;
        int count;double energy,electrons;int clamped,tail;final long[] times=new long[5];
        Block(int size,int n){coordinates=new double[4*size];ao=new double[size*n];dx=new double[size*n];dy=new double[size*n];dz=new double[size*n];matrix=new double[n*n];rho=new DensityGradient[size];xc=new PbeFunctional.Value[size];}
    }
    public BlockedPbe(QuantumSystem system,List<ContractedGaussian> input,GridDefinition definition,int blockSize,int workerCount)throws IOException {
        if(blockSize<1||blockSize>65536)throw new IllegalArgumentException("Invalid block size");
        basis=List.copyOf(input);if(basis.isEmpty())throw new IllegalArgumentException("Empty basis");
        this.blockSize=blockSize;workers=new ExactWorkers(workerCount);points=new GridPointSource(system,definition);
        products=basis.stream().map(AoGrid::prepare).toArray(double[][]::new);systemHash=IntegralMatrixData.systemHash(system);
        basisHash=IntegralMatrixData.basisGeometryHash(basis);gridHash=points.receiptHash();
        protocol="aether-blocked-PBE-15-1;block="+blockSize+";workers="+workerCount+";ordered-block-triangle-reduction;"+definition.protocol()+";"+PbeFunctional.PROTOCOL+";"+AoFirstDerivatives.PROTOCOL;
    }
    public synchronized Result evaluate(DensityMatrix density) {
        OccupiedDensityCalculator.requireEqual(systemHash,density.systemHash(),"PBE system");
        OccupiedDensityCalculator.requireEqual(basisHash,density.basisGeometryHash(),"PBE basis/geometry/order");
        long started=System.nanoTime();int n=basis.size(),capacity=Math.min(blockSize,points.size());
        var reuse=new ArrayDeque<Block>();double[] totals=new double[2],matrix=new double[n*n];int[] counts=new int[2];long[] times=new long[5];
        var hash=new NumericalEvidenceHash("aether-rho-gradient-15-1");
        Iterable<Block> blocks=()->new Iterator<>() {
            int start;
            public boolean hasNext(){return start<points.size();}
            public Block next(){if(!hasNext())throw new NoSuchElementException();var b=reuse.pollFirst();if(b==null)b=new Block(capacity,n);
                b.count=Math.min(capacity,points.size()-start);points.fill(start,b.count,b.coordinates);start+=b.count;return b;}
        };
        workers.forEachOrdered(blocks,b->{
            Arrays.fill(b.matrix,0);b.energy=0;b.electrons=0;b.clamped=0;b.tail=0;
            long t=System.nanoTime();
            for(int g=0;g<b.count;g++){var p=new Point3D(b.coordinates[4*g],b.coordinates[4*g+1],b.coordinates[4*g+2]);for(int i=0;i<n;i++)b.ao[g*n+i]=AoGrid.evaluate(basis.get(i),p,products[i]);}
            b.times[0]=System.nanoTime()-t;t=System.nanoTime();
            for(int g=0;g<b.count;g++){var p=new Point3D(b.coordinates[4*g],b.coordinates[4*g+1],b.coordinates[4*g+2]);for(int i=0;i<n;i++){var v=AoFirstDerivatives.evaluate(basis.get(i),p,products[i]);b.dx[g*n+i]=v.x();b.dy[g*n+i]=v.y();b.dz[g*n+i]=v.z();}}
            b.times[1]=System.nanoTime()-t;t=System.nanoTime();
            for(int g=0;g<b.count;g++)b.rho[g]=DensityGradient.evaluate(density,b.ao,b.dx,b.dy,b.dz,g*n);
            b.times[2]=System.nanoTime()-t;t=System.nanoTime();
            for(int g=0;g<b.count;g++)b.xc[g]=PbeFunctional.evaluate(b.rho[g].rho(),b.rho[g].sigma());
            b.times[3]=System.nanoTime()-t;t=System.nanoTime();
            for(int g=0;g<b.count;g++) {
                var r=b.rho[g];var xc=b.xc[g];double w=b.coordinates[4*g+3];b.energy+=w*xc.energyDensity();b.electrons+=w*r.rho();
                if(r.roundoffClamped())b.clamped++;if(r.rho()<PbeFunctional.CORRELATION_DENSITY_FLOOR)b.tail++;
                double vr=w*xc.vrho(),vs=2*w*xc.vsigma();int off=g*n;
                for(int i=0;i<n;i++)for(int j=i;j<n;j++) {
                    double a=b.ao[off+i],c=b.ao[off+j];
                    double grad=r.x()*(b.dx[off+i]*c+a*b.dx[off+j])+r.y()*(b.dy[off+i]*c+a*b.dy[off+j])+r.z()*(b.dz[off+i]*c+a*b.dz[off+j]);
                    b.matrix[i*n+j]+=vr*a*c+vs*grad;
                }
            }
            b.times[4]=System.nanoTime()-t;return b;
        },b->{
            for(int i=0;i<5;i++)times[i]+=b.times[i];totals[0]+=b.energy;totals[1]+=b.electrons;counts[0]+=b.clamped;counts[1]+=b.tail;
            for(int i=0;i<matrix.length;i++)matrix[i]+=b.matrix[i];
            for(int g=0;g<b.count;g++){var r=b.rho[g];hash.add(r.rho());hash.add(r.x());hash.add(r.y());hash.add(r.z());hash.add(r.sigma());}
            reuse.addLast(b);
        });
        if(!Double.isFinite(totals[0])||!Double.isFinite(totals[1]))throw new ArithmeticException("Nonfinite PBE integral");
        String rhoHash=hash.finish(),sources=gridHash+"\n"+density.densityHash()+"\n"+rhoHash;
        var potential=new ExecutionMatrix(ExecutionMatrix.Kind.XC_POTENTIAL,basis,systemHash,density.densityHash(),protocol,sources,(i,j)->matrix[i*n+j]);
        String receipt=ContentHash.sha256(protocol+"\n"+sources+"\n"+potential.receiptHash()+"\n"+ContentHash.number(totals[0])+"\n"+ContentHash.number(totals[1])+"\n"+Arrays.toString(counts)+"\nSCREENING_ONLY");
        long bufferEstimate=workers.capacity()*(capacity*(32L*n+160)+8L*n*n);
        return new Result(potential,totals[0],totals[1],counts[0],counts[1],rhoHash,gridHash,receipt,
                new Performance(points.size(),blockSize,bufferEstimate,times[0],times[1],times[2],times[3],times[4],System.nanoTime()-started));
    }
    @Override public synchronized void close(){workers.close();}
    public record Performance(int gridPoints,int blockSize,long blockStorageEstimateBytes,long aoNanos,long derivativeNanos,long densityGradientNanos,long functionalNanos,long potentialNanos,long totalNanos){}
    public record Result(ExecutionMatrix potential,double energyHartree,double integratedElectrons,int roundoffClampedPoints,int lowDensityTailPoints,
                         String densityGradientHash,String gridHash,String receiptHash,Performance performance){public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}}
}
