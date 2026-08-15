package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.molecular.Molecule;

/** Deterministic, bounded, nuclear-charge-weighted multicentre importance samples. */
public final class GeneralMolecularImportanceBatches implements GeneralMolecularSampleSource {
    private static final int[] PRIMES=firstPrimes(128);
    private final Molecule molecule;
    private final int count,skip,batchSize;
    private final double exponent;
    private final String provenanceHash;

    public GeneralMolecularImportanceBatches(Molecule molecule,int count,double exponent,int skip,int batchSize) {
        this.molecule=Objects.requireNonNull(molecule);
        if(count<16||!(exponent>0)||skip<1||batchSize<1||batchSize>512)
            throw new IllegalArgumentException("invalid general molecular sampling request");
        if(6*molecule.electrons().value()>PRIMES.length)
            throw new IllegalArgumentException("electron count exceeds deterministic Halton dimensions");
        this.count=count;this.exponent=exponent;this.skip=skip;this.batchSize=batchSize;
        this.provenanceHash=CanonicalHashing.sha256Hex("general-multicentre-halton-v1|"
                +molecule.scientificIdentity()+"|"+count+"|"+exponent+"|"+skip+"|"+batchSize);
    }

    public String provenanceHash(){return provenanceHash;}
    public int count(){return count;}
    public int batchSize(){return batchSize;}

    @Override public void forEach(SampleConsumer consumer) {
        Objects.requireNonNull(consumer);
        forEachBatch(batch->batch.forEach(point->consumer.accept(point.weight(),point.coordinates())));
    }

    public void forEachBatch(Consumer<List<CollocationPointSet.WeightedPoint>> consumer) {
        for(int start=0;start<count;start+=batchSize) {
            int end=Math.min(count,start+batchSize);
            List<CollocationPointSet.WeightedPoint> batch=new ArrayList<>(end-start);
            for(int sample=start;sample<end;sample++)batch.add(point(sample));
            consumer.accept(List.copyOf(batch));
        }
    }

    private CollocationPointSet.WeightedPoint point(int sample) {
        List<QuantumCoordinates.ParticleCoordinate> particles=new ArrayList<>();
        double logDensity=0;int alpha=molecule.spin().alphaElectrons();
        for(int electron=0;electron<molecule.electrons().value();electron++) {
            int base=6*electron,index=sample+skip;
            double[] q=new double[6];for(int i=0;i<6;i++)q[i]=halton(index,PRIMES[base+i]);
            var center=selectCenter(q[5]);
            double radius=-Math.log(q[0]*q[1]*q[2])/(2*exponent);
            double cosine=2*q[3]-1,sine=Math.sqrt(1-cosine*cosine),phi=2*Math.PI*q[4];
            var origin=center.position().inBohr();double x=origin.x()+radius*sine*Math.cos(phi),y=origin.y()+radius*sine*Math.sin(phi),z=origin.z()+radius*cosine;
            logDensity+=Math.log(mixtureDensity(x,y,z));
            particles.add(new QuantumCoordinates.ParticleCoordinate(electron,x,y,z,electron<alpha?SpinProjection.ALPHA:SpinProjection.BETA));
        }
        double weight=Math.exp(-logDensity)/count;
        if(!Double.isFinite(weight)||!(weight>0))throw new IllegalStateException("non-finite importance weight");
        return new CollocationPointSet.WeightedPoint(new QuantumCoordinates(particles),weight);
    }

    private totah.lab.prometheus.molecular.NuclearCenter selectCenter(double u) {
        int total=molecule.nuclei().stream().mapToInt(n->n.charge().atomicNumber()).sum();double target=u*total,cumulative=0;
        for(var nucleus:molecule.nuclei()){cumulative+=nucleus.charge().atomicNumber();if(target<cumulative)return nucleus;}
        return molecule.nuclei().getLast();
    }

    private double mixtureDensity(double x,double y,double z) {
        int total=molecule.nuclei().stream().mapToInt(n->n.charge().atomicNumber()).sum();double density=0;
        for(var nucleus:molecule.nuclei()) {
            var p=nucleus.position().inBohr();double dx=x-p.x(),dy=y-p.y(),dz=z-p.z(),r=Math.sqrt(dx*dx+dy*dy+dz*dz);
            double normalized=exponent*exponent*exponent/Math.PI*Math.exp(-2*exponent*r);
            density+=(double)nucleus.charge().atomicNumber()/total*normalized;
        }
        return density;
    }

    private static double halton(int index,int base){double result=0,fraction=1.0/base;for(int n=index;n>0;n/=base){result+=fraction*(n%base);fraction/=base;}return result;}
    private static int[] firstPrimes(int count){int[] result=new int[count];int found=0,candidate=2;while(found<count){boolean prime=true;for(int d=2;d*d<=candidate;d++)if(candidate%d==0){prime=false;break;}if(prime)result[found++]=candidate;candidate++;}return result;}
}
