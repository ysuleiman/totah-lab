package totah.lab.aether.matrix;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Additive cache-backed RHF assembly; the frozen ScfCycles engine owns every transition. */
public final class SemiDirectScf {
    public static final String PROTOCOL="aether-SEMI_DIRECT_CACHE-SCF-14.1-1;RHF;core-guess;energy=1e-12;density=1e-10;cap=128;"
            +"construction-proof;"+PulayDiis.PROTOCOL+";"+OneShotNumerics.PROTOCOL+";"+RhfEnergyCalculator.PROTOCOL+";"+SemiDirectJk.PROTOCOL;
    public record Options(Path cacheDirectory,int generationWorkers) {
        public Options(Path directory){this(directory,8);}
        public Options {Objects.requireNonNull(cacheDirectory);if(generationWorkers<1||generationWorkers>32)throw new IllegalArgumentException("Invalid generation workers");}
    }
    private SemiDirectScf(){}
    public static Result solve(QuantumSystem system,List<ContractedGaussian> basis,Options options,Consumer<String> progress)throws IOException {
        RhfScfCalculator.validateScope(system,basis);return run(system,List.copyOf(basis),options,"REAL_CENTERS",progress);
    }
    /** Explicitly proves the same complete ghost basis before adopting the caller's AB AO order. */
    public static Result solve(GhostBasis ghosts,List<ContractedGaussian> ordered,Options options,Consumer<String> progress)throws IOException {
        ghosts.validate(ghosts.system(),ghosts.functions());requireSameFunctions(ghosts.functions(),ordered);
        return run(ghosts.system(),List.copyOf(ordered),options,ghosts.identity()+";explicit-full-basis-reordering",progress);
    }
    static void requireSameFunctions(List<ContractedGaussian> original,List<ContractedGaussian> ordered) {
        var counts=new HashMap<String,Integer>();
        for(var f:original)counts.merge(EriDiskCache.identity(List.of(f)),1,Integer::sum);
        for(var f:ordered)counts.merge(EriDiskCache.identity(List.of(f)),-1,Integer::sum);
        if(counts.values().stream().anyMatch(n->n!=0))throw new IllegalArgumentException("Not the identical full ghost basis");
    }
    private static Result run(QuantumSystem system,List<ContractedGaussian> basis,Options options,String ghostIdentity,Consumer<String> progress)throws IOException {
        return run(system,basis,options,ghostIdentity,progress,null);
    }
    /** Internal M17.1 gate; ambiguity retains the existing fresh-cache path. */
    static Result solveWithCache(GhostBasis ghosts,List<ContractedGaussian> ordered,Options options,Consumer<String> progress,EriDiskCache canonical)throws IOException {
        ghosts.validate(ghosts.system(),ghosts.functions());requireSameFunctions(ghosts.functions(),ordered);
        return run(ghosts.system(),List.copyOf(ordered),options,ghosts.identity()+";explicit-full-basis-reordering",progress,canonical);
    }
    private static Result run(QuantumSystem system,List<ContractedGaussian> basis,Options options,String ghostIdentity,Consumer<String> progress,EriDiskCache canonical)throws IOException {
        Objects.requireNonNull(progress);long start=System.nanoTime();progress.accept("CACHE_OPEN ao="+basis.size());
        boolean reuse=canonical!=null&&PermutedCacheContractions.bijection(canonical.basis(),basis).isPresent();
        var creation=reuse?new EriDiskCache.Creation(canonical,false,0,0,0,0,0):EriDiskCache.openOrCreate(options.cacheDirectory(),basis,options.generationWorkers());
        progress.accept("CACHE_READY identity="+creation.cache().identity()+" generated="+creation.generated()+" seconds="+creation.totalNanos()/1e9);
        var overlap=OverlapMatrix.compute(basis);var core=new CoreHamiltonianCalculator(system,basis).calculate();
        var s=DiisRhfScf.matrix(basis.size(),overlap::get);double nuclear=NuclearRepulsion.calculate(system).hartree();
        var guess=OneShotNumerics.solve(s,DiisRhfScf.matrix(basis.size(),core::get));
        var initial=ConstructedDensity.build(system,overlap,ValidatedOrbitals.fromSolve(system,overlap,guess,core.receipt().receiptHash()),ConstructedDensity.Mode.CONSTRUCTION);
        var rows=new ArrayList<ExactScf.Iteration>();ExactScf.State[] last={null};ConstructedDensity.Result[] update={null};
        var scans=new ArrayList<EriDiskCache.Scan>();long[] jkTime={0};RhfScfResult.Status status;
        try(var jk=reuse?null:new SemiDirectJk(system,creation.cache());
            var permuted=reuse?new PermutedCacheContractions(system,creation.cache(),basis,false):null) {
            try {
                status=ScfCycles.run(system,overlap,initial.density(),RhfScfCalculator.DEFAULT_MAX_ITERATIONS,p->{
                    SemiDirectJk.Result matrices;
                    try{matrices=reuse?permuted.calculateJk(p):jk.calculate(p);}catch(IOException ex){throw new UncheckedIOException(ex);}
                    jkTime[0]+=matrices.elapsedNanos();scans.add(matrices.scan());
                    var f=new ExecutionMatrix(ExecutionMatrix.Kind.FOCK,basis,p.systemHash(),p.densityHash(),PROTOCOL,
                            core.receipt().receiptHash()+"\n"+matrices.receiptHash(),
                            (i,j)->MeanFieldArithmetic.rhfFock(core.get(i,j),matrices.coulomb().get(i,j),matrices.exchange().get(i,j)));
                    var solved=OneShotNumerics.solve(s,DiisRhfScf.matrix(basis.size(),f::get));
                    var c=ValidatedOrbitals.fromSolve(system,overlap,solved,f.receiptHash());
                    var next=ConstructedDensity.build(system,overlap,c,ConstructedDensity.Mode.CONSTRUCTION);update[0]=next;
                    double electronic=MeanFieldArithmetic.rhfEnergy(p,core::get,f::get);double total=electronic+nuclear;
                    var state=new ExactScf.State(p,f,c,electronic,nuclear,total,Optional.empty());
                    return new ScfCycles.Evaluation<>(total,next.density(),f::get,state);
                },cycle->{
                    var state=cycle.evaluation().evidence();last[0]=state;var step=cycle.diis();
                    Optional<KohnShamResult.DiisEvidence> diis=step==null?Optional.empty():Optional.of(new KohnShamResult.DiisEvidence(
                            step.historyIterations(),step.fockHashes(),step.errorHashes(),step.coefficients(),step.events(),step.errorMaximum(),step.reciprocalCondition(),step.extrapolated(),PulayDiis.hash(step.fock())));
                    var proof=update[0];var energies=new ArrayList<Double>();for(int i=0;i<basis.size();i++)energies.add(state.orbitals().energy(i));
                    rows.add(new ExactScf.Iteration(cycle.number(),state.electronicHartree(),state.totalHartree(),cycle.deltaEnergy(),cycle.residual(),cycle.energyPassed(),cycle.densityPassed(),
                            cycle.input().densityHash(),cycle.output().densityHash(),state.fock().receiptHash(),state.orbitals().receiptHash(),proof.orbitalReceiptHash(),proof.occupation().receiptHash(),
                            proof.tracePS(),proof.idempotencyStatus(),proof.idempotencyResidual(),proof.receiptHash(),energies,diis));
                    progress.accept("SCF iteration="+cycle.number()+" energy="+state.totalHartree()+" delta="+cycle.deltaEnergy()+" residual="+cycle.residual());
                },solved->{
                    var c=ValidatedOrbitals.fromSolve(system,overlap,solved,"DIIS_UPDATE\n"+PulayDiis.hash(solved.fock()));
                    update[0]=ConstructedDensity.build(system,overlap,c,ConstructedDensity.Mode.CONSTRUCTION);return update[0].density();
                });
            }catch(UncheckedIOException ex){throw ex.getCause();}
        }
        String reason=status==RhfScfResult.Status.CONVERGED?"Both physical-state criteria passed":"Iteration cap reached without both criteria";
        var hash=ContentHash.accumulator().line(PROTOCOL).line(IntegralMatrixData.systemHash(system)).line(ghostIdentity)
                .line(creation.cache().identity()).line(initial.receiptHash()).line(status.name()).line(reason);
        for(var row:rows)hash.line(row.toString());hash.line("SCREENING_ONLY");
        return new Result(status,reason,List.copyOf(rows),Optional.ofNullable(last[0]),creation,List.copyOf(scans),jkTime[0],hash.finish(),System.nanoTime()-start);
    }
    public record Result(RhfScfResult.Status convergenceStatus,String reason,List<ExactScf.Iteration> iterations,
                         Optional<ExactScf.State> state,EriDiskCache.Creation cache,List<EriDiskCache.Scan> scans,
                         long jkNanos,String receiptHash,long totalNanos) {
        public Result{iterations=List.copyOf(iterations);scans=List.copyOf(scans);}
        public Optional<ExactScf.State> convergedState(){return convergenceStatus==RhfScfResult.Status.CONVERGED?state:Optional.empty();}
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
}
