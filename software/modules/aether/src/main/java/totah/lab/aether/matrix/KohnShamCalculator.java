package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;

/** KS physical model for the same deterministic SCF cycles used by RHF/DIIS. */
public final class KohnShamCalculator {
    public static final String PROTOCOL="aether-KS-LDA-1;Java21;STO-3G-H-C-N-O-P-S-Cl;s/p;real-centers-only;closed-shell;"
            +"F=Hcore+J+Vxc;Eelec=sum(P*Hcore)+0.5*sum(P*J)+Exc;Etot=Eelec+Enuc;no-HF-exchange;"
            +"core-guess;energyThreshold=1e-12;densityThreshold=1e-10;both-required;ScfCycles-v1;"
            +"Lowdin;CommonsMath3.6.1;rankAbsolute=1e-12;rankRelative=1e-10;normalizationTolerance=1e-10;"
            +"ascending-orbitals;max-absolute-AO-pivot-positive;"+PulayDiis.PROTOCOL+";"+OccupiedDensityCalculator.PROTOCOL+";SCREENING_ONLY";
    private KohnShamCalculator(){}
    public static KohnShamResult solve(QuantumSystem system,List<ContractedGaussian> basis,GridDefinition grid,LdaFunctional functional)throws IOException {
        return solve(system,basis,grid,functional,RhfScfCalculator.DEFAULT_MAX_ITERATIONS);
    }
    public static KohnShamResult solve(QuantumSystem system,List<ContractedGaussian> basis,GridDefinition definition,LdaFunctional functional,int cap)throws IOException {
        java.util.Objects.requireNonNull(system);java.util.Objects.requireNonNull(functional);java.util.Objects.requireNonNull(definition);
        var functions=List.copyOf(basis);if(cap<1)throw new IllegalArgumentException("SCF iteration cap must be positive");
        long started=System.nanoTime();long[] times={0,0,0};var rows=new ArrayList<KohnShamResult.Iteration>();
        KohnShamResult.State[] last={null};String initialHash="UNAVAILABLE",gridHash="UNAVAILABLE";int points=0;
        RhfScfResult.Status status;String reason;BasisPerformance profile=null;
        try{RhfScfCalculator.validateScope(system,functions);}catch(IllegalArgumentException e){return finish(system,functions,definition,functional,cap,rows,null,initialHash,gridHash,0,RhfScfResult.Status.UNSUPPORTED_SYSTEM,e.getMessage(),times,started);}
        try {
            var grid=MolecularGrid.build(system,definition);gridHash=grid.receiptHash();points=grid.size();long aoStarted=System.nanoTime();var ao=new AoGrid(grid,functions);long aoTime=System.nanoTime()-aoStarted;
            long integralStarted=System.nanoTime();
            var overlap=OverlapMatrix.compute(functions);var s=DiisRhfScf.matrix(functions.size(),overlap::get);
            var core=new CoreHamiltonianCalculator(system,functions).calculate();var eri=new ElectronRepulsionCalculator(system,functions).calculate();
            profile=BasisPerformance.capture(functions,eri,System.nanoTime()-integralStarted,points,aoTime);
            double nuclear=NuclearRepulsion.calculate(system).hartree();
            long t=System.nanoTime();var guess=OneShotNumerics.solve(s,DiisRhfScf.matrix(functions.size(),core::get));times[2]+=System.nanoTime()-t;
            var construction=OccupiedDensityCalculator.construct(system,overlap,functions.size(),guess.energies().length,guess.coefficients()::getEntry,i->guess.energies()[i]);
            initialHash=ContentHash.sha256(core.receipt().receiptHash()+"\n"+overlap.receipt().receiptHash()+"\n"+construction.occupation().receiptHash()+"\n"+construction.density().densityHash());
            status=ScfCycles.run(system,overlap,construction.density(),cap,p->{
                long timer=System.nanoTime();var j=JkCalculator.calculate(p,eri).coulomb();times[0]+=System.nanoTime()-timer;
                timer=System.nanoTime();var xc=XcIntegration.evaluate(ao,p,functional);times[1]+=System.nanoTime()-timer;
                var f=KsFockMatrix.assemble(p,core,j,xc);
                timer=System.nanoTime();var solved=OneShotNumerics.solve(s,DiisRhfScf.matrix(p.size(),f::get));times[2]+=System.nanoTime()-timer;
                var orbitals=new KsOrbitals(p,overlap,f,solved);
                var next=OccupiedDensityCalculator.construct(system,overlap,p.size(),solved.energies().length,solved.coefficients()::getEntry,i->solved.energies()[i]);
                double electronic=MeanFieldArithmetic.ksEnergy(p,core::get,j::get,xc.energyHartree()),total=electronic+nuclear;
                if(!Double.isFinite(total))throw new ArithmeticException("Nonfinite KS energy");
                String hash=ContentHash.sha256(BasisScope.protocol(PROTOCOL,functions)+"\n"+p.systemHash()+"\n"+p.densityHash()+"\n"+core.receipt().receiptHash()+"\n"+j.receipt().receiptHash()+"\n"+xc.receiptHash()+"\n"+f.receiptHash()+"\n"+ContentHash.number(electronic)+"\n"+ContentHash.number(nuclear)+"\n"+ContentHash.number(total));
                var state=new KohnShamResult.State(p,xc,f,orbitals,new KohnShamResult.Energy(electronic,nuclear,total,hash));
                return new ScfCycles.Evaluation<>(total,next.density(),f::get,state);
            },cycle->{
                var state=cycle.evaluation().evidence();last[0]=state;times[2]+=cycle.updateNanos();
                var energies=new ArrayList<Double>();for(int i=0;i<state.orbitals().size();i++)energies.add(state.orbitals().energy(i));
                Optional<KohnShamResult.DiisEvidence> diis=Optional.empty();var step=cycle.diis();
                if(step!=null)diis=Optional.of(new KohnShamResult.DiisEvidence(step.historyIterations(),step.fockHashes(),step.errorHashes(),step.coefficients(),step.events(),step.errorMaximum(),step.reciprocalCondition(),step.extrapolated(),PulayDiis.hash(step.fock())));
                rows.add(new KohnShamResult.Iteration(cycle.number(),state.energy().electronicHartree(),state.energy().totalHartree(),cycle.deltaEnergy(),cycle.residual(),cycle.energyPassed(),cycle.densityPassed(),energies,cycle.input().densityHash(),cycle.output().densityHash(),state.fock().receiptHash(),state.xc().receiptHash(),state.orbitals().receiptHash(),state.energy().receiptHash(),diis));
            });
            reason=status==RhfScfResult.Status.CONVERGED?"Both physical-state criteria passed":"Iteration cap reached without both criteria";
        }catch(IllegalArgumentException|ArithmeticException e){status=RhfScfResult.Status.NUMERICAL_FAILURE;reason=e.getMessage();}
        return finish(system,functions,definition,functional,cap,rows,last[0],initialHash,gridHash,points,status,reason,times,started).withBasisPerformance(profile);
    }
    private static KohnShamResult finish(QuantumSystem system,List<ContractedGaussian> basis,GridDefinition definition,LdaFunctional functional,int cap,
            List<KohnShamResult.Iteration> rows,KohnShamResult.State last,String initial,String gridHash,int points,RhfScfResult.Status status,String reason,long[] times,long start) {
        String systemHash=IntegralMatrixData.systemHash(system),basisHash=basis.isEmpty()?ContentHash.sha256("empty-basis"):IntegralMatrixData.basisGeometryHash(basis);
        String protocol=BasisScope.protocol(PROTOCOL,basis)+";"+definition.protocol()+";"+functional.protocol();
        String hash=ContentHash.sha256(protocol+"\n"+systemHash+"\n"+basisHash+"\n"+gridHash+"\n"+initial+"\n"+cap+"\n"+status+"\n"+reason+"\n"+rows);
        return new KohnShamResult(new KohnShamResult.Receipt(protocol,systemHash,basisHash,gridHash,functional.name(),initial,cap,status,reason,rows,hash),last,new KohnShamResult.Performance(basis.size(),points,rows.size(),System.nanoTime()-start,times[0],times[1],times[2]));
    }
}
