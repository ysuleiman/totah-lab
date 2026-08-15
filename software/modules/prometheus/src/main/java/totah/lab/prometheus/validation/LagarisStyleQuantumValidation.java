package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.neural.DenseLayer;
import totah.lab.prometheus.neural.DirichletUnitIntervalNeuralState;
import totah.lab.prometheus.neural.FeedForwardNetwork;
import totah.lab.prometheus.neural.IdentityActivation;
import totah.lab.prometheus.neural.ParameterTensor;
import totah.lab.prometheus.neural.TanhActivation;
import totah.lab.prometheus.variational.FiniteDifferenceAdamOptimizer;
import totah.lab.prometheus.variational.InfiniteSquareWellHamiltonian;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.RayleighQuotientFunctional;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.UniformUnitIntervalPoints;
import totah.lab.prometheus.variational.VariationalProblem;

/** Executable end-to-end neural solution of the 1D unit infinite square well. */
public final class LagarisStyleQuantumValidation {
    private LagarisStyleQuantumValidation() { }

    public static ValidationResult run() {
        DirichletUnitIntervalNeuralState initial=new DirichletUnitIntervalNeuralState(initialNetwork());
        var hamiltonian=new InfiniteSquareWellHamiltonian();
        var functional=new RayleighQuotientFunctional();
        var trainingPoints=UniformUnitIntervalPoints.create(101);
        String identity=CanonicalHashing.sha256Hex("lagaris-unit-box-v1|101|6|tanh|fd-adam-350");
        var problem=new VariationalProblem(initial,hamiltonian,functional,trainingPoints,
                List.of("finite objective","exact boundary conditions","energy error <= 0.02 hartree",
                        "normalized wavefunction RMSE <= 0.03","Schrodinger residual RMS <= 0.35"),identity);
        double initialEnergy=functional.evaluate(initial,hamiltonian,trainingPoints).objective();
        var optimizedResult=new FiniteDifferenceAdamOptimizer(350,0.01,1e-4).optimize(problem);
        var optimized=(DirichletUnitIntervalNeuralState)initial.withParameters(optimizedResult.parameters());
        var finalEvaluation=functional.evaluate(optimized,hamiltonian,UniformUnitIntervalPoints.create(401));
        double energy=finalEvaluation.objective();
        WavefunctionMetrics wavefunction=wavefunctionMetrics(optimized,801);
        DerivativeAudit derivatives=derivativeAudit(optimized,0.371,1e-4);
        double left=optimized.value(point(0)).real(),right=optimized.value(point(1)).real();
        double exact=InfiniteSquareWellHamiltonian.exactGroundEnergyHartree();
        boolean passed=energy<initialEnergy && Math.abs(energy-exact)<=0.02
                && wavefunction.rmse()<=0.03 && finalEvaluation.terms().get("residual_rms")<=0.35
                && Math.abs(left)<=1e-14 && Math.abs(right)<=1e-14
                && derivatives.firstAbsoluteError()<=1e-6 && derivatives.secondAbsoluteError()<=1e-4;
        return new ValidationResult(identity,initialEnergy,energy,exact,Math.abs(energy-exact),
                wavefunction.rmse(),wavefunction.overlap(),finalEvaluation.terms().get("residual_rms"),
                left,right,derivatives,optimizedResult.parameters(),passed);
    }

    private static FeedForwardNetwork initialNetwork() {
        double[] hiddenWeights={-3,-2,-1,1,2,3}; double[] hiddenBiases={1.5,1,.5,-.5,-1,-1.5};
        double[] outputWeights={0,0,0,0,0,0};
        return new FeedForwardNetwork(List.of(
                new DenseLayer(ParameterTensor.of(6,1,hiddenWeights),hiddenBiases,new TanhActivation()),
                new DenseLayer(ParameterTensor.of(1,6,outputWeights),new double[]{1},new IdentityActivation())));
    }

    private static WavefunctionMetrics wavefunctionMetrics(DirichletUnitIntervalNeuralState state,int count) {
        double spacing=1.0/(count-1),norm=0,overlap=0;
        double[] values=new double[count],exact=new double[count];
        for(int i=0;i<count;i++) {
            double x=i*spacing,weight=(i==0||i==count-1)?spacing/2:spacing;
            values[i]=state.value(point(x)).real(); exact[i]=InfiniteSquareWellHamiltonian.exactGroundState(x);
            norm+=weight*values[i]*values[i]; overlap+=weight*values[i]*exact[i];
        }
        double scale=(overlap<0?-1:1)/Math.sqrt(norm),squared=0,normalizedOverlap=0;
        for(int i=0;i<count;i++) {
            double weight=(i==0||i==count-1)?spacing/2:spacing;
            double predicted=scale*values[i]; squared+=weight*(predicted-exact[i])*(predicted-exact[i]);
            normalizedOverlap+=weight*predicted*exact[i];
        }
        return new WavefunctionMetrics(Math.sqrt(squared),normalizedOverlap);
    }

