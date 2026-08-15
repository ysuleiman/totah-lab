package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.neural.CoulombRadialNeuralState;
import totah.lab.prometheus.neural.DenseLayer;
import totah.lab.prometheus.neural.FeedForwardNetwork;
import totah.lab.prometheus.neural.IdentityActivation;
import totah.lab.prometheus.neural.ParameterTensor;
import totah.lab.prometheus.neural.TanhActivation;
import totah.lab.prometheus.variational.FiniteDifferenceAdamOptimizer;
import totah.lab.prometheus.variational.HydrogenAtomHamiltonian;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.ThreeDimensionalRayleighFunctional;
import totah.lab.prometheus.variational.TransformedRadialPointSet;
import totah.lab.prometheus.variational.VariationalProblem;

/** Permanent pure-Java Coulombic physics gate for the hydrogen atom. */
public final class HydrogenAtomNeuralValidation {
    public static final double ENERGY_ERROR_GATE_HARTREE=1e-3;
    public static final double WAVEFUNCTION_RMSE_GATE=5e-3;
    public static final double OVERLAP_GATE=0.9999;
    public static final double CUSP_ERROR_GATE=1e-6;
    public static final double ASYMPTOTIC_DECAY_ERROR_GATE=0.03;
    public static final double GRADIENT_COMPONENT_ERROR_GATE=2e-6;
    public static final double LAPLACIAN_ERROR_GATE=2e-4;
    private HydrogenAtomNeuralValidation() { }

    public static ValidationResult run() {
        var initial=new CoulombRadialNeuralState(1,initialNetwork());
        var hamiltonian=new HydrogenAtomHamiltonian(1);
        var functional=new ThreeDimensionalRayleighFunctional();
        var trainingPoints=TransformedRadialPointSet.create(161,1.0);
        String identity=CanonicalHashing.sha256Hex(
                "hydrogen-coulomb-neural-v1|Z=1|radial-161|4-tanh|fd-adam-220|atomic-units");
        var problem=new VariationalProblem(initial,hamiltonian,functional,trainingPoints,List.of(
                "absolute energy error <= 0.001 hartree","normalized wavefunction RMSE <= 0.005",
                "normalized overlap >= 0.9999","cusp error <= 1e-6",
                "asymptotic decay error <= 0.03","3D gradient component error <= 2e-6",
                "3D Laplacian error <= 2e-4"),identity);
        double initialEnergy=functional.evaluate(initial,hamiltonian,trainingPoints).objective();
        var optimization=new FiniteDifferenceAdamOptimizer(220,0.008,1e-4).optimize(problem);
        var optimized=initial.withParameters(optimization.parameters());
        var finalEvaluation=functional.evaluate(optimized,hamiltonian,TransformedRadialPointSet.create(801,1.0));
        double energy=finalEvaluation.objective();
        WavefunctionMetrics wavefunction=wavefunctionMetrics(optimized,hamiltonian,2001);
        DerivativeAudit derivative=derivativeAudit(optimized,0.4,-0.3,0.2,2e-4);
        double cusp=cuspRatio(optimized,1e-6);
        double asymptotic=asymptoticDecay(optimized,6.0,8.0);
        double exact=hamiltonian.exactGroundEnergyHartree();
        boolean passed=Math.abs(energy-exact)<=ENERGY_ERROR_GATE_HARTREE
                && wavefunction.rmse()<=WAVEFUNCTION_RMSE_GATE && wavefunction.overlap()>=OVERLAP_GATE
                && Math.abs(cusp+1.0)<=CUSP_ERROR_GATE
                && Math.abs(asymptotic-1.0)<=ASYMPTOTIC_DECAY_ERROR_GATE
                && derivative.maxGradientComponentError()<=GRADIENT_COMPONENT_ERROR_GATE
                && derivative.laplacianAbsoluteError()<=LAPLACIAN_ERROR_GATE;
        return new ValidationResult(identity,initialEnergy,energy,exact,Math.abs(energy-exact),
                wavefunction.rmse(),wavefunction.overlap(),finalEvaluation.terms().get("norm"),
                finalEvaluation.terms().get("residual_rms"),cusp,asymptotic,derivative,
                optimization.parameters(),passed);
    }

