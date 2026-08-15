package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.numerics.FixedPreconditioners;
import totah.lab.prometheus.numerics.StreamingCovarianceOperator;
import totah.lab.prometheus.numerics.TrueResidualPreconditionedConjugateGradientSolver;

/** Step-0 H2 optimizer using the qualified streamed covariance and fixed 5x4 BLOCK policy. */
public final class BlockMatrixFreeHydrogenMoleculeOptimizer {
    private static final int PARAMETER_COUNT=20, BLOCK_COUNT=5, BLOCK_SIZE=4;
    private final Configuration configuration;

    public BlockMatrixFreeHydrogenMoleculeOptimizer(Configuration configuration) {
        this.configuration=Objects.requireNonNull(configuration,"configuration");
    }

    public Result optimize(GeometryConditionedHydrogenMoleculeState initial,
            HydrogenMoleculeHamiltonian hamiltonian, HydrogenMoleculeImportanceBatches batches) {
        Objects.requireNonNull(initial,"initial");Objects.requireNonNull(hamiltonian,"hamiltonian");
        Objects.requireNonNull(batches,"batches");
        if(Double.doubleToRawLongBits(initial.geometryCoordinateBohr())
                !=Double.doubleToRawLongBits(hamiltonian.bondLengthBohr())) {
            throw new IllegalArgumentException("state/Hamiltonian geometry mismatch");
        }
        GeometryConditionedHydrogenMoleculeState state=initial;long evaluations=0,operatorPasses=0;
        int totalSolverIterations=0;double residual=Double.NaN;List<Double> energies=new ArrayList<>();
        for(int iteration=0;iteration<configuration.iterations();iteration++) {
            Statistics statistics=statistics(state,hamiltonian,batches);evaluations+=statistics.stateEvaluations;
            energies.add(statistics.energy);List<FixedPreconditioners.Block> blocks=blocks(statistics);
            var operator=new StreamingCovarianceOperator(PARAMETER_COUNT,
                    source(state,statistics.mean,statistics.normalization,batches),configuration.damping());
            double[] rhs=statistics.gradient.clone();for(int i=0;i<rhs.length;i++)rhs[i]=-rhs[i];
            var solve=new TrueResidualPreconditionedConjugateGradientSolver().solve(operator,
                    FixedPreconditioners.blocks(PARAMETER_COUNT,blocks),rhs,
                    new TrueResidualPreconditionedConjugateGradientSolver.Configuration(
                            configuration.maximumSolverIterations(),configuration.relativeResidualTolerance(),
                            configuration.absoluteResidualTolerance()));
            if(!solve.converged())throw new IllegalStateException("BLOCK matrix-free SR failed true-residual gate");
            operatorPasses+=operator.counters().streamedPasses();totalSolverIterations+=solve.iterations();
            residual=solve.relativeTrueResidual();List<Double> next=new ArrayList<>(state.parameters().values());
            for(int i=0;i<PARAMETER_COUNT;i++) {
                double update=configuration.learningRate()*solve.solution()[i];
                update=Math.max(-configuration.maximumAbsoluteUpdate(),Math.min(configuration.maximumAbsoluteUpdate(),update));
                next.set(i,next.get(i)+update);
            }
            state=state.withParameters(new ParameterVector(next));
        }
        Statistics finalStatistics=statistics(state,hamiltonian,batches);evaluations+=finalStatistics.stateEvaluations;
        energies.add(finalStatistics.energy);
        return new Result(state.parameters(),finalStatistics.energy,List.copyOf(energies),configuration.iterations(),
                totalSolverIterations,operatorPasses,evaluations,residual,
                "BLOCK_PRECONDITIONED_MATRIX_FREE_SR","INDEPENDENT_TRUE_RESIDUAL");
    }

    private List<FixedPreconditioners.Block> blocks(Statistics statistics) {
        List<FixedPreconditioners.Block> blocks=new ArrayList<>();
        for(int block=0;block<BLOCK_COUNT;block++) {
            double[][] matrix=new double[BLOCK_SIZE][BLOCK_SIZE];
            for(int i=0;i<BLOCK_SIZE;i++)for(int j=0;j<BLOCK_SIZE;j++)
                matrix[i][j]=statistics.blocks[block][i][j];
            for(int i=0;i<BLOCK_SIZE;i++)matrix[i][i]+=configuration.damping();
            blocks.add(new FixedPreconditioners.Block(block*BLOCK_SIZE,(block+1)*BLOCK_SIZE,matrix));
        }
        return List.copyOf(blocks);
    }

