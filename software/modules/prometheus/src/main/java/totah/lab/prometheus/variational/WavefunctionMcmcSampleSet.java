package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.GeneralMolecularCoulombHamiltonian;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;

/**
 * Bounded, replayable electron-by-electron Markov-chain samples from |Psi|^2.
 * Transition adaptation is confined to warmup; retained configurations all have
 * unit statistical weight.
 */
public final class WavefunctionMcmcSampleSet implements DirectWavefunctionSampleSource {
    public enum Kernel { RANDOM_WALK_METROPOLIS, METROPOLIS_ADJUSTED_LANGEVIN }

    public record Configuration(Kernel kernel,int walkers,int warmupSweeps,int retainedPerWalker,
            int sweepsBetweenRetained,double initialStepSizeBohr,double targetAcceptance,
            int adaptationInterval,long seed) {
        public Configuration {
            Objects.requireNonNull(kernel,"kernel");
            if(walkers<2||warmupSweeps<0||retainedPerWalker<1||sweepsBetweenRetained<1)
                throw new IllegalArgumentException("invalid bounded MCMC counts");
            if(!(initialStepSizeBohr>0)||!(targetAcceptance>0&&targetAcceptance<1)||adaptationInterval<1)
                throw new IllegalArgumentException("invalid MCMC scale/adaptation");
        }
        public int retainedSamples(){return Math.multiplyExact(walkers,retainedPerWalker);}
    }

