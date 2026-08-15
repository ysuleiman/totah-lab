package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Frozen-parameter implicit-response audit using the preregistered unregularized solve. */
public final class NonstationaryParameterResponseAudit {
    public static final double PARAMETER_STEP=1e-4,GEOMETRY_STEP_BOHR=1e-3,MINIMUM_PIVOT_RATIO=1e-10;

    public Result evaluate(GeometryDifferentiableQuantumState state,
            HydrogenMoleculeImportanceBatches batches) {
        Objects.requireNonNull(state,"state");Objects.requireNonNull(batches,"batches");
        int size=state.parameters().values().size();
        GradientEvaluation center=gradient(state,new HydrogenMoleculeHamiltonian(state.geometryCoordinateBohr()),batches);
        double[][] hessian=new double[size][size];long evaluations=center.evaluations();
        for(int column=0;column<size;column++) {
            GeometryDifferentiableQuantumState plus=withParameterDisplacement(state,column,PARAMETER_STEP);
            GeometryDifferentiableQuantumState minus=withParameterDisplacement(state,column,-PARAMETER_STEP);
            GradientEvaluation positive=gradient(plus,new HydrogenMoleculeHamiltonian(plus.geometryCoordinateBohr()),batches);
            GradientEvaluation negative=gradient(minus,new HydrogenMoleculeHamiltonian(minus.geometryCoordinateBohr()),batches);
            evaluations+=positive.evaluations()+negative.evaluations();
            for(int row=0;row<size;row++)hessian[row][column]=(positive.gradient()[row]-negative.gradient()[row])/(2*PARAMETER_STEP);
        }
        double radius=state.geometryCoordinateBohr();
        GeometryDifferentiableQuantumState geometryPlus=state.atGeometry(radius+GEOMETRY_STEP_BOHR);
        GeometryDifferentiableQuantumState geometryMinus=state.atGeometry(radius-GEOMETRY_STEP_BOHR);
        GradientEvaluation positiveGeometry=gradient(geometryPlus,new HydrogenMoleculeHamiltonian(radius+GEOMETRY_STEP_BOHR),batches);
        GradientEvaluation negativeGeometry=gradient(geometryMinus,new HydrogenMoleculeHamiltonian(radius-GEOMETRY_STEP_BOHR),batches);
        evaluations+=positiveGeometry.evaluations()+negativeGeometry.evaluations();
        double[] rightHandSide=new double[size];
        for(int row=0;row<size;row++)rightHandSide[row]=-(positiveGeometry.gradient()[row]-negativeGeometry.gradient()[row])/(2*GEOMETRY_STEP_BOHR);
        Solve solve=solve(hessian,rightHandSide);
        boolean determined=solve.rank()==size&&solve.pivotRatio()>=MINIMUM_PIVOT_RATIO;
        double l2=0,max=0;List<Double> gradient=new ArrayList<>(size);
        for(double value:center.gradient()){gradient.add(value);l2+=value*value;max=Math.max(max,Math.abs(value));}
        OptionalDouble responseForce=OptionalDouble.empty();List<Double> parameterResponse=List.of();
        if(determined) {
            double energyResponse=0;List<Double> response=new ArrayList<>(size);
            for(int i=0;i<size;i++){response.add(solve.solution()[i]);energyResponse+=center.gradient()[i]*solve.solution()[i];}
            parameterResponse=List.copyOf(response);responseForce=OptionalDouble.of(-energyResponse);
        }
        Classification classification=determined?Classification.PARAMETER_RESPONSE_DETERMINED
                :Classification.PARAMETER_RESPONSE_UNDERDETERMINED;
        List<String> evidence=List.of(
                "analysis/prometheus/java-neural-nuclear-force-estimator-study/NUCLEAR_FORCE_ESTIMATOR_CAPABILITY_PROTOCOL_LOCKED.md",
                "central parameter-gradient Hessian step=1e-4",
                "central mixed geometry-gradient step=1e-3 bohr",
                "unregularized partial-pivot elimination; no pseudoinverse, shift, truncation, or parameter movement");
        long expected=(2L*size+3)*batches.count();
        return new Result(classification,List.copyOf(gradient),Math.sqrt(l2),max,center.energy(),
                parameterResponse,responseForce,"hartree/bohr",solve.rank(),size,solve.pivotRatio(),
                PARAMETER_STEP,GEOMETRY_STEP_BOHR,evaluations,expected,evaluations-expected,evidence);
    }

    private static GeometryDifferentiableQuantumState withParameterDisplacement(
            GeometryDifferentiableQuantumState state,int index,double displacement) {
        List<Double> values=new ArrayList<>(state.parameters().values());values.set(index,values.get(index)+displacement);
        DifferentiableQuantumState replacement=state.withParameters(new ParameterVector(values));
        if(!(replacement instanceof GeometryDifferentiableQuantumState geometry))
            throw new IllegalArgumentException("withParameters must preserve geometry differentiability");
        return geometry;
    }