    private static DerivativeAudit derivativeAudit(DirichletUnitIntervalNeuralState state,double x,double step) {
        var analytic=state.evaluateWithDerivatives(point(x));
        double minus=state.value(point(x-step)).real(),center=state.value(point(x)).real();
        double plus=state.value(point(x+step)).real();
        double first=(plus-minus)/(2*step),second=(plus-2*center+minus)/(step*step);
        double analyticFirst=analytic.coordinateGradient().particleGradients().getFirst().x().real();
        double analyticSecond=analytic.coordinateLaplacian().value().real();
        return new DerivativeAudit(analyticFirst,first,Math.abs(analyticFirst-first),analyticSecond,second,
                Math.abs(analyticSecond-second));
    }

    private static QuantumCoordinates point(double x) {
        return new QuantumCoordinates(List.of(new QuantumCoordinates.ParticleCoordinate(
                0,x,0,0,SpinProjection.UNSPECIFIED)));
    }

    public static void main(String[] args) throws IOException {
        ValidationResult result=run(); String json=result.toJson();
        if(args.length==0) System.out.println(json);
        else {
            Path resultPath=Path.of(args[0]); Files.writeString(resultPath,json+"\n",StandardCharsets.UTF_8);
            writeWavefunction(result,resultPath.resolveSibling("LAGARIS_STYLE_INFINITE_WELL_WAVEFUNCTION.csv"));
        }
        if(!result.passed()) throw new IllegalStateException("Lagaris-style validation failed");
    }

    private static void writeWavefunction(ValidationResult result,Path target) throws IOException {
        var state=(DirichletUnitIntervalNeuralState)new DirichletUnitIntervalNeuralState(initialNetwork())
                .withParameters(result.optimizedParameters());
        int integrationCount=2001; double spacing=1.0/(integrationCount-1),norm=0,overlap=0;
        for(int i=0;i<integrationCount;i++) {
            double x=i*spacing,weight=(i==0||i==integrationCount-1)?spacing/2:spacing;
            double value=state.value(point(x)).real(); norm+=weight*value*value;
            overlap+=weight*value*InfiniteSquareWellHamiltonian.exactGroundState(x);
        }
        double scale=(overlap<0?-1:1)/Math.sqrt(norm); StringBuilder csv=new StringBuilder(
                "x_bohr,predicted_normalized_wavefunction,exact_normalized_wavefunction,error\n");
        for(int i=0;i<=100;i++) {
            double x=i/100.0,predicted=scale*state.value(point(x)).real();
            double exact=InfiniteSquareWellHamiltonian.exactGroundState(x);
            csv.append(String.format(Locale.ROOT,"%.8f,%.16g,%.16g,%.16g%n",x,predicted,exact,predicted-exact));
        }
        Files.writeString(target,csv,StandardCharsets.UTF_8);
    }

    public record ValidationResult(String scientificIdentity,double initialEnergyHartree,
            double optimizedEnergyHartree,double exactEnergyHartree,double absoluteEnergyErrorHartree,
            double normalizedWavefunctionRmse,double normalizedWavefunctionOverlap,double residualRms,
            double leftBoundaryValue,double rightBoundaryValue,DerivativeAudit derivativeAudit,
            ParameterVector optimizedParameters,boolean passed) {
        public String toJson() {
            List<String> parameters=new ArrayList<>();
            optimizedParameters.values().forEach(value->parameters.add(String.format(Locale.ROOT,"%.16g",value)));
            return String.format(Locale.ROOT,"""
                    {
                      "scientific_identity": "%s",
                      "initial_energy_hartree": %.16g,
                      "optimized_energy_hartree": %.16g,
                      "exact_energy_hartree": %.16g,
                      "absolute_energy_error_hartree": %.16g,
                      "normalized_wavefunction_rmse": %.16g,
                      "normalized_wavefunction_overlap": %.16g,
                      "schrodinger_residual_rms": %.16g,
                      "left_boundary_value": %.16g,
                      "right_boundary_value": %.16g,
                      "first_derivative_absolute_error": %.16g,
                      "second_derivative_absolute_error": %.16g,
                      "optimized_parameters": [%s],
                      "passed": %s
                    }""",scientificIdentity,initialEnergyHartree,optimizedEnergyHartree,exactEnergyHartree,
                    absoluteEnergyErrorHartree,normalizedWavefunctionRmse,normalizedWavefunctionOverlap,residualRms,
                    leftBoundaryValue,rightBoundaryValue,derivativeAudit.firstAbsoluteError,
                    derivativeAudit.secondAbsoluteError,String.join(", ",parameters),passed);
        }
    }

    public record WavefunctionMetrics(double rmse,double overlap) { }
    public record DerivativeAudit(double analyticFirst,double finiteDifferenceFirst,double firstAbsoluteError,
            double analyticSecond,double finiteDifferenceSecond,double secondAbsoluteError) { }
}
