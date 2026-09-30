package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.*;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Versioned M14 execution of the frozen RHF/LDA equations through the shared SCF transition engine. */
public final class ExactScf {
    public enum Integrals { PACKED_REFERENCE, DIRECT_EXACT }
    public enum Method { RHF, LDA_EXCHANGE, LDA_PZ81 }
    public record Options(Method method,Integrals integrals,int gridBlockSize,ConstructedDensity.Mode densityMode,int workers) {
        public Options(Method method,Integrals integrals,int gridBlockSize,ConstructedDensity.Mode densityMode){this(method,integrals,gridBlockSize,densityMode,1);}
        public Options {
            Objects.requireNonNull(method);Objects.requireNonNull(integrals);Objects.requireNonNull(densityMode);
            if(workers<1||workers>32)throw new IllegalArgumentException("Invalid worker count");
            if(gridBlockSize<1||gridBlockSize>65536)throw new IllegalArgumentException("Invalid grid block size");
        }
        String protocol(){return "aether-exact-SCF-14-2;"+method+";"+integrals+";gridBlock="+gridBlockSize+";density="+densityMode
                +";workers="+workers+";core-guess;energy=1e-12;density=1e-10;maxIterations=128;ScfCycles-v1;"+PulayDiis.PROTOCOL
                +";"+OneShotNumerics.PROTOCOL+";"+DirectExactJk.PROTOCOL
                +";physical-equations="+(method==Method.RHF?RhfEnergyCalculator.PROTOCOL+";F=(Hcore+J)-0.5*K":
                    "F=Hcore+J+Vxc;Eelec=sum(P*Hcore)+0.5*sum(P*J)+Exc;no-HF-exchange;"+new GridDefinition(120,590).protocol()+";"
                            +(method==Method.LDA_EXCHANGE?LdaFunctional.EXCHANGE:LdaFunctional.EXCHANGE_PZ81).protocol())+";SCREENING_ONLY";}
    }
    private ExactScf() {}
    public static Result solve(QuantumSystem system,List<ContractedGaussian> basis,Options options)throws IOException {
        return solve(system,basis,options,null);
    }
    public static Result solve(GhostBasis ghosts,Options options)throws IOException {
        if(options.method()!=Method.RHF)throw new IllegalArgumentException("M14 ghost validation is RHF only");
        return solve(ghosts.system(),ghosts.functions(),options,ghosts);
    }
    private static Result solve(QuantumSystem system,List<ContractedGaussian> input,Options options,GhostBasis ghosts)throws IOException {
        long started=System.nanoTime();Objects.requireNonNull(options);var basis=List.copyOf(input);
        var rows=new ArrayList<Iteration>();long[] times=new long[7]; // integral setup, JK, XC, solve, density validation/construction
        State[] last={null};ConstructedDensity.Result[] update={null};String initial="UNAVAILABLE";
        String provenance=IntegralMatrixData.systemHash(system)+"\n"+(basis.isEmpty()?ContentHash.sha256("empty-basis"):IntegralMatrixData.basisGeometryHash(basis))+"\n"+(ghosts==null?"REAL_CENTERS":ghosts.identity());
        RhfScfResult.Status status;String reason;
        try {if(ghosts==null)RhfScfCalculator.validateScope(system,basis);else ghosts.validate(system,basis);}
        catch(IllegalArgumentException e){return finish(options,provenance,rows,null,initial,RhfScfResult.Status.UNSUPPORTED_SYSTEM,e.getMessage(),times,started);}
        try {
            long timer=System.nanoTime();var overlap=OverlapMatrix.compute(basis);var core=new CoreHamiltonianCalculator(system,basis).calculate();
            var packed=options.integrals()==Integrals.PACKED_REFERENCE?new ElectronRepulsionCalculator(system,basis).calculate():null;
            var direct=options.integrals()==Integrals.DIRECT_EXACT?new DirectExactJk(system,basis,options.workers()):null;
            times[0]+=System.nanoTime()-timer;timer=System.nanoTime();
            try(direct;var grid=options.method()==Method.RHF?null:new BlockedXc(system,basis,new GridDefinition(120,590),options.gridBlockSize(),options.workers())) {
            times[2]+=System.nanoTime()-timer;
            var functional=options.method()==Method.LDA_EXCHANGE?LdaFunctional.EXCHANGE:LdaFunctional.EXCHANGE_PZ81;
            var s=DiisRhfScf.matrix(basis.size(),overlap::get);double nuclear=NuclearRepulsion.calculate(system).hartree();
            timer=System.nanoTime();var guess=OneShotNumerics.solve(s,DiisRhfScf.matrix(basis.size(),core::get));times[3]+=System.nanoTime()-timer;
            var guessOrbitals=ValidatedOrbitals.fromSolve(system,overlap,guess,core.receipt().receiptHash());
            var initialDensity=ConstructedDensity.build(system,overlap,guessOrbitals,options.densityMode());times[4]+=initialDensity.elapsedNanos();initial=initialDensity.receiptHash();
            status=ScfCycles.run(system,overlap,initialDensity.density(),RhfScfCalculator.DEFAULT_MAX_ITERATIONS,p->{
                long t=System.nanoTime();ExecutionMatrix j,k=null;String contractionHash;
                if(direct!=null) {
                    var jk=direct.calculate(p,options.method()==Method.RHF?DirectExactJk.Contraction.COULOMB_AND_EXCHANGE:DirectExactJk.Contraction.COULOMB_ONLY);
                    j=jk.coulomb();k=jk.exchange().orElse(null);contractionHash=jk.receiptHash();
                    times[5]+=jk.performance().integralNanos();times[6]+=jk.performance().accumulationNanos();
                } else if(options.method()==Method.RHF) {
                    var jk=JkCalculator.calculate(p,packed);contractionHash=jk.receipt().receiptHash();
                    j=new ExecutionMatrix(ExecutionMatrix.Kind.COULOMB,basis,p.systemHash(),p.densityHash(),options.protocol(),contractionHash,jk.coulomb()::get);
                    k=new ExecutionMatrix(ExecutionMatrix.Kind.EXCHANGE,basis,p.systemHash(),p.densityHash(),options.protocol(),contractionHash,jk.exchange()::get);
                } else {var only=PackedCoulomb.calculate(p,packed);j=only.coulomb();contractionHash=j.receiptHash();}
                times[1]+=System.nanoTime()-t;t=System.nanoTime();
                var xc=grid==null?null:grid.evaluate(p,functional);times[2]+=System.nanoTime()-t;
                var exchange=k;
                String sources=core.receipt().receiptHash()+"\n"+contractionHash+"\n"+(xc==null?"NO_XC":xc.receiptHash());
                var f=new ExecutionMatrix(ExecutionMatrix.Kind.FOCK,basis,p.systemHash(),p.densityHash(),options.protocol(),sources,
                        (a,b)->exchange==null?MeanFieldArithmetic.ksFock(core.get(a,b),j.get(a,b),xc.potential().get(a,b))
                                :MeanFieldArithmetic.rhfFock(core.get(a,b),j.get(a,b),exchange.get(a,b)));
                t=System.nanoTime();var solved=OneShotNumerics.solve(s,DiisRhfScf.matrix(p.size(),f::get));times[3]+=System.nanoTime()-t;
                var orbitals=ValidatedOrbitals.fromSolve(system,overlap,solved,f.receiptHash());
                var next=ConstructedDensity.build(system,overlap,orbitals,options.densityMode());times[4]+=next.elapsedNanos();update[0]=next;
                double electronic=exchange==null?MeanFieldArithmetic.ksEnergy(p,core::get,j::get,xc.energyHartree()):MeanFieldArithmetic.rhfEnergy(p,core::get,f::get);
                double total=electronic+nuclear;if(!Double.isFinite(total))throw new ArithmeticException("Nonfinite mean-field energy");
                var state=new State(p,f,orbitals,electronic,nuclear,total,Optional.ofNullable(xc));
                return new ScfCycles.Evaluation<>(total,next.density(),f::get,state);
            },cycle->{
                var state=cycle.evaluation().evidence();last[0]=state;times[3]+=cycle.updateNanos();
                var step=cycle.diis();Optional<KohnShamResult.DiisEvidence> diis=Optional.empty();
                if(step!=null)diis=Optional.of(new KohnShamResult.DiisEvidence(step.historyIterations(),step.fockHashes(),step.errorHashes(),step.coefficients(),step.events(),step.errorMaximum(),step.reciprocalCondition(),step.extrapolated(),PulayDiis.hash(step.fock())));
                var proof=update[0];
                var energies=new ArrayList<Double>();for(int i=0;i<state.orbitals().size();i++)energies.add(state.orbitals().energy(i));
                rows.add(new Iteration(cycle.number(),state.electronicHartree(),state.totalHartree(),cycle.deltaEnergy(),cycle.residual(),cycle.energyPassed(),cycle.densityPassed(),
                        cycle.input().densityHash(),cycle.output().densityHash(),state.fock().receiptHash(),state.orbitals().receiptHash(),proof.orbitalReceiptHash(),proof.occupation().receiptHash(),
                        proof.tracePS(),proof.idempotencyStatus(),proof.idempotencyResidual(),proof.receiptHash(),energies,diis));
            },solved->{
                var orbitals=ValidatedOrbitals.fromSolve(system,overlap,solved,"DIIS_UPDATE\n"+PulayDiis.hash(solved.fock()));
                update[0]=ConstructedDensity.build(system,overlap,orbitals,options.densityMode());times[4]+=update[0].elapsedNanos();return update[0].density();
            });
            reason=status==RhfScfResult.Status.CONVERGED?"Both physical-state criteria passed":"Iteration cap reached without both criteria";
            }
        }catch(IllegalArgumentException|ArithmeticException e){status=RhfScfResult.Status.NUMERICAL_FAILURE;reason=e.getMessage();}
        return finish(options,provenance,rows,last[0],initial,status,reason,times,started);
    }
    private static Result finish(Options options,String provenance,List<Iteration> rows,State state,String initial,RhfScfResult.Status status,String reason,long[] times,long start) {
        var canonical=ContentHash.accumulator().line(options.protocol()).line(provenance).line(initial).line(status.name()).line(reason);
        for(var row:rows)canonical.line(row.toString());
        return new Result(options,status,reason,List.copyOf(rows),Optional.ofNullable(state),canonical.finish(),
                new Performance(times[0],times[1],times[2],times[3],times[4],times[5],times[6],System.nanoTime()-start));
    }
    public record State(DensityMatrix density,ExecutionMatrix fock,ValidatedOrbitals orbitals,double electronicHartree,double nuclearHartree,double totalHartree,Optional<BlockedXc.Result> xc) {}
    public record Iteration(int number,double electronicEnergy,double totalEnergy,OptionalDouble deltaEnergy,double densityResidual,boolean energyPassed,boolean densityPassed,
                            String inputDensityHash,String outputDensityHash,String fockHash,String physicalOrbitalsHash,String updateOrbitalsHash,String occupationHash,double tracePS,
                            ConstructedDensity.IdempotencyStatus idempotencyStatus,OptionalDouble idempotencyResidual,String densityEvidenceHash,List<Double> orbitalEnergies,Optional<KohnShamResult.DiisEvidence> diis) {
        public Iteration {orbitalEnergies=List.copyOf(orbitalEnergies);}
    }
    public record Performance(long integralSetupNanos,long jkNanos,long xcNanos,long eigensolveNanos,long densityConstructionNanos,long directIntegralNanos,long directAccumulationNanos,long totalNanos) {}
    public record Result(Options options,RhfScfResult.Status convergenceStatus,String reason,List<Iteration> iterations,Optional<State> state,String receiptHash,Performance performance) {
        public Result {iterations=List.copyOf(iterations);}
        public Optional<State> convergedState(){return convergenceStatus==RhfScfResult.Status.CONVERGED?state:Optional.empty();}
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
}