    private static GradientEvaluation gradient(GeometryDifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian hamiltonian,HydrogenMoleculeImportanceBatches batches) {
        int size=state.parameters().values().size();Accumulator a=new Accumulator(size);
        batches.forEachBatch(batch->batch.forEach(point->{
            GeometryStateEvaluation geometry=state.evaluateWithGeometryDerivatives(point.coordinates());a.evaluations++;
            DifferentiableStateEvaluation evaluation=geometry.stateEvaluation();
            if(evaluation.parameterGradient().derivatives().size()!=size)throw new IllegalArgumentException("gradient dimension mismatch");
            double psi=evaluation.value().real();if(!Double.isFinite(psi)||Math.abs(psi)<1e-14)return;
            double weight=point.weight()*psi*psi;
            double local=-.5*evaluation.coordinateLaplacian().value().real()/psi+hamiltonian.potential(point.coordinates());
            if(!Double.isFinite(weight)||!Double.isFinite(local))throw new IllegalArgumentException("non-finite contribution");
            a.norm+=weight;a.energy+=weight*local;
            for(int i=0;i<size;i++){double observable=evaluation.parameterGradient().derivatives().get(i).real()/psi;
                a.observable[i]+=weight*observable;a.observableEnergy[i]+=weight*observable*local;}
        }));
        if(!Double.isFinite(a.norm)||a.norm<1e-14)throw new IllegalArgumentException("zero sampled norm");
        double energy=a.energy/a.norm;double[] result=new double[size];
        for(int i=0;i<size;i++)result[i]=2*(a.observableEnergy[i]/a.norm-a.observable[i]/a.norm*energy);
        return new GradientEvaluation(result,energy,a.evaluations);
    }

    private static Solve solve(double[][] matrix,double[] rhs) {
        int size=rhs.length;double[][] a=new double[size][size+1];
        for(int row=0;row<size;row++){System.arraycopy(matrix[row],0,a[row],0,size);a[row][size]=rhs[row];}
        int rank=0;double largest=0,smallest=Double.POSITIVE_INFINITY;
        for(int pivot=0;pivot<size;pivot++){
            int selected=pivot;for(int row=pivot+1;row<size;row++)if(Math.abs(a[row][pivot])>Math.abs(a[selected][pivot]))selected=row;
            double[] swap=a[pivot];a[pivot]=a[selected];a[selected]=swap;double magnitude=Math.abs(a[pivot][pivot]);
            if(magnitude==0||!Double.isFinite(magnitude))break;
            rank++;largest=Math.max(largest,magnitude);smallest=Math.min(smallest,magnitude);
            for(int row=pivot+1;row<size;row++){double factor=a[row][pivot]/a[pivot][pivot];
                for(int column=pivot;column<=size;column++)a[row][column]-=factor*a[pivot][column];}
        }
        double ratio=rank==0?0:smallest/largest;double[] solution=new double[size];
        if(rank==size)for(int row=size-1;row>=0;row--){double remainder=a[row][size];
            for(int column=row+1;column<size;column++)remainder-=a[row][column]*solution[column];solution[row]=remainder/a[row][row];}
        return new Solve(solution,rank,ratio);
    }

    public enum Classification { PARAMETER_RESPONSE_DETERMINED,PARAMETER_RESPONSE_UNDERDETERMINED }
    public record Result(Classification classification,List<Double> variationalGradient,double gradientL2Norm,
            double gradientMaximumAbsolute,double energyHartree,List<Double> parameterResponsePerBohr,
            OptionalDouble implicitResponseForceHartreePerBohr,String responseForceUnits,int responseRank,
            int responseDimension,double pivotRatio,double parameterStep,double geometryStepBohr,
            long stateEvaluations,long expectedStateEvaluations,long redundantStateEvaluations,List<String> evidence) {
        public Result {Objects.requireNonNull(classification);variationalGradient=List.copyOf(variationalGradient);
            parameterResponsePerBohr=List.copyOf(parameterResponsePerBohr);Objects.requireNonNull(implicitResponseForceHartreePerBohr);
            Objects.requireNonNull(responseForceUnits);evidence=List.copyOf(evidence);}
    }
    private record GradientEvaluation(double[] gradient,double energy,long evaluations){}
    private record Solve(double[] solution,int rank,double pivotRatio){}
    private static final class Accumulator{double norm,energy;long evaluations;final double[] observable,observableEnergy;
        Accumulator(int size){observable=new double[size];observableEnergy=new double[size];}}
}
