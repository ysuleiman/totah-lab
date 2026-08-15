package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.neural.HeliumCorrelatedNeuralState;
import totah.lab.prometheus.neural.HeliumUncorrelatedState;
import totah.lab.prometheus.variational.FiniteDifferenceAdamOptimizer;
import totah.lab.prometheus.variational.HeliumHamiltonian;
import totah.lab.prometheus.variational.HeliumImportancePointSet;
import totah.lab.prometheus.variational.HeliumRayleighFunctional;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.VariationalProblem;

/** End-to-end interacting-electron helium physics gate in owned Java code. */
public final class HeliumNeuralValidation {
    private static final double ENERGY_ERROR_GATE=0.015;
    private static final double CUSP_ERROR_GATE=0.01;
    private static final double PERMUTATION_GATE=1e-12;
    private static final double GRADIENT_GATE=3e-6;
    private static final double LAPLACIAN_GATE=5e-4;
    private static final double VIRIAL_GATE=0.08;
    private static final double CORRELATION_RECOVERY_GATE=0.55;
    private static final double SEED_SPREAD_GATE=0.01;
    private HeliumNeuralValidation() { }

    public static ValidationResult run() {
        var hamiltonian=new HeliumHamiltonian(); var functional=new HeliumRayleighFunctional();
        var training=HeliumImportancePointSet.create(3000,1.8,37);
        List<ParameterVector> seeds=List.of(
                new ParameterVector(List.of(0.30,0.00,0.00,0.00,0.00)),
                new ParameterVector(List.of(0.15,0.03,-0.02,0.01,-0.01)),
                new ParameterVector(List.of(0.45,-0.03,0.02,-0.01,0.01)));
        List<Double> seedEnergies=new ArrayList<>(); ParameterVector best=null; double bestTraining=Double.MAX_VALUE;
        int bestSeed=-1;
        for(int seed=0;seed<seeds.size();seed++) {
            var initial=new HeliumCorrelatedNeuralState(seeds.get(seed));
            String identity=CanonicalHashing.sha256Hex("helium-correlated-neural-v1|training-3000|seed="+seed);
            var problem=new VariationalProblem(initial,hamiltonian,functional,training,List.of(
                    "energy error <= 0.015 hartree","electron-nuclear cusp error <= 0.01",
                    "electron-electron cusp error <= 0.01","permutation error <= 1e-12",
                    "correlation recovery >= 55 percent"),identity);
            var optimized=new FiniteDifferenceAdamOptimizer(80,0.012,2e-4).optimize(problem);
            double candidate=functional.evaluate(initial.withParameters(optimized.parameters()),hamiltonian,training).objective();
            seedEnergies.add(candidate);
            if(candidate<bestTraining) { bestTraining=candidate; best=optimized.parameters(); bestSeed=seed; }
        }
        var replayInitial=new HeliumCorrelatedNeuralState(seeds.get(bestSeed));
        String replayIdentity=CanonicalHashing.sha256Hex("helium-correlated-neural-v1|training-3000|seed="+bestSeed);
        var replayProblem=new VariationalProblem(replayInitial,hamiltonian,functional,training,List.of(
                "deterministic optimization replay"),replayIdentity);
        ParameterVector replay=new FiniteDifferenceAdamOptimizer(80,0.012,2e-4).optimize(replayProblem).parameters();
        double reproducibilityDifference=maxParameterDifference(best,replay);
        var correlated=new HeliumCorrelatedNeuralState(best);
        var evaluationPoints=HeliumImportancePointSet.create(30000,1.8,1009);
        var evaluation=functional.evaluate(correlated,hamiltonian,evaluationPoints);
        var stability=functional.evaluate(correlated,hamiltonian,HeliumImportancePointSet.create(15000,1.8,1009));
        var uncorrelated=new HeliumUncorrelatedState(27.0/16.0);
        var baseline=functional.evaluate(uncorrelated,hamiltonian,evaluationPoints);
        double exact=HeliumHamiltonian.referenceGroundEnergyHartree();
        double analyticBaseline=-2.84765625;
        double baselineQuadratureError=Math.abs(baseline.objective()-analyticBaseline);
        double exactGap=exact-analyticBaseline;
        double recovered=(analyticBaseline-evaluation.objective())/(analyticBaseline-exact);
        CuspAudit cusps=cuspAudit(correlated);
        double permutation=permutationError(correlated);
        DerivativeAudit derivatives=derivativeAudit(correlated,2e-4);
        double seedSpread=seedEnergies.stream().mapToDouble(Double::doubleValue).max().orElseThrow()
                -seedEnergies.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double stabilityDifference=Math.abs(evaluation.objective()-stability.objective());
        double r12Response=r12AmplitudeResponse(correlated);
        boolean passed=Math.abs(evaluation.objective()-exact)<=ENERGY_ERROR_GATE
                && cusps.electronNuclearError<=CUSP_ERROR_GATE && cusps.electronElectronError<=CUSP_ERROR_GATE
                && permutation<=PERMUTATION_GATE && derivatives.maxGradientError<=GRADIENT_GATE
                && derivatives.laplacianError<=LAPLACIAN_GATE
                && Math.abs(evaluation.terms().get("virial_ratio")-1.0)<=VIRIAL_GATE
                && recovered>=CORRELATION_RECOVERY_GATE && seedSpread<=SEED_SPREAD_GATE
                && stabilityDifference<=0.005
                && evaluation.terms().get("local_energy_variance")<=0.1
                && baselineQuadratureError<=0.003
                && r12Response>=0.05 && reproducibilityDifference<=1e-14
                && evaluation.terms().get("state_evaluations")==evaluationPoints.points().size();
        String identity=CanonicalHashing.sha256Hex("helium-gate-v1|"+evaluationPoints.provenanceHash()+"|"+best.values());
        return new ValidationResult(identity,baseline.objective(),analyticBaseline,baselineQuadratureError,
                evaluation.objective(),exact,
                Math.abs(evaluation.objective()-exact),exactGap,recovered,evaluation.terms().get("kinetic"),
                evaluation.terms().get("potential"),evaluation.terms().get("virial_ratio"),
                evaluation.terms().get("local_energy_variance"),evaluation.terms().get("norm"),
                stabilityDifference,r12Response,cusps,permutation,derivatives,seedEnergies,seedSpread,
                reproducibilityDifference,best,passed);
    }