    public record Diagnostics(Kernel kernel,int walkers,long proposedMoves,long acceptedMoves,
            double warmupAcceptance,double measurementAcceptance,double frozenStepSizeBohr,
            long stateEvaluations,long elapsedNanos,long peakHeapBytes,String replayHash) {
        public Diagnostics {
            Objects.requireNonNull(kernel);Objects.requireNonNull(replayHash);
            if(!replayHash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid replay hash");
        }
    }

    public record StatisticalDiagnostics(int samples,double energyMeanHartree,double energyVarianceHartree2,
            double integratedAutocorrelationTime,double autocorrelationAdjustedEss,double normalizedEss,
            double betweenWalkerRhat,double maximumRetainedStickingFraction,double q01Hartree,
            double medianHartree,double q99Hartree,double topOnePercentVarianceFraction,
            double topFivePercentVarianceFraction,String localEnergyReplayHash) {}

    private final List<QuantumCoordinates> samples;
    private final List<Integer> walkerIds;
    private final Diagnostics diagnostics;

    private WavefunctionMcmcSampleSet(List<QuantumCoordinates> samples,List<Integer> walkerIds,Diagnostics diagnostics){
        this.samples=List.copyOf(samples);this.walkerIds=List.copyOf(walkerIds);this.diagnostics=diagnostics;
    }

    public static WavefunctionMcmcSampleSet generate(GeneralSlaterJastrowState state,Configuration config){
        Objects.requireNonNull(state);Objects.requireNonNull(config);
        long started=System.nanoTime(),peak=usedHeap(),evaluations=0,proposed=0,accepted=0,warmProposed=0,warmAccepted=0;
        Random random=new Random(config.seed());
        List<Walker> walkers=new ArrayList<>();
        for(int w=0;w<config.walkers();w++){
            QuantumCoordinates coordinates=initialCoordinates(state,w,random);
            LogState log=evaluate(state,coordinates);evaluations++;
            walkers.add(new Walker(coordinates,log));
        }
        double step=config.initialStepSizeBohr();long windowProposed=0,windowAccepted=0;
        for(int sweep=1;sweep<=config.warmupSweeps();sweep++){
            for(Walker walker:walkers)for(int electron=0;electron<state.molecule().electrons().value();electron++){
                Transition t=transition(state,walker,electron,step,config.kernel(),random);evaluations+=t.evaluations;proposed++;warmProposed++;windowProposed++;
                if(t.accepted){walker.coordinates=t.coordinates;walker.log=t.log;accepted++;warmAccepted++;windowAccepted++;}
            }
            if(sweep%config.adaptationInterval()==0&&windowProposed>0){
                double rate=(double)windowAccepted/windowProposed;
                step*=Math.exp(.35*(rate-config.targetAcceptance()));
                step=Math.max(1e-4,Math.min(4.0,step));windowProposed=0;windowAccepted=0;
            }
            peak=Math.max(peak,usedHeap());
        }
        long measurementProposed=0,measurementAccepted=0;
        List<QuantumCoordinates> retained=new ArrayList<>(config.retainedSamples());
        List<Integer> ids=new ArrayList<>(config.retainedSamples());
        StringBuilder replay=new StringBuilder();
        for(int n=0;n<config.retainedPerWalker();n++){
            for(int skip=0;skip<config.sweepsBetweenRetained();skip++)for(Walker walker:walkers)for(int electron=0;electron<state.molecule().electrons().value();electron++){
                Transition t=transition(state,walker,electron,step,config.kernel(),random);evaluations+=t.evaluations;proposed++;measurementProposed++;
                if(t.accepted){walker.coordinates=t.coordinates;walker.log=t.log;accepted++;measurementAccepted++;}
            }
            for(int w=0;w<walkers.size();w++){
                QuantumCoordinates c=walkers.get(w).coordinates;retained.add(c);ids.add(w);append(replay,w,c);
            }
            peak=Math.max(peak,usedHeap());
        }
        Diagnostics d=new Diagnostics(config.kernel(),config.walkers(),proposed,accepted,
                warmProposed==0?Double.NaN:(double)warmAccepted/warmProposed,
                measurementProposed==0?Double.NaN:(double)measurementAccepted/measurementProposed,
                step,evaluations,System.nanoTime()-started,peak,CanonicalHashing.sha256Hex(replay.toString()));
        return new WavefunctionMcmcSampleSet(retained,ids,d);
    }

    public Diagnostics diagnostics(){return diagnostics;}
    public StatisticalDiagnostics statisticalDiagnostics(GeneralSlaterJastrowState state,GeneralMolecularCoulombHamiltonian hamiltonian){
        if(!state.molecule().scientificIdentity().equals(hamiltonian.molecule().scientificIdentity()))throw new IllegalArgumentException("state/Hamiltonian mismatch");
        List<List<Double>> chains=new ArrayList<>();for(int w=0;w<diagnostics.walkers();w++)chains.add(new ArrayList<>());
        List<Double> all=new ArrayList<>(samples.size());StringBuilder replay=new StringBuilder();
        for(int i=0;i<samples.size();i++){double e=state.evaluateWithLocalEnergy(samples.get(i),hamiltonian).localEnergy().orElseThrow().totalHartree();chains.get(walkerIds.get(i)).add(e);all.add(e);replay.append(Long.toHexString(Double.doubleToRawLongBits(e))).append('\n');}
        double mean=all.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),variance=all.stream().mapToDouble(x->square(x-mean)).sum()/(all.size()-1);
        double tau=chains.stream().mapToDouble(WavefunctionMcmcSampleSet::integratedAutocorrelation).average().orElse(1);double ess=all.size()/tau;
        double within=chains.stream().mapToDouble(WavefunctionMcmcSampleSet::sampleVariance).average().orElse(0),chainMean=chains.stream().mapToDouble(x->x.stream().mapToDouble(Double::doubleValue).average().orElseThrow()).average().orElseThrow();
        int n=chains.getFirst().size();double between=n*chains.stream().mapToDouble(x->square(x.stream().mapToDouble(Double::doubleValue).average().orElseThrow()-chainMean)).sum()/(chains.size()-1);double varHat=(n-1.0)/n*within+between/n,rhat=within==0?1:Math.sqrt(varHat/within);
        List<Double> sorted=new ArrayList<>(all);sorted.sort(Double::compare);List<Double> contributions=new ArrayList<>();for(double e:all)contributions.add(square(e-mean));contributions.sort(java.util.Comparator.reverseOrder());double denominator=contributions.stream().mapToDouble(Double::doubleValue).sum();
        double maxSticking=0;for(int w=0;w<diagnostics.walkers();w++){int same=0,total=0,last=-1;for(int i=0;i<samples.size();i++)if(walkerIds.get(i)==w){if(last>=0){total++;if(samples.get(last).equals(samples.get(i)))same++;}last=i;}if(total>0)maxSticking=Math.max(maxSticking,(double)same/total);}
        return new StatisticalDiagnostics(all.size(),mean,variance,tau,ess,ess/all.size(),rhat,maxSticking,quantile(sorted,.01),quantile(sorted,.5),quantile(sorted,.99),fraction(contributions,Math.max(1,(int)Math.ceil(all.size()*.01)),denominator),fraction(contributions,Math.max(1,(int)Math.ceil(all.size()*.05)),denominator),CanonicalHashing.sha256Hex(replay.toString()));
    }
    public int size(){return samples.size();}
    public List<Integer> walkerIds(){return walkerIds;}
    @Override public void forEach(SampleConsumer consumer){Objects.requireNonNull(consumer);for(QuantumCoordinates c:samples)consumer.accept(1.0,c);}