    private static FeedForwardNetwork initialNetwork() {
        return new FeedForwardNetwork(List.of(
                new DenseLayer(ParameterTensor.of(4,1,new double[]{-2.0,-0.7,0.7,2.0}),
                        new double[]{1.0,0.3,-0.3,-1.0},new TanhActivation()),
                new DenseLayer(ParameterTensor.of(1,4,new double[]{0.02,-0.02,0.02,-0.02}),
                        new double[]{0.12},new IdentityActivation())));
    }

    private static WavefunctionMetrics wavefunctionMetrics(CoulombRadialNeuralState state,
            HydrogenAtomHamiltonian hamiltonian,int count) {
        var points=TransformedRadialPointSet.create(count,1.0);
        double norm=0.0,overlap=0.0;
        for(var point:points.points()) {
            double r=point.coordinates().particles().getFirst().xBohr();
            double predicted=state.value(point.coordinates()).real();
            double exact=hamiltonian.exactNormalizedGroundState(r);
            norm+=point.weight()*predicted*predicted;
            overlap+=point.weight()*predicted*exact;
        }
        double sign=overlap<0?-1.0:1.0,scale=sign/Math.sqrt(norm),error=0.0,normalizedOverlap=0.0;
        for(var point:points.points()) {
            double r=point.coordinates().particles().getFirst().xBohr();
            double predicted=scale*state.value(point.coordinates()).real();
            double exact=hamiltonian.exactNormalizedGroundState(r);
            error+=point.weight()*(predicted-exact)*(predicted-exact);
            normalizedOverlap+=point.weight()*predicted*exact;
        }
        return new WavefunctionMetrics(Math.sqrt(error),normalizedOverlap);
    }

    private static double cuspRatio(CoulombRadialNeuralState state,double radius) {
        var evaluation=state.evaluateWithDerivatives(point(radius,0,0));
        return evaluation.coordinateGradient().particleGradients().getFirst().x().real()/evaluation.value().real();
    }

    private static double asymptoticDecay(CoulombRadialNeuralState state,double firstRadius,double secondRadius) {
        double first=Math.abs(state.value(point(firstRadius,0,0)).real());
        double second=Math.abs(state.value(point(secondRadius,0,0)).real());
        return -Math.log(second/first)/(secondRadius-firstRadius);
    }

    private static DerivativeAudit derivativeAudit(CoulombRadialNeuralState state,double x,double y,double z,
            double step) {
        var analytic=state.evaluateWithDerivatives(point(x,y,z));
        var gradient=analytic.coordinateGradient().particleGradients().getFirst();
        double[] center={x,y,z},finiteGradient=new double[3];
        double centerValue=state.value(point(x,y,z)).real(),finiteLaplacian=0.0;
        for(int axis=0;axis<3;axis++) {
            double[] minus=center.clone(),plus=center.clone(); minus[axis]-=step; plus[axis]+=step;
            double minusValue=state.value(point(minus[0],minus[1],minus[2])).real();
            double plusValue=state.value(point(plus[0],plus[1],plus[2])).real();
            finiteGradient[axis]=(plusValue-minusValue)/(2.0*step);
            finiteLaplacian+=(plusValue-2.0*centerValue+minusValue)/(step*step);
        }
        double[] analyticGradient={gradient.x().real(),gradient.y().real(),gradient.z().real()};
        double maximum=0.0;
        for(int axis=0;axis<3;axis++) maximum=Math.max(maximum,Math.abs(analyticGradient[axis]-finiteGradient[axis]));
        double analyticLaplacian=analytic.coordinateLaplacian().value().real();
        return new DerivativeAudit(analyticGradient,finiteGradient,maximum,analyticLaplacian,finiteLaplacian,
                Math.abs(analyticLaplacian-finiteLaplacian));
    }

