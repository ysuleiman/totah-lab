package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import static totah.lab.aether.matrix.RhfScfResult.Status;

/** DIIS orchestration; all physical operators and stopping criteria use frozen kernels. */
final class DiisRhfScf {
    static final String IMPLEMENTATION="aether-rhf-diis-2";
    static final String PROTOCOL="Java21;SCREENING_ONLY;"+PulayDiis.PROTOCOL+";guess="+RhfScfCalculator.INITIAL_GUESS
            +";energyThreshold=1e-12;densityThreshold=1e-10;both-required;first-deltaE-unavailable;"
            +"E=E(P,F(P));densityResidual=maxAbs(occupied(F(P))-P);DIIS-F-only-for-update;"
            +OneShotRhfCalculator.PROTOCOL+";"+OccupiedDensityCalculator.PROTOCOL+";"+RhfEnergyCalculator.PROTOCOL;
    private DiisRhfScf() {}
    static RhfScfRun solve(QuantumSystem system,List<ContractedGaussian> basis,int cap) throws IOException {
        return solve(system,basis,cap,null);
    }
    static RhfScfRun solve(QuantumSystem system,List<ContractedGaussian> basis,int cap,GhostBasis ghostBasis) throws IOException {
        java.util.Objects.requireNonNull(system);var functions=List.copyOf(basis);
        if(cap<1)throw new IllegalArgumentException("SCF iteration cap must be positive");
        long start=System.nanoTime();long[] times={0,0};String initialHash="UNAVAILABLE";
        var iterations=new ArrayList<RhfScfRun.Iteration>();
        try { if(ghostBasis==null)RhfScfCalculator.validateScope(system,functions); else ghostBasis.validate(system,functions); }
        catch(IllegalArgumentException e){return finish(system,functions,cap,iterations,initialHash,Status.UNSUPPORTED_SYSTEM,e.getMessage(),times[0],times[1],start,ghostBasis);}
        try {
            NuclearRepulsion.calculate(system);
            long integralStarted=System.nanoTime();
            var overlap=OverlapMatrix.compute(functions);var s=matrix(overlap.size(),overlap::get);
            var core=new CoreHamiltonianCalculator(system,functions).calculate();
            var eri=new ElectronRepulsionCalculator(system,functions).calculate();int n=functions.size();
            var profile=BasisPerformance.capture(functions,eri,System.nanoTime()-integralStarted,0,0);
            var zero=DensityMatrix.fromRowMajor(system,functions,Collections.nCopies(Math.multiplyExact(n,n),0.0));
            long timer=System.nanoTime();var zeroJk=JkCalculator.calculate(zero,eri);times[0]+=System.nanoTime()-timer;
            timer=System.nanoTime();var guess=OneShotRhfCalculator.solve(zero,overlap,core,zeroJk);times[1]+=System.nanoTime()-timer;
            var initial=OccupiedDensityCalculator.build(system,overlap,guess.coefficients(),guess.energies());
            initialHash=initial.receipt().receiptHash();
            var status=ScfCycles.run(system,overlap,initial.density(),cap,density->{
                long t=System.nanoTime();var jk=JkCalculator.calculate(density,eri);times[0]+=System.nanoTime()-t;
                t=System.nanoTime();var physical=OneShotRhfCalculator.solve(density,overlap,core,jk);times[1]+=System.nanoTime()-t;
                var energy=RhfEnergyCalculator.evaluate(density,core,physical.fock());
                var plain=OccupiedDensityCalculator.build(system,overlap,physical.coefficients(),physical.energies());
                return new ScfCycles.Evaluation<>(energy.totalHartree(),plain.density(),physical.fock()::get,new Physical(physical,energy,plain));
            },cycle->{
                var p=cycle.evaluation().evidence();var physical=p.orbitals();var plain=p.plain();
                if(cycle.diis()==null) {
                    iterations.add(new RhfScfRun.Iteration(cycle.number(),cycle.input(),physical,p.energy(),cycle.output(),cycle.deltaEnergy(),cycle.residual(),true,true,Optional.empty()));
                    return;
                }
                var step=cycle.diis();times[1]+=cycle.updateNanos();
                String coefficientsHash,energiesHash,occupationHash;double trace,idempotency,normalization;
                if(step.extrapolated()) {
                    var update=cycle.update();var construction=cycle.construction();
                    coefficientsHash=PulayDiis.hash(update.coefficients());
                    energiesHash=SpectralValues.capture(update.energies(),"aether-diis-update-energies-v1").resultHash();
                    occupationHash=construction.occupation().receiptHash();trace=construction.trace();idempotency=construction.idempotency();normalization=construction.orthonormality();
                } else {
                    coefficientsHash=physical.receipt().coefficientsHash();energiesHash=physical.receipt().orbitalEnergiesHash();
                    occupationHash=plain.receipt().occupation().receiptHash();trace=plain.receipt().tracePS();idempotency=plain.receipt().maxIdempotencyError();normalization=plain.receipt().maxOrthonormalityError();
                }
                var evidence=new RhfScfRun.DiisUpdate(step.historyIterations(),step.fockHashes(),step.errorHashes(),step.coefficients(),step.events(),
                        step.errorMaximum(),step.reciprocalCondition(),step.extrapolated(),physical.receipt().receiptHash(),PulayDiis.hash(step.fock()),
                        coefficientsHash,energiesHash,occupationHash,cycle.output().densityHash(),trace,idempotency,normalization);
                iterations.add(new RhfScfRun.Iteration(cycle.number(),cycle.input(),physical,p.energy(),cycle.output(),cycle.deltaEnergy(),cycle.residual(),cycle.energyPassed(),cycle.densityPassed(),Optional.of(evidence)));
            });
            return finish(system,functions,cap,iterations,initialHash,status,status==Status.CONVERGED?"Both physical-state criteria passed":"Iteration cap reached without both criteria",times[0],times[1],start,ghostBasis).withBasisPerformance(profile);
        }catch(IllegalArgumentException|ArithmeticException e){return finish(system,functions,cap,iterations,initialHash,Status.NUMERICAL_FAILURE,e.getMessage(),times[0],times[1],start,ghostBasis);}
    }
    private record Physical(OneShotRhfResult orbitals,RhfEnergyCalculator.Result energy,OccupiedDensityCalculator.Result plain) {}
    static RealMatrix matrix(int n,IntegralMatrixData.Entry entry) {
        var m=new Array2DRowRealMatrix(n,n);for(int i=0;i<n;i++)for(int j=0;j<n;j++)m.setEntry(i,j,entry.get(i,j));return m;
    }
    static String protocol(QuantumSystem system,List<ContractedGaussian> basis,GhostBasis ghostBasis) {
        String protocol=IntegralMatrixData.protocol(PROTOCOL,basis);
        if(system.nuclei().stream().anyMatch(n->n.charge()>=15)) {
            protocol=protocol.replace("STO-3G-H-C-N-O;","STO-3G-H-C-N-O-P-S-Cl;")
                    +";SPCl-basis-sha256="+totah.lab.aether.basis.Sto3gBasis.SPCL_RESOURCE_SHA256;
        }
        if(ghostBasis!=null&&ghostBasis.ghosts().stream().anyMatch(g->g.basisAtomicNumber()>=15))
            protocol=protocol.replace("STO-3G-H-C-N-O;","STO-3G-H-C-N-O-P-S-Cl;");
        if(ghostBasis!=null)protocol+=";ghost-basis-v1="+ghostBasis.identity()+";ghost-Z=0;ghost-electrons=0;real-nuclei-only";
        return protocol;
    }
    private static RhfScfRun finish(QuantumSystem system,List<ContractedGaussian> basis,int cap,List<RhfScfRun.Iteration> rows,
                                    String initial,Status status,String reason,long jk,long eigen,long start,GhostBasis ghostBasis) {
        String systemHash=IntegralMatrixData.systemHash(system);
        String basisHash=basis.isEmpty()?ContentHash.sha256("aether-empty-basis-v1"):IntegralMatrixData.basisGeometryHash(basis);
        String protocol=protocol(system,basis,ghostBasis);
        var canonical=new StringBuilder("aether-diis-trajectory-v1\n").append(initial).append('\n');
        for(var r:rows) {
            canonical.append(r.number()).append('\n').append(ContentHash.number(r.energy().electronicHartree())).append('\n')
                    .append(ContentHash.number(r.energy().totalHartree())).append('\n')
                    .append(r.deltaEnergy().isPresent()?ContentHash.number(r.deltaEnergy().getAsDouble()):"UNAVAILABLE").append('\n')
                    .append(ContentHash.number(r.densityResidual())).append('\n').append(r.density().densityHash()).append('\n')
                    .append(r.physicalOrbitals().receipt().receiptHash()).append('\n').append(r.energy().receipt().receiptHash()).append('\n')
                    .append(r.nextDensity().densityHash()).append('\n').append(r.energyCriterionPassed()).append('\n').append(r.densityCriterionPassed()).append('\n')
                    .append(r.update().map(Object::toString).orElse("CONVERGED_NO_UPDATE")).append('\n');
        }
        canonical.append(status).append('\n');
        String trajectory=canonical.toString();
        var id=IntegralMatrixData.identity(basisHash,IMPLEMENTATION,protocol,reason,"\n"+systemHash+"\n"+cap,trajectory);
        var receipt=new RhfScfRun.Receipt(ScfPolicy.DIIS,protocol,systemHash,basisHash,initial,cap,status,reason,trajectory,id.calculationHash(),id.resultHash(),id.receiptHash());
        return new RhfScfRun(rows,receipt,new RhfScfResult.PerformanceCounters(basis.size(),rows.size(),jk,eigen,System.nanoTime()-start,
                rows.isEmpty()?OptionalDouble.empty():rows.getLast().deltaEnergy(),rows.isEmpty()?OptionalDouble.empty():OptionalDouble.of(rows.getLast().densityResidual())),Optional.empty());
    }
}
