package totah.lab.aether.matrix;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.*;

/** KS_PBE assembly around the unchanged ScfCycles engine and exact J-only contraction. */
public final class PbeScf {
    public static final String METHOD="KS_PBE";
    public record Options(Path cacheDirectory,GridDefinition grid,int blockSize,int workers) {
        public Options(Path cache){this(cache,new GridDefinition(120,590),256,8);}
        public Options {Objects.requireNonNull(cacheDirectory);Objects.requireNonNull(grid);if(blockSize<1||blockSize>65536||workers<1||workers>32)throw new IllegalArgumentException("Invalid execution options");}
        String protocol(){return "aether-KS_PBE-15-1;core-guess;energy=1e-12;density=1e-10;cap=128;F=H+J+Vxc;E=sum(PH)+.5*sum(PJ)+Exc;no-K;"
                +PbeFunctional.PROTOCOL+";"+grid.protocol()+";block="+blockSize+";workers="+workers+";"+PulayDiis.PROTOCOL+";"+OneShotNumerics.PROTOCOL+";"+CachedCoulomb.PROTOCOL;}
    }
    private PbeScf(){}
    public static Result solve(QuantumSystem system,List<ContractedGaussian> input,Options options,Consumer<String> progress)throws IOException {
        return solveInternal(system,input,options,progress,null);
    }
    /** Explicit ghost context: real nuclei define the grid and Hamiltonian; ghosts supply AOs only. */
    public static Result solve(GhostBasis ghost,Options options,Consumer<String> progress)throws IOException {
        Objects.requireNonNull(ghost);
        return solveInternal(ghost.system(),ghost.functions(),options,progress,ghost);
    }
    private static Result solveInternal(QuantumSystem system,List<ContractedGaussian> input,Options options,Consumer<String> progress,GhostBasis ghost)throws IOException {
        return solveInternal(system,input,options,progress,ghost,null);
    }
    /** Internal M17.1 gate; existing public entry points retain their exact path. */
    static Result solveWithCache(GhostBasis ghost,Options options,Consumer<String> progress,EriDiskCache canonical)throws IOException {
        return solveInternal(ghost.system(),ghost.functions(),options,progress,ghost,canonical);
    }
    private static Result solveInternal(QuantumSystem system,List<ContractedGaussian> input,Options options,Consumer<String> progress,GhostBasis ghost,EriDiskCache canonical)throws IOException {
        if(ghost==null)RhfScfCalculator.validateScope(system,input);else ghost.validate(system,input);
        String protocol=options.protocol()+(ghost==null?"":";ghost-basis-v1="+ghost.identity());
        var basis=List.copyOf(input);Objects.requireNonNull(progress);long start=System.nanoTime();
        boolean reuse=canonical!=null&&PermutedCacheContractions.bijection(canonical.basis(),basis).isPresent();
        var cache=reuse?new EriDiskCache.Creation(canonical,false,0,0,0,0,0):EriDiskCache.openOrCreate(options.cacheDirectory(),basis,options.workers());
        var overlap=OverlapMatrix.compute(basis);var core=new CoreHamiltonianCalculator(system,basis).calculate();
        var s=DiisRhfScf.matrix(basis.size(),overlap::get);double nuclear=NuclearRepulsion.calculate(system).hartree();
        var guess=OneShotNumerics.solve(s,DiisRhfScf.matrix(basis.size(),core::get));
        var initial=ConstructedDensity.build(system,overlap,ValidatedOrbitals.fromSolve(system,overlap,guess,core.receipt().receiptHash()),ConstructedDensity.Mode.CONSTRUCTION);
        var rows=new ArrayList<ExactScf.Iteration>();State[] last={null};ConstructedDensity.Result[] update={null};long[] times=new long[7];
        var gridMeasurements=new ArrayList<BlockedPbe.Performance>();RhfScfResult.Status status;
        try(var jOnly=reuse?null:new CachedCoulomb(system,cache.cache());
            var permuted=reuse?new PermutedCacheContractions(system,cache.cache(),basis,true):null;
            var grid=new BlockedPbe(system,basis,options.grid(),options.blockSize(),options.workers())) {
            try {
                status=ScfCycles.run(system,overlap,initial.density(),RhfScfCalculator.DEFAULT_MAX_ITERATIONS,p->{
                    CachedCoulomb.Result jr;try{jr=reuse?permuted.calculateCoulomb(p):jOnly.calculate(p);}catch(IOException e){throw new UncheckedIOException(e);}
                    times[0]+=jr.elapsedNanos();var xc=grid.evaluate(p);gridMeasurements.add(xc.performance());
                    var f=new ExecutionMatrix(ExecutionMatrix.Kind.FOCK,basis,p.systemHash(),p.densityHash(),protocol,
                            core.receipt().receiptHash()+"\n"+jr.coulomb().receiptHash()+"\n"+xc.receiptHash(),
                            (i,j)->MeanFieldArithmetic.ksFock(core.get(i,j),jr.coulomb().get(i,j),xc.potential().get(i,j)));
                    long t=System.nanoTime();var solved=OneShotNumerics.solve(s,DiisRhfScf.matrix(basis.size(),f::get));times[1]+=System.nanoTime()-t;
                    var orbitals=ValidatedOrbitals.fromSolve(system,overlap,solved,f.receiptHash());
                    var next=ConstructedDensity.build(system,overlap,orbitals,ConstructedDensity.Mode.CONSTRUCTION);update[0]=next;
                    double electronic=MeanFieldArithmetic.ksEnergy(p,core::get,jr.coulomb()::get,xc.energyHartree());double total=electronic+nuclear;
                    if(!Double.isFinite(total))throw new ArithmeticException("Nonfinite KS-PBE energy");
                    var state=new State(p,f,orbitals,xc,electronic,nuclear,total);
                    return new ScfCycles.Evaluation<>(total,next.density(),f::get,state);
                },cycle->{
                    var state=cycle.evaluation().evidence();last[0]=state;times[1]+=cycle.updateNanos();var step=cycle.diis();
                    Optional<KohnShamResult.DiisEvidence> diis=step==null?Optional.empty():Optional.of(new KohnShamResult.DiisEvidence(step.historyIterations(),step.fockHashes(),step.errorHashes(),step.coefficients(),step.events(),step.errorMaximum(),step.reciprocalCondition(),step.extrapolated(),PulayDiis.hash(step.fock())));
                    var proof=update[0];var energies=new ArrayList<Double>();for(int i=0;i<basis.size();i++)energies.add(state.orbitals().energy(i));
                    rows.add(new ExactScf.Iteration(cycle.number(),state.electronicHartree(),state.totalHartree(),cycle.deltaEnergy(),cycle.residual(),cycle.energyPassed(),cycle.densityPassed(),
                            cycle.input().densityHash(),cycle.output().densityHash(),state.fock().receiptHash(),state.orbitals().receiptHash(),proof.orbitalReceiptHash(),proof.occupation().receiptHash(),
                            proof.tracePS(),proof.idempotencyStatus(),proof.idempotencyResidual(),proof.receiptHash(),energies,diis));
                    progress.accept("KS_PBE iteration="+cycle.number()+" energy="+state.totalHartree()+" delta="+cycle.deltaEnergy()+" residual="+cycle.residual());
                },solved->{
                    var c=ValidatedOrbitals.fromSolve(system,overlap,solved,"DIIS_UPDATE\n"+PulayDiis.hash(solved.fock()));
                    update[0]=ConstructedDensity.build(system,overlap,c,ConstructedDensity.Mode.CONSTRUCTION);return update[0].density();
                });
            }catch(UncheckedIOException e){throw e.getCause();}
        }
        var hash=ContentHash.accumulator().line(protocol).line(IntegralMatrixData.systemHash(system)).line(cache.cache().identity()).line(initial.receiptHash()).line(status.name());
        for(var row:rows)hash.line(row.toString());hash.line("SCREENING_ONLY");
        return new Result(status,List.copyOf(rows),Optional.ofNullable(last[0]),cache,List.copyOf(gridMeasurements),times[0],times[1],System.nanoTime()-start,hash.finish());
    }
    public record State(DensityMatrix density,ExecutionMatrix fock,ValidatedOrbitals orbitals,BlockedPbe.Result xc,double electronicHartree,double nuclearHartree,double totalHartree){}
    public record Result(RhfScfResult.Status convergenceStatus,List<ExactScf.Iteration> iterations,Optional<State> state,EriDiskCache.Creation cache,
                         List<BlockedPbe.Performance> gridMeasurements,long jNanos,long eigensolveNanos,long totalNanos,String receiptHash) {
        public Result{iterations=List.copyOf(iterations);gridMeasurements=List.copyOf(gridMeasurements);}
        public Optional<State> convergedState(){return convergenceStatus==RhfScfResult.Status.CONVERGED?state:Optional.empty();}
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
}