    private static QuantumCoordinates point(double x,double y,double z) {
        return new QuantumCoordinates(List.of(new QuantumCoordinates.ParticleCoordinate(
                0,x,y,z,SpinProjection.UNSPECIFIED)));
    }

    public static void main(String[] args) throws IOException {
        ValidationResult result=run();
        if(args.length==0) System.out.println(result.toJson());
        else {
            Path resultPath=Path.of(args[0]); Files.writeString(resultPath,result.toJson()+"\n",StandardCharsets.UTF_8);
            writeWavefunction(result,resultPath.resolveSibling("HYDROGEN_ATOM_WAVEFUNCTION.csv"));
        }
        if(!result.passed()) throw new IllegalStateException("hydrogen physics gate failed");
    }

    private static void writeWavefunction(ValidationResult result,Path target) throws IOException {
        var state=new CoulombRadialNeuralState(1,initialNetwork()).withParameters(result.optimizedParameters());
        var hamiltonian=new HydrogenAtomHamiltonian(1);
        var metrics=wavefunctionMetrics(state,hamiltonian,4001);
        var points=TransformedRadialPointSet.create(4001,1.0);
        double rawNorm=0.0;
        for(var point:points.points()) {
            double value=state.value(point.coordinates()).real(); rawNorm+=point.weight()*value*value;
        }
        double scale=1.0/Math.sqrt(rawNorm);
        StringBuilder csv=new StringBuilder("radius_bohr,predicted_normalized_wavefunction,exact_wavefunction,error\n");
        for(int i=0;i<=160;i++) {
            double radius=i*0.05,predicted=scale*state.value(point(radius,0,0)).real();
            double exact=hamiltonian.exactNormalizedGroundState(radius);
            csv.append(String.format(Locale.ROOT,"%.8f,%.16g,%.16g,%.16g%n",radius,predicted,exact,predicted-exact));
        }
        if(metrics.overlap()<0) throw new IllegalStateException("unexpected wavefunction phase");
        Files.writeString(target,csv,StandardCharsets.UTF_8);
    }

    public record ValidationResult(String scientificIdentity,double initialEnergyHartree,
            double optimizedEnergyHartree,double exactEnergyHartree,double absoluteEnergyErrorHartree,
            double normalizedWavefunctionRmse,double normalizedWavefunctionOverlap,double rawNorm,
            double residualRms,double cuspLogarithmicDerivative,double asymptoticDecayExponent,
            DerivativeAudit derivativeAudit,ParameterVector optimizedParameters,boolean passed) {
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
                      "raw_norm": %.16g,
                      "schrodinger_residual_rms": %.16g,
                      "cusp_logarithmic_derivative": %.16g,
                      "asymptotic_decay_exponent": %.16g,
                      "max_gradient_component_error": %.16g,
                      "laplacian_absolute_error": %.16g,
                      "optimized_parameters": [%s],
                      "passed": %s
                    }""",scientificIdentity,initialEnergyHartree,optimizedEnergyHartree,exactEnergyHartree,
                    absoluteEnergyErrorHartree,normalizedWavefunctionRmse,normalizedWavefunctionOverlap,rawNorm,
                    residualRms,cuspLogarithmicDerivative,asymptoticDecayExponent,
                    derivativeAudit.maxGradientComponentError,derivativeAudit.laplacianAbsoluteError,
                    String.join(", ",parameters),passed);
        }
    }

    public record WavefunctionMetrics(double rmse,double overlap) { }
    public record DerivativeAudit(double[] analyticGradient,double[] finiteDifferenceGradient,
            double maxGradientComponentError,double analyticLaplacian,double finiteDifferenceLaplacian,
            double laplacianAbsoluteError) {
        public DerivativeAudit {
            analyticGradient=analyticGradient.clone(); finiteDifferenceGradient=finiteDifferenceGradient.clone();
        }
        @Override public double[] analyticGradient() { return analyticGradient.clone(); }
        @Override public double[] finiteDifferenceGradient() { return finiteDifferenceGradient.clone(); }
    }
}