    private static Transition transition(GeneralSlaterJastrowState state,Walker walker,int electron,double step,Kernel kernel,Random random){
        var old=walker.coordinates.particles().get(electron);double sigma=kernel==Kernel.RANDOM_WALK_METROPOLIS?step:Math.sqrt(step);
        double driftX=kernel==Kernel.METROPOLIS_ADJUSTED_LANGEVIN?step*walker.log.logPsiGradient[electron][0]:0;
        double driftY=kernel==Kernel.METROPOLIS_ADJUSTED_LANGEVIN?step*walker.log.logPsiGradient[electron][1]:0;
        double driftZ=kernel==Kernel.METROPOLIS_ADJUSTED_LANGEVIN?step*walker.log.logPsiGradient[electron][2]:0;
        var moved=new QuantumCoordinates.ParticleCoordinate(electron,old.xBohr()+driftX+sigma*random.nextGaussian(),old.yBohr()+driftY+sigma*random.nextGaussian(),old.zBohr()+driftZ+sigma*random.nextGaussian(),old.spin());
        List<QuantumCoordinates.ParticleCoordinate> particles=new ArrayList<>(walker.coordinates.particles());particles.set(electron,moved);QuantumCoordinates candidate=new QuantumCoordinates(particles);
        LogState next;try{next=evaluate(state,candidate);}catch(IllegalArgumentException ex){return new Transition(false,walker.coordinates,walker.log,1);}
        double logAcceptance=2*(next.logAbsPsi-walker.log.logAbsPsi);
        if(kernel==Kernel.METROPOLIS_ADJUSTED_LANGEVIN){
            double forward=square(moved.xBohr()-old.xBohr()-driftX)+square(moved.yBohr()-old.yBohr()-driftY)+square(moved.zBohr()-old.zBohr()-driftZ);
            double reverseDriftX=step*next.logPsiGradient[electron][0],reverseDriftY=step*next.logPsiGradient[electron][1],reverseDriftZ=step*next.logPsiGradient[electron][2];
            double reverse=square(old.xBohr()-moved.xBohr()-reverseDriftX)+square(old.yBohr()-moved.yBohr()-reverseDriftY)+square(old.zBohr()-moved.zBohr()-reverseDriftZ);
            logAcceptance+=(forward-reverse)/(2*step);
        }
        boolean accept=Math.log(random.nextDouble())<Math.min(0,logAcceptance);
        return new Transition(accept,accept?candidate:walker.coordinates,accept?next:walker.log,1);
    }

    private static LogState evaluate(GeneralSlaterJastrowState state,QuantumCoordinates c){
        var e=state.evaluate(c);double psi=e.derivatives().value().real();double[][] gradient=new double[c.particles().size()][3];
        for(int i=0;i<gradient.length;i++){var g=e.derivatives().coordinateGradient().particleGradients().get(i);gradient[i][0]=g.x().real()/psi;gradient[i][1]=g.y().real()/psi;gradient[i][2]=g.z().real()/psi;}
        return new LogState(e.logAbsoluteWavefunction(),gradient);
    }

    private static QuantumCoordinates initialCoordinates(GeneralSlaterJastrowState state,int walker,Random random){
        List<QuantumCoordinates.ParticleCoordinate> particles=new ArrayList<>();var nuclei=state.molecule().nuclei();double scale=1.0/Math.sqrt(nuclei.stream().mapToInt(n->n.charge().atomicNumber()).max().orElse(1));
        for(int i=0;i<state.molecule().electrons().value();i++){CartesianPosition p=nuclei.get((i+walker)%nuclei.size()).position().inBohr();SpinProjection spin=i<state.molecule().spin().alphaElectrons()?SpinProjection.ALPHA:SpinProjection.BETA;particles.add(new QuantumCoordinates.ParticleCoordinate(i,p.x()+scale*random.nextGaussian(),p.y()+scale*random.nextGaussian(),p.z()+scale*random.nextGaussian(),spin));}
        return new QuantumCoordinates(particles);
    }
    private static void append(StringBuilder b,int walker,QuantumCoordinates c){b.append(walker).append(':');for(var p:c.particles())b.append(Long.toHexString(Double.doubleToRawLongBits(p.xBohr()))).append(',').append(Long.toHexString(Double.doubleToRawLongBits(p.yBohr()))).append(',').append(Long.toHexString(Double.doubleToRawLongBits(p.zBohr()))).append(';');b.append('\n');}
    private static long usedHeap(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    private static double square(double x){return x*x;}
    private static double sampleVariance(List<Double> values){if(values.size()<2)return 0;double mean=values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();return values.stream().mapToDouble(x->square(x-mean)).sum()/(values.size()-1);}
    private static double integratedAutocorrelation(List<Double> values){int n=values.size();if(n<4)return 1;double mean=values.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),variance=values.stream().mapToDouble(x->square(x-mean)).sum()/n;if(!(variance>0))return 1;double tau=1;for(int lag=1;lag<=Math.min(n/2,100);lag++){double covariance=0;for(int i=0;i<n-lag;i++)covariance+=(values.get(i)-mean)*(values.get(i+lag)-mean);double rho=covariance/((n-lag)*variance);if(rho<=0)break;tau+=2*rho;}return Math.max(1,tau);}
    private static double quantile(List<Double> sorted,double p){double x=p*(sorted.size()-1);int lo=(int)Math.floor(x),hi=(int)Math.ceil(x);return sorted.get(lo)+(x-lo)*(sorted.get(hi)-sorted.get(lo));}
    private static double fraction(List<Double> descending,int count,double denominator){if(denominator==0)return 0;double sum=0;for(int i=0;i<count;i++)sum+=descending.get(i);return sum/denominator;}
    private static final class Walker{private QuantumCoordinates coordinates;private LogState log;private Walker(QuantumCoordinates c,LogState l){coordinates=c;log=l;}}
    private record LogState(double logAbsPsi,double[][] logPsiGradient){}
    private record Transition(boolean accepted,QuantumCoordinates coordinates,LogState log,long evaluations){}
}
