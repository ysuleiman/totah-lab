package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.*;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

/** Serial evaluation in bounded blocks; never retains the full AO grid or rho. */
public final class BlockedXc implements AutoCloseable {
    private final List<ContractedGaussian> basis;
    private final GridPointSource points;
    private final String systemHash,basisHash,gridHash,protocol;
    private final int blockSize;
    private final ExactWorkers workers;
    private record Block(int count,double[] coordinates,double[] values,XcAccumulator.PointValue[] rho,long aoNanos,long xcNanos) {}
    private final double[][] normalizedCoefficients;
    public BlockedXc(QuantumSystem system,List<ContractedGaussian> basis,GridDefinition definition,int blockSize)throws IOException {
        this(system,basis,definition,blockSize,1);
    }
    public BlockedXc(QuantumSystem system,List<ContractedGaussian> basis,GridDefinition definition,int blockSize,int workerCount)throws IOException {
        workers=new ExactWorkers(workerCount);
        if(blockSize<1||blockSize>65536)throw new IllegalArgumentException("Block size must be 1..65536");
        this.basis=List.copyOf(basis);if(this.basis.isEmpty())throw new IllegalArgumentException("Empty basis");
        normalizedCoefficients=this.basis.stream().map(AoGrid::prepare).toArray(double[][]::new);
        this.blockSize=blockSize;points=new GridPointSource(system,definition);systemHash=IntegralMatrixData.systemHash(system);
        basisHash=IntegralMatrixData.basisGeometryHash(basis);gridHash=points.receiptHash();
        protocol="aether-blocked-XC-14-2;prepared-AO-normalization-products;block="+blockSize+";workers="+workerCount+";ordered-caller-reduction;"+definition.protocol()+";"+XcIntegration.PROTOCOL;
    }
    public synchronized Result evaluate(DensityMatrix density,LdaFunctional functional) {
        long started=System.nanoTime(),aoNanos=0,xcNanos=0;
        OccupiedDensityCalculator.requireEqual(systemHash,density.systemHash(),"blocked grid/system");
        OccupiedDensityCalculator.requireEqual(basisHash,density.basisGeometryHash(),"blocked grid/basis/order");
        int n=basis.size(),capacity=Math.min(blockSize,points.size());
        var sum=new XcAccumulator(density,functional);long[] times=new long[2];
        var available=new ArrayDeque<Block>();
        Iterable<Block> blocks=()->new Iterator<>() {
            int start;
            public boolean hasNext(){return start<points.size();}
            public Block next(){
                if(!hasNext())throw new NoSuchElementException();
                int count=Math.min(capacity,points.size()-start);
                var reusable=available.pollFirst();
                double[] coordinates=reusable==null?new double[4*capacity]:reusable.coordinates();
                long t=System.nanoTime();points.fill(start,count,coordinates);times[0]+=System.nanoTime()-t;start+=count;
                return new Block(count,coordinates,reusable==null?new double[n*capacity]:reusable.values(),
                        reusable==null?new XcAccumulator.PointValue[capacity]:reusable.rho(),0,0);
            }
        };
        workers.forEachOrdered(blocks,block->{
            long t=System.nanoTime();
            for(int g=0;g<block.count();g++) {
                var point=new Point3D(block.coordinates()[4*g],block.coordinates()[4*g+1],block.coordinates()[4*g+2]);
                for(int i=0;i<n;i++)block.values()[g*n+i]=AoGrid.evaluate(basis.get(i),point,normalizedCoefficients[i]);
            }
            long ao=System.nanoTime()-t;t=System.nanoTime();
            for(int g=0;g<block.count();g++)block.rho()[g]=sum.evaluate(block.values(),g*n);
            return new Block(block.count(),block.coordinates(),block.values(),block.rho(),ao,System.nanoTime()-t);
        },block->{
            times[0]+=block.aoNanos();times[1]+=block.xcNanos();long t=System.nanoTime();
            for(int g=0;g<block.count();g++)sum.accept(block.coordinates()[4*g+3],block.values(),g*n,block.rho()[g]);
            times[1]+=System.nanoTime()-t;
            available.addLast(block); // Only the caller recycles a fully consumed block.
        });
        aoNanos=times[0];xcNanos=times[1];
        String rhoHash=sum.finish();String sources=gridHash+"\n"+density.densityHash()+"\n"+functional.protocol()+"\n"+rhoHash;
        var potential=new ExecutionMatrix(ExecutionMatrix.Kind.XC_POTENTIAL,basis,systemHash,density.densityHash(),protocol,sources,(i,j)->sum.matrix[i][j]);
        String hash=ContentHash.sha256(protocol+"\n"+sources+"\n"+potential.receiptHash()+"\n"+ContentHash.number(sum.electrons)+"\n"+ContentHash.number(sum.energy)+"\n"+sum.clamped+"\nSCREENING_ONLY");
        return new Result(potential,sum.electrons,sum.energy,sum.clamped,rhoHash,gridHash,hash,
                new Performance(points.size(),blockSize,workers.capacity()*capacity*(8L*(n+4)+48L),aoNanos,xcNanos,System.nanoTime()-started));
    }
    @Override public synchronized void close(){workers.close();}
    public String gridReceiptHash(){return gridHash;}
    public int gridPoints(){return points.size();}
    public record Result(ExecutionMatrix potential,double integratedElectrons,double energyHartree,int roundoffClampedPoints,
                         String densityGridHash,String gridReceiptHash,String receiptHash,Performance performance) {
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
    public record Performance(int gridPoints,int blockSize,long blockArrayBytes,long gridAndAoNanos,long xcKernelNanos,long totalNanos){}
}
