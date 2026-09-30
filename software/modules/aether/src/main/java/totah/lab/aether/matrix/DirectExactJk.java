package totah.lab.aether.matrix;

import java.util.*;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.integral.PreparedRepulsion;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Bounded shell-quartet evaluation with strictly ordered caller-thread contraction. */
public final class DirectExactJk implements AutoCloseable {
    public enum Contraction { COULOMB_ONLY, COULOMB_AND_EXCHANGE }
    public static final String PROTOCOL="aether-direct-exact-JK-14-2;shell-first-appearance-order;shell-pair-triangle;"
            +"canonical-AO-quartets;unique-eightfold-permutations;upper-output-triangle;ordered-sums;"
            +"prepared-Hermite-pairs;shell-aux-context-cache-max4096;aux-degree-at-most8;bounded-Coulomb-recurrence-memo;no-screening;no-quartet-cache;"+DensityMatrix.CONVENTION;
    private final List<ContractedGaussian> basis;
    private final String systemHash,basisHash,source;
    private final PreparedRepulsion evaluator;
    private final List<List<AoPair>> shellPairs;
    private final ExactWorkers workers;
    private final ThreadLocal<PreparedRepulsion> sessions;
    private record Block(int left,int right) {}
    private record Evaluated(Block block,double[] values,long primitiveCount,long nanos) {}
    private record AoPair(int i,int j,int index) {}
    public DirectExactJk(QuantumSystem system,List<ContractedGaussian> functions) {
        this(system,functions,1);
    }
    public DirectExactJk(QuantumSystem system,List<ContractedGaussian> functions,int workerCount) {
        workers=new ExactWorkers(workerCount);
        basis=List.copyOf(functions);if(basis.isEmpty())throw new IllegalArgumentException("Empty basis");
        systemHash=IntegralMatrixData.systemHash(system);basisHash=IntegralMatrixData.basisGeometryHash(basis);
        source=ContentHash.sha256(PROTOCOL+";workers="+workerCount+";batch=16;inflight=2*workers"+"\n"+systemHash+"\n"+basisHash+"\n"+IntegralMatrixData.protocol(ElectronRepulsionTensor.PROTOCOL,basis));
        evaluator=new PreparedRepulsion(basis);
        sessions=ThreadLocal.withInitial(evaluator::newSession);
        var groups=new LinkedHashMap<String,List<Integer>>();
        for(int i=0;i<basis.size();i++)groups.computeIfAbsent(shellKey(basis.get(i)),k->new ArrayList<>()).add(i);
        var shells=new ArrayList<>(groups.values());var pairs=new ArrayList<List<AoPair>>();
        for(int a=0;a<shells.size();a++)for(int b=0;b<=a;b++) {
            var block=new ArrayList<AoPair>();
            for(int i:shells.get(a))for(int j:shells.get(b)) {
                if(a==b&&j>i)continue;
                int hi=Math.max(i,j),lo=Math.min(i,j);block.add(new AoPair(hi,lo,pair(hi,lo)));
            }
            pairs.add(List.copyOf(block));
        }
        shellPairs=List.copyOf(pairs);
    }
    public synchronized Result calculate(DensityMatrix density,Contraction contraction) {
        long start=System.nanoTime(),integralNanos=0,accumulationNanos=0,quartets=0,primitiveQuartets=0,jCount=0,kCount=0;
        Objects.requireNonNull(contraction);
        OccupiedDensityCalculator.requireEqual(systemHash,density.systemHash(),"direct system");
        OccupiedDensityCalculator.requireEqual(basisHash,density.basisGeometryHash(),"direct basis/geometry/order");
        int n=basis.size();double[][] j=new double[n][n],k=contraction==Contraction.COULOMB_AND_EXCHANGE?new double[n][n]:null;
        int[][] permutations=new int[8][4];
        long[] counts=new long[6]; // quartets, primitives, J, K, integral worker time, accumulation wall time
        workers.forEachOrdered(batches(),batch->{
            var results=new ArrayList<Evaluated>(batch.size());
            for(var block:batch)results.add(evaluate(block));
            return results;
        },results->{
            for(var result:results) {
                var block=result.block();var left=shellPairs.get(block.left());var right=shellPairs.get(block.right());
                counts[0]+=result.values().length;counts[1]+=result.primitiveCount();counts[4]+=result.nanos();
                long t=System.nanoTime();int slot=0;
                for(int x=0;x<left.size();x++)for(int y=0;y<right.size();y++) {
                    if(block.left()==block.right()&&y>x)continue;
                    var a=left.get(x);var b=right.get(y);
                    if(a.index()<b.index()){var swap=a;a=b;b=swap;}
                    double value=result.values()[slot++];
                    int count=permutations(a.i(),a.j(),b.i(),b.j(),permutations);
                    for(int q=0;q<count;q++) {
                        int[] v=permutations[q];
                        if(v[0]<=v[1]){add(j,v[0],v[1],density.get(v[2],v[3])*value);counts[2]++;}
                        if(k!=null&&v[0]<=v[2]){add(k,v[0],v[2],density.get(v[1],v[3])*value);counts[3]++;}
                    }
                }
                counts[5]+=System.nanoTime()-t;
            }
        });
        quartets=counts[0];primitiveQuartets=counts[1];jCount=counts[2];kCount=counts[3];integralNanos=counts[4];accumulationNanos=counts[5];
        String protocol=PROTOCOL+";"+contraction;
        var jm=new ExecutionMatrix(ExecutionMatrix.Kind.COULOMB,basis,systemHash,density.densityHash(),protocol,source,(i,l)->j[i][l]);
        Optional<ExecutionMatrix> km=k==null?Optional.empty():Optional.of(new ExecutionMatrix(ExecutionMatrix.Kind.EXCHANGE,basis,systemHash,density.densityHash(),protocol,source,(i,l)->k[i][l]));
        String hash=ContentHash.sha256(source+"\n"+protocol+"\n"+density.densityHash()+"\n"+jm.receiptHash()+"\n"+km.map(ExecutionMatrix::receiptHash).orElse("NOT_COMPUTED")+"\nSCREENING_ONLY");
        return new Result(jm,km,hash,new Performance(n,quartets,primitiveQuartets,jCount,kCount,integralNanos,accumulationNanos,System.nanoTime()-start));
    }
    private Iterable<List<Block>> batches() {
        return ()->new Iterator<>() {
            int left,right;
            public boolean hasNext(){return left<shellPairs.size();}
            public List<Block> next(){
                if(!hasNext())throw new NoSuchElementException();
                var batch=new ArrayList<Block>(16);
                while(batch.size()<16&&hasNext()){
                    batch.add(new Block(left,right));if(++right>left){left++;right=0;}
                }
                return batch;
            }
        };
    }
    private Evaluated evaluate(Block block) {
        long start=System.nanoTime(),primitives=0;var local=sessions.get();
        var left=shellPairs.get(block.left());var right=shellPairs.get(block.right());
        int size=block.left()==block.right()?left.size()*(left.size()+1)/2:left.size()*right.size();
        double[] values=new double[size];int slot=0;local.beginBlock();
        try {
            for(int x=0;x<left.size();x++)for(int y=0;y<right.size();y++) {
                if(block.left()==block.right()&&y>x)continue;
                var a=left.get(x);var b=right.get(y);
                if(a.index()<b.index()){var swap=a;a=b;b=swap;}
                double value=local.get(a.i(),a.j(),b.i(),b.j());
                if(!Double.isFinite(value))throw new ArithmeticException("Nonfinite direct ERI");
                values[slot++]=value;
                primitives+=((long)basis.get(a.i()).terms().size())*basis.get(a.j()).terms().size()*basis.get(b.i()).terms().size()*basis.get(b.j()).terms().size();
            }
        } finally {local.endBlock();}
        return new Evaluated(block,values,primitives,System.nanoTime()-start);
    }
    @Override public synchronized void close(){workers.close();sessions.remove();}
    private static void add(double[][] matrix,int i,int j,double term) {
        double sum=matrix[i][j]+term;
        if(!Double.isFinite(term)||!Double.isFinite(sum))throw new ArithmeticException("Nonfinite direct contraction");
        matrix[i][j]=sum;
    }
    static int permutations(int i,int j,int k,int l,int[][] out) {
        int count=0;
        for(int exchange=0;exchange<2;exchange++)for(int first=0;first<2;first++)for(int second=0;second<2;second++) {
            int a=exchange==0?i:k,b=exchange==0?j:l,c=exchange==0?k:i,d=exchange==0?l:j;
            if(first==1){int t=a;a=b;b=t;}if(second==1){int t=c;c=d;d=t;}
            boolean duplicate=false;for(int p=0;p<count;p++)if(out[p][0]==a&&out[p][1]==b&&out[p][2]==c&&out[p][3]==d){duplicate=true;break;}
            if(!duplicate){out[count][0]=a;out[count][1]=b;out[count][2]=c;out[count][3]=d;count++;}
        }
        return count;
    }
    private static int pair(int i,int j){return Math.toIntExact((long)i*(i+1)/2+j);}
    private static String shellKey(ContractedGaussian f) {
        var l=f.angularMomentum();var result=new StringBuilder().append(l.x()+l.y()+l.z());
        for(var term:f.terms()) {
            var p=term.primitive();var c=p.centerBohr();
            result.append('|').append(Double.toHexString(c.x())).append(':').append(Double.toHexString(c.y())).append(':').append(Double.toHexString(c.z()))
                    .append(':').append(Double.toHexString(p.exponent())).append(':').append(Double.toHexString(term.coefficient()));
        }
        return result.toString();
    }
    public record Result(ExecutionMatrix coulomb,Optional<ExecutionMatrix> exchange,String receiptHash,Performance performance) {
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
    public record Performance(int dimension,long uniqueQuartets,long primitiveQuartets,long jAccumulations,long kAccumulations,
                              long integralNanos,long accumulationNanos,long totalNanos) {}
}