    private static double maxParameterDifference(ParameterVector first,ParameterVector second) {
        double maximum=0.0;
        for(int i=0;i<first.values().size();i++) maximum=Math.max(maximum,
                Math.abs(first.values().get(i)-second.values().get(i)));
        return maximum;
    }

    private static double r12AmplitudeResponse(HeliumCorrelatedNeuralState state) {
        double near=state.value(configuration(1,0,0,Math.cos(0.35),Math.sin(0.35),0)).real();
        double far=state.value(configuration(1,0,0,-1,0,0)).real();
        return Math.abs(Math.log(Math.abs(far/near)));
    }

    private static CuspAudit cuspAudit(HeliumCorrelatedNeuralState state) {
        double h=2e-5; double nuclear=sphericalAverageLogDerivative(state,h,2*h);
        double electron=electronElectronLogDerivative(state,h,2*h);
        return new CuspAudit(nuclear,Math.abs(nuclear+2.0),electron,Math.abs(electron-0.5));
    }
    private static double sphericalAverageLogDerivative(HeliumCorrelatedNeuralState state,double firstRadius,
            double secondRadius) {
        double first=sphericalAverage(state,firstRadius),second=sphericalAverage(state,secondRadius);
        return (Math.log(second)-Math.log(first))/(secondRadius-firstRadius);
    }
    private static double sphericalAverage(HeliumCorrelatedNeuralState state,double radius) {
        double[][] directions={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}; double sum=0;
        for(double[] direction:directions) sum+=state.value(configuration(radius*direction[0],radius*direction[1],
                radius*direction[2],1.2,0.3,-0.2)).real();
        return sum/directions.length;
    }
    private static double electronElectronLogDerivative(HeliumCorrelatedNeuralState state,double firstDistance,
            double secondDistance) {
        double first=coalescenceAverage(state,firstDistance),second=coalescenceAverage(state,secondDistance);
        return (Math.log(second)-Math.log(first))/(secondDistance-firstDistance);
    }
    private static double coalescenceAverage(HeliumCorrelatedNeuralState state,double separation) {
        double[][] directions={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}; double sum=0;
        for(double[] direction:directions) {
            double dx=0.5*separation*direction[0],dy=0.5*separation*direction[1],dz=0.5*separation*direction[2];
            sum+=state.value(configuration(1.1+dx,0.2+dy,-0.1+dz,1.1-dx,0.2-dy,-0.1-dz)).real();
        }
        return sum/directions.length;
    }
    private static double permutationError(HeliumCorrelatedNeuralState state) {
        var original=configuration(0.7,-0.2,0.4,-0.5,0.8,0.3);
        var swapped=configuration(-0.5,0.8,0.3,0.7,-0.2,0.4);
        return Math.abs(state.value(original).real()-state.value(swapped).real());
    }
    private static DerivativeAudit derivativeAudit(HeliumCorrelatedNeuralState state,double step) {
        double[] coordinate={0.7,-0.2,0.4,-0.5,0.8,0.3};
        var analytic=state.evaluateWithDerivatives(configuration(coordinate));
        double center=analytic.value().real(),finiteLaplacian=0,maxGradient=0;
        for(int axis=0;axis<6;axis++) {
            double[] minus=coordinate.clone(),plus=coordinate.clone(); minus[axis]-=step; plus[axis]+=step;
            double minusValue=state.value(configuration(minus)).real(),plusValue=state.value(configuration(plus)).real();
            double finiteGradient=(plusValue-minusValue)/(2*step);
            var vector=analytic.coordinateGradient().particleGradients().get(axis/3);
            double analyticGradient=switch(axis%3) { case 0 -> vector.x().real(); case 1 -> vector.y().real(); default -> vector.z().real(); };
            maxGradient=Math.max(maxGradient,Math.abs(analyticGradient-finiteGradient));
            finiteLaplacian+=(plusValue-2*center+minusValue)/(step*step);
        }
        double analyticLaplacian=analytic.coordinateLaplacian().value().real();
        return new DerivativeAudit(maxGradient,analyticLaplacian,finiteLaplacian,
                Math.abs(analyticLaplacian-finiteLaplacian));
    }
    private static QuantumCoordinates configuration(double... xyz) {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0,xyz[0],xyz[1],xyz[2],SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1,xyz[3],xyz[4],xyz[5],SpinProjection.BETA)));
    }

    public static void main(String[] args) throws IOException {
        var result=run();
        if(args.length==0) System.out.println(result.toJson());
        else Files.writeString(Path.of(args[0]),result.toJson(),StandardCharsets.UTF_8);
        if(!result.passed) throw new IllegalStateException("helium physics gate failed");
    }

    public record ValidationResult(String scientificIdentity,double sampledUncorrelatedEnergyHartree,
            double analyticUncorrelatedEnergyHartree,double uncorrelatedQuadratureErrorHartree,
            double correlatedEnergyHartree,double referenceEnergyHartree,double absoluteEnergyErrorHartree,
            double exactCorrelationGapHartree,double recoveredCorrelationFraction,double kineticEnergyHartree,
            double potentialEnergyHartree,double virialRatio,double localEnergyVariance,double normalization,
            double normalizationStabilityEnergyDifference,double r12AmplitudeLogResponse,CuspAudit cuspAudit,double permutationError,
            DerivativeAudit derivativeAudit,List<Double> convergedSeedEnergies,double seedEnergySpread,
            double deterministicReplayParameterDifference,ParameterVector optimizedParameters,boolean passed) {
        public ValidationResult { convergedSeedEnergies=List.copyOf(convergedSeedEnergies); }
        public String toJson() {
            return String.format(Locale.ROOT,"""
                    {"scientific_identity":"%s","sampled_uncorrelated_energy_hartree":%.16g,
                    "analytic_uncorrelated_energy_hartree":%.16g,"uncorrelated_quadrature_error_hartree":%.16g,
                    "correlated_energy_hartree":%.16g,"reference_energy_hartree":%.16g,
                    "absolute_energy_error_hartree":%.16g,"exact_correlation_gap_hartree":%.16g,
                    "recovered_correlation_fraction":%.16g,"kinetic_energy_hartree":%.16g,
                    "potential_energy_hartree":%.16g,"virial_ratio":%.16g,
                    "local_energy_variance":%.16g,"normalization":%.16g,
                    "normalization_stability_energy_difference":%.16g,
                    "r12_amplitude_log_response":%.16g,
                    "electron_nuclear_cusp":%.16g,"electron_nuclear_cusp_error":%.16g,
                    "electron_electron_cusp":%.16g,"electron_electron_cusp_error":%.16g,
                    "permutation_error":%.16g,"max_6d_gradient_error":%.16g,
                    "laplacian_error":%.16g,"seed_energy_spread":%.16g,
                    "deterministic_replay_parameter_difference":%.16g,
                    "optimized_parameters":%s,"passed":%s}%n""",scientificIdentity,
                    sampledUncorrelatedEnergyHartree,analyticUncorrelatedEnergyHartree,
                    uncorrelatedQuadratureErrorHartree,correlatedEnergyHartree,referenceEnergyHartree,
                    absoluteEnergyErrorHartree,exactCorrelationGapHartree,
                    recoveredCorrelationFraction,kineticEnergyHartree,potentialEnergyHartree,virialRatio,
                    localEnergyVariance,normalization,normalizationStabilityEnergyDifference,r12AmplitudeLogResponse,
                    cuspAudit.electronNuclear,cuspAudit.electronNuclearError,cuspAudit.electronElectron,
                    cuspAudit.electronElectronError,permutationError,derivativeAudit.maxGradientError,
                    derivativeAudit.laplacianError,seedEnergySpread,deterministicReplayParameterDifference,
                    optimizedParameters.values(),passed);
        }
    }
    public record CuspAudit(double electronNuclear,double electronNuclearError,double electronElectron,
            double electronElectronError) { }
    public record DerivativeAudit(double maxGradientError,double analyticLaplacian,double finiteDifferenceLaplacian,
            double laplacianError) { }
}