    private static StreamingCovarianceOperator.CenteredObservationSource source(
            GeometryConditionedHydrogenMoleculeState state,double[] mean,double normalization,
            HydrogenMoleculeImportanceBatches batches) {
        return consumer->batches.forEachBatch(batch->batch.forEach(point->{
            var evaluation=state.evaluateWithGeometryDerivatives(point.coordinates()).stateEvaluation();
            double psi=evaluation.value().real();if(!Double.isFinite(psi)||Math.abs(psi)<1e-14)return;
            double[] centered=new double[PARAMETER_COUNT];
            for(int i=0;i<PARAMETER_COUNT;i++)centered[i]=evaluation.parameterGradient().derivatives().get(i).real()/psi-mean[i];
            consumer.accept(point.weight()*psi*psi/normalization,centered);
        }));
    }

    private static Statistics statistics(GeometryConditionedHydrogenMoleculeState state,
            HydrogenMoleculeHamiltonian hamiltonian,HydrogenMoleculeImportanceBatches batches) {
        Mutable m=new Mutable();batches.forEachBatch(batch->batch.forEach(point->{
            var evaluation=state.evaluateWithGeometryDerivatives(point.coordinates()).stateEvaluation();m.stateEvaluations++;
            double psi=evaluation.value().real();if(!Double.isFinite(psi)||Math.abs(psi)<1e-14)return;
            double weight=point.weight()*psi*psi,local=-.5*evaluation.coordinateLaplacian().value().real()/psi
                    +hamiltonian.potential(point.coordinates());double[] observable=new double[PARAMETER_COUNT];
            for(int i=0;i<PARAMETER_COUNT;i++)observable[i]=evaluation.parameterGradient().derivatives().get(i).real()/psi;
            m.norm+=weight;m.energy+=weight*local;
            for(int i=0;i<PARAMETER_COUNT;i++){m.o[i]+=weight*observable[i];m.oe[i]+=weight*observable[i]*local;}
            for(int block=0;block<BLOCK_COUNT;block++)for(int i=0;i<BLOCK_SIZE;i++)for(int j=0;j<BLOCK_SIZE;j++)
                m.rawBlocks[block][i][j]+=weight*observable[block*BLOCK_SIZE+i]*observable[block*BLOCK_SIZE+j];
        }));
        if(!(m.norm>1e-14)||!Double.isFinite(m.norm))throw new IllegalArgumentException("invalid H2 sampled norm");
        double energy=m.energy/m.norm;double[] mean=new double[PARAMETER_COUNT],gradient=new double[PARAMETER_COUNT];
        double[][][] blocks=new double[BLOCK_COUNT][BLOCK_SIZE][BLOCK_SIZE];
        for(int i=0;i<PARAMETER_COUNT;i++){mean[i]=m.o[i]/m.norm;gradient[i]=2*(m.oe[i]/m.norm-mean[i]*energy);}
        for(int block=0;block<BLOCK_COUNT;block++)for(int i=0;i<BLOCK_SIZE;i++)for(int j=0;j<BLOCK_SIZE;j++)
            blocks[block][i][j]=m.rawBlocks[block][i][j]/m.norm-mean[block*BLOCK_SIZE+i]*mean[block*BLOCK_SIZE+j];
        return new Statistics(energy,m.norm,mean,gradient,blocks,m.stateEvaluations);
    }

    public record Configuration(int iterations,double learningRate,double damping,double maximumAbsoluteUpdate,
            int maximumSolverIterations,double relativeResidualTolerance,double absoluteResidualTolerance) {
        public Configuration {
            if(iterations<1||!(learningRate>0)||!(damping>0)||!(maximumAbsoluteUpdate>0)
                    ||maximumSolverIterations<1||!(relativeResidualTolerance>0)||!(absoluteResidualTolerance>0))
                throw new IllegalArgumentException("invalid BLOCK matrix-free SR configuration");
        }
    }
    public record Result(ParameterVector parameters,double energyHartree,List<Double> energyHistory,int iterations,
            int solverIterations,long operatorPasses,long stateEvaluations,double finalRelativeTrueResidual,
            String optimizer,String residualDefinition){public Result{energyHistory=List.copyOf(energyHistory);}}
    private record Statistics(double energy,double normalization,double[] mean,double[] gradient,double[][][] blocks,long stateEvaluations){}
    private static final class Mutable{double norm,energy;long stateEvaluations;final double[] o=new double[PARAMETER_COUNT],oe=new double[PARAMETER_COUNT];final double[][][] rawBlocks=new double[BLOCK_COUNT][BLOCK_SIZE][BLOCK_SIZE];}
}
