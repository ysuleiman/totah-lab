package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.neural.HydrogenMoleculeCorrelatedState;
import totah.lab.prometheus.neural.HydrogenMoleculeUncorrelatedState;
import totah.lab.prometheus.variational.ConvergedFiniteDifferenceAdam;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportancePointSet;
import totah.lab.prometheus.variational.HydrogenMoleculeRayleighFunctional;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/** Complete preregistered nine-point H2 Born-Oppenheimer curve gate. */
public final class HydrogenMoleculeCurveValidation {
    private static final double[] RADII={0.8,1.0,1.2,1.4,1.6,2.0,3.0,4.0,6.0};
    private static final double[] REFERENCES={-1.0200566663601389,-1.1245397195465791,-1.1649352434400281,
            -1.1744757142200755,-1.1685833733709263,-1.1381329571315035,-1.0573262688692439,
            -1.0163902529471283,-1.0008357076542279};
    private static final ParameterVector COLD_SEED=new ParameterVector(List.of(1.0,0.0,0.0,0.0,0.0));
    private HydrogenMoleculeCurveValidation() { }

    public static CurveResult run() {
        var functional=new HydrogenMoleculeRayleighFunctional();
        var optimizer=new ConvergedFiniteDifferenceAdam(120,18,8,0.012,2e-4,2e-7);
        List<PointResult> points=new ArrayList<>(); Map<Double,ColdControl> coldControls=new LinkedHashMap<>();
        ParameterVector continuation=COLD_SEED;
        for(int index=0;index<RADII.length;index++) {
            double radius=RADII[index]; var hamiltonian=new HydrogenMoleculeHamiltonian(radius);
            var training=HydrogenMoleculeImportancePointSet.create(2500,radius,1.15,43);
            var initial=new HydrogenMoleculeCorrelatedState(radius,continuation);
            var optimized=optimizer.optimize(initial,hamiltonian,functional,training);
            continuation=optimized.parameters(); var state=initial.withParameters(continuation);
            var evaluationSet=HydrogenMoleculeImportancePointSet.create(18000,radius,1.15,1009);
            var evaluation=functional.evaluate(state,hamiltonian,evaluationSet);
            var baseline=functional.evaluate(new HydrogenMoleculeUncorrelatedState(radius,1.0),hamiltonian,evaluationSet);
            Audit audit=audit(state,radius);
            double stabilityDifference=Double.NaN;
            if(radius==1.4||radius==3.0||radius==6.0) {
                var smaller=HydrogenMoleculeImportancePointSet.create(9000,radius,1.15,1009);
                stabilityDifference=Math.abs(evaluation.objective()-functional.evaluate(state,hamiltonian,smaller).objective());
            }
            points.add(new PointResult(radius,REFERENCES[index],baseline.objective(),evaluation.objective(),
                    evaluation.objective()-REFERENCES[index],evaluation.terms().get("local_energy_variance"),
                    evaluation.terms().get("virial_ratio"),stabilityDifference,audit,continuation,
                    optimized.iterations(),optimized.objectiveEvaluations(),optimized.wallTimeNanos(),
                    evaluation.terms().get("state_evaluations").longValue(),
                    evaluation.terms().get("redundant_state_evaluations").longValue(),optimized.converged()));
            if(radius==1.6||radius==3.0||radius==6.0) {
                var coldInitial=new HydrogenMoleculeCorrelatedState(radius,COLD_SEED);
                var cold=optimizer.optimize(coldInitial,hamiltonian,functional,training);
                double coldEnergy=functional.evaluate(coldInitial.withParameters(cold.parameters()),hamiltonian,evaluationSet).objective();
                coldControls.put(radius,new ColdControl(radius,coldEnergy,cold.iterations(),cold.objectiveEvaluations(),
                        cold.wallTimeNanos(),Math.abs(coldEnergy-evaluation.objective())));
            }
        }
        double rmse=Math.sqrt(points.stream().mapToDouble(p->p.error*p.error).average().orElseThrow());
        double maximum=points.stream().mapToDouble(p->Math.abs(p.error)).max().orElseThrow();
        double equilibrium=quadraticMinimum(points.get(2),points.get(3),points.get(4));
        double minimumEnergy=points.stream().mapToDouble(PointResult::energy).min().orElseThrow();
        double wellDepth=-1.0-minimumEnergy,wellDepthError=Math.abs(wellDepth-0.174475931400216);
        double continuationSaving=continuationSaving(points,coldControls);
        boolean smooth=smoothCurve(points); boolean forceSigns=forceSigns(points);
        double seedSpread=Math.max(seedSpread(1.4,functional,optimizer),seedSpread(4.0,functional,optimizer));
        boolean pointGates=points.stream().allMatch(point->point.audit.passes()&&point.redundantEvaluations==0
                &&Double.isFinite(point.variance)&&point.converged
                &&(!Double.isFinite(point.stabilityDifference)||point.stabilityDifference<=0.005));
        boolean passed=rmse<=0.015&&maximum<=0.025&&Math.abs(equilibrium-1.4011)<=0.08
                &&wellDepthError<=0.015&&Math.abs(points.getLast().energy+1)<=0.010
                &&smooth&&forceSigns&&Math.abs(points.get(3).virialRatio-1)<=0.08
                &&points.get(3).variance<=0.10&&seedSpread<=0.01&&continuationSaving>=0.20&&pointGates;
        String identity=CanonicalHashing.sha256Hex("h2-nine-point-gate-v1|"+points.stream()
                .map(p->p.radius+":"+p.parameters.values()).toList());
        return new CurveResult(identity,points,coldControls,rmse,maximum,equilibrium,wellDepth,wellDepthError,
                continuationSaving,seedSpread,smooth,forceSigns,passed);
    }

    static Audit audit(HydrogenMoleculeCorrelatedState state,double radius) {
        double nuclearMaximum=0;
        for(double nucleus:new double[]{-radius/2,radius/2}) nuclearMaximum=Math.max(nuclearMaximum,
                Math.abs(sphericalNuclearCusp(state,nucleus,radius)+1));
        double electronCusp=electronCusp(state,radius),exchange=exchangeError(state,radius),nuclear=nuclearSwapError(state,radius);
        DerivativeAudit derivative=derivativeAudit(state,radius,2e-4);
        return new Audit(nuclearMaximum,Math.abs(electronCusp-0.5),exchange,nuclear,
                derivative.maxGradientError,derivative.laplacianError);
    }
    private static double sphericalNuclearCusp(HydrogenMoleculeCorrelatedState state,double nucleus,double radius) {
        double h=2e-5; return (Math.log(sphericalAverage(state,nucleus,2*h,radius))
                -Math.log(sphericalAverage(state,nucleus,h,radius)))/h;
    }
    private static double sphericalAverage(HydrogenMoleculeCorrelatedState state,double nucleus,double shell,double radius) {
        double[][] d={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};double sum=0;
        for(double[] v:d) sum+=state.value(configuration(shell*v[0],shell*v[1],nucleus+shell*v[2],
                0.4,-0.3,radius/2+0.7)).real(); return sum/d.length;
    }
    private static double electronCusp(HydrogenMoleculeCorrelatedState state,double radius) {
        double h=2e-5; return (Math.log(coalescence(state,2*h,radius))-Math.log(coalescence(state,h,radius)))/h;
    }
    private static double coalescence(HydrogenMoleculeCorrelatedState state,double separation,double radius) {
        double sum=0;double[][] d={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for(double[] v:d) sum+=state.value(configuration(separation*v[0]/2,separation*v[1]/2,
                0.2+separation*v[2]/2,-separation*v[0]/2,-separation*v[1]/2,
                0.2-separation*v[2]/2)).real(); return sum/d.length;
    }
    private static double exchangeError(HydrogenMoleculeCorrelatedState state,double radius) {
        return Math.abs(state.value(configuration(.3,-.2,radius/2+.4,-.4,.5,-radius/2-.2)).real()
                -state.value(configuration(-.4,.5,-radius/2-.2,.3,-.2,radius/2+.4)).real());
    }
    private static double nuclearSwapError(HydrogenMoleculeCorrelatedState state,double radius) {
        return Math.abs(state.value(configuration(.3,-.2,radius/2+.4,-.4,.5,-radius/2-.2)).real()
                -state.value(configuration(.3,-.2,-radius/2-.4,-.4,.5,radius/2+.2)).real());
    }
    private static DerivativeAudit derivativeAudit(HydrogenMoleculeCorrelatedState state,double radius,double step) {
        double[] xyz={.35,-.22,radius/2+.45,-.41,.37,-radius/2-.31};
        var analytic=state.evaluateWithDerivatives(configuration(xyz));double center=analytic.value().real();
        double finiteLaplacian=0,maxGradient=0;
        for(int axis=0;axis<6;axis++) {double[] minus=xyz.clone(),plus=xyz.clone();minus[axis]-=step;plus[axis]+=step;
            double mv=state.value(configuration(minus)).real(),pv=state.value(configuration(plus)).real();
            var vector=analytic.coordinateGradient().particleGradients().get(axis/3);
            double ag=switch(axis%3){case 0->vector.x().real();case 1->vector.y().real();default->vector.z().real();};
            maxGradient=Math.max(maxGradient,Math.abs(ag-(pv-mv)/(2*step)));
            finiteLaplacian+=(pv-2*center+mv)/(step*step);}
        return new DerivativeAudit(maxGradient,Math.abs(analytic.coordinateLaplacian().value().real()-finiteLaplacian));
    }
    private static double seedSpread(double radius,HydrogenMoleculeRayleighFunctional functional,
            ConvergedFiniteDifferenceAdam optimizer) {
        var h=new HydrogenMoleculeHamiltonian(radius);var set=HydrogenMoleculeImportancePointSet.create(2500,radius,1.15,43);
        List<ParameterVector> seeds=List.of(COLD_SEED,new ParameterVector(List.of(.8,.03,-.02,.01,-.01)),
                new ParameterVector(List.of(1.2,-.03,.02,-.01,.01)));double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(var seed:seeds){var initial=new HydrogenMoleculeCorrelatedState(radius,seed);
            double energy=optimizer.optimize(initial,h,functional,set).objective();min=Math.min(min,energy);max=Math.max(max,energy);}return max-min;
    }
    private static double quadraticMinimum(PointResult a,PointResult b,PointResult c) {
        double h=b.radius-a.radius; double denominator=a.energy-2*b.energy+c.energy;
        return b.radius+0.5*h*(a.energy-c.energy)/denominator;
    }
    private static boolean smoothCurve(List<PointResult> points) {
        double previous=Double.NaN;
        for(int i=1;i<points.size();i++){double slope=(points.get(i).energy-points.get(i-1).energy)/(points.get(i).radius-points.get(i-1).radius);
            if(!Double.isFinite(slope)||(Double.isFinite(previous)&&Math.abs(slope-previous)>0.5))return false;previous=slope;}return true;
    }
    private static boolean forceSigns(List<PointResult> points) { return points.get(2).energy>points.get(3).energy&&points.get(4).energy>points.get(3).energy; }
    private static double continuationSaving(List<PointResult> points,Map<Double,ColdControl> controls) {
        double warm=0,cold=0;for(var entry:controls.entrySet()){var point=points.stream().filter(p->p.radius==entry.getKey()).findFirst().orElseThrow();
            warm+=point.objectiveEvaluations;cold+=entry.getValue().objectiveEvaluations;}return 1-warm/cold;
    }
    private static QuantumCoordinates configuration(double... xyz){return new QuantumCoordinates(List.of(
            new QuantumCoordinates.ParticleCoordinate(0,xyz[0],xyz[1],xyz[2],SpinProjection.ALPHA),
            new QuantumCoordinates.ParticleCoordinate(1,xyz[3],xyz[4],xyz[5],SpinProjection.BETA)));}

    public static void main(String[] args)throws IOException{CurveResult result=run();String json=result.toJson();
        if(args.length==0)System.out.println(json);else{Path directory=Path.of(args[0]);Files.createDirectories(directory);
            Files.writeString(directory.resolve("H2_CURVE_RESULT.json"),json,StandardCharsets.UTF_8);
            writeCsv(result,directory.resolve("H2_CURVE.csv"));writeGeometryEvidence(result,directory);}
        if(!result.passed)throw new IllegalStateException("H2 gate failed");}
    private static void writeGeometryEvidence(CurveResult result,Path directory)throws IOException{
        for(var point:result.points){String identity=CanonicalHashing.sha256Hex("h2-geometry-v1|R="+point.radius
                +"|parameters="+point.parameters.values());String json=String.format(Locale.ROOT,
                "{\"scientific_identity\":\"%s\",\"hamiltonian\":\"H2_BO_FIXED_R\",\"R_bohr\":%.1f,\"nuclei\":[[0,0,%.16g],[0,0,%.16g]],\"ansatz\":\"h2-covalent-r12-neural-v1\",\"parameters\":%s,\"energy_hartree\":%.16g,\"variance_hartree2\":%.16g,\"iterations\":%d,\"objective_evaluations\":%d,\"state_evaluations\":%d,\"redundant_state_evaluations\":%d,\"converged\":%s}%n",
                identity,point.radius,-point.radius/2,point.radius/2,point.parameters.values(),point.energy,point.variance,
                point.iterations,point.objectiveEvaluations,point.stateEvaluations,point.redundantEvaluations,point.converged);
            Files.writeString(directory.resolve(String.format(Locale.ROOT,"H2_R_%03d_EVIDENCE.json",Math.round(point.radius*10))),
                    json,StandardCharsets.UTF_8);}}
    private static void writeCsv(CurveResult result,Path path)throws IOException{StringBuilder csv=new StringBuilder(
            "R_bohr,reference_Ha,uncorrelated_Ha,correlated_Ha,error_Ha,variance,iterations,objective_evaluations,wall_time_ns\n");
        for(var p:result.points)csv.append(String.format(Locale.ROOT,"%.1f,%.16g,%.16g,%.16g,%.16g,%.16g,%d,%d,%d%n",
                p.radius,p.reference,p.uncorrelatedEnergy,p.energy,p.error,p.variance,p.iterations,p.objectiveEvaluations,p.wallTimeNanos));
        Files.writeString(path,csv,StandardCharsets.UTF_8);}

    public record CurveResult(String scientificIdentity,List<PointResult> points,Map<Double,ColdControl> coldControls,
            double rmse,double maximumAbsoluteError,double equilibriumBondLength,double wellDepth,double wellDepthError,
            double continuationSavingFraction,double seedEnergySpread,boolean smoothCurve,boolean forceSignsCorrect,boolean passed){
        public CurveResult{points=List.copyOf(points);coldControls=Map.copyOf(coldControls);}
        public String toJson(){String rows=points.stream().map(PointResult::toJson).reduce((a,b)->a+",\n"+b).orElse("");
            return String.format(Locale.ROOT,"""
                    {"scientific_identity":"%s","rmse_hartree":%.16g,"max_error_hartree":%.16g,
                    "equilibrium_bond_length_bohr":%.16g,"well_depth_hartree":%.16g,"well_depth_error_hartree":%.16g,
                    "continuation_saving_fraction":%.16g,"seed_energy_spread":%.16g,
                    "smooth_curve":%s,"force_signs_correct":%s,"classification":"%s","passed":%s,
                    "cold_controls":%s,"points":[%s]}%n""",scientificIdentity,
                    rmse,maximumAbsoluteError,equilibriumBondLength,wellDepth,wellDepthError,continuationSavingFraction,
                    seedEnergySpread,smoothCurve,forceSignsCorrect,
                    passed?"H2_MULTI_GEOMETRY_GATE_PASSED":"H2_MULTI_GEOMETRY_GATE_FAILED",passed,
                    coldJson(coldControls),rows);}
        private static String coldJson(Map<Double,ColdControl> controls){return controls.values().stream().map(c->String.format(
                Locale.ROOT,"{\"R\":%.1f,\"energy\":%.16g,\"iterations\":%d,\"objective_evaluations\":%d,\"wall_time_ns\":%d,\"warm_cold_energy_difference\":%.16g}",
                c.radius,c.energy,c.iterations,c.objectiveEvaluations,c.wallTimeNanos,c.warmColdEnergyDifference))
                .reduce((a,b)->a+","+b).map(value->"["+value+"]").orElse("[]");}}
    public record PointResult(double radius,double reference,double uncorrelatedEnergy,double energy,double error,
            double variance,double virialRatio,double stabilityDifference,Audit audit,ParameterVector parameters,
            int iterations,long objectiveEvaluations,long wallTimeNanos,long stateEvaluations,long redundantEvaluations,
            boolean converged){String toJson(){return String.format(Locale.ROOT,
                    "{\"R\":%.1f,\"reference\":%.16g,\"energy\":%.16g,\"error\":%.16g,\"variance\":%.16g,\"stability_difference\":%.16g,\"nuclear_cusp_error\":%.16g,\"electron_cusp_error\":%.16g,\"electron_exchange_error\":%.16g,\"nuclear_exchange_error\":%.16g,\"gradient_error\":%.16g,\"laplacian_error\":%.16g,\"iterations\":%d,\"objective_evaluations\":%d,\"redundant_evaluations\":%d,\"converged\":%s}",
                    radius,reference,energy,error,variance,stabilityDifference,audit.nuclearCuspMaxError,
                    audit.electronCuspError,audit.electronExchangeError,audit.nuclearExchangeError,
                    audit.gradientError,audit.laplacianError,iterations,objectiveEvaluations,redundantEvaluations,converged)
                    .replace("\"converged\":"+converged,"\"parameters\":\""+parameters.values()+"\",\"converged\":"+converged);}}
    public record ColdControl(double radius,double energy,int iterations,long objectiveEvaluations,long wallTimeNanos,
            double warmColdEnergyDifference){}
    public record Audit(double nuclearCuspMaxError,double electronCuspError,double electronExchangeError,
            double nuclearExchangeError,double gradientError,double laplacianError){boolean passes(){return nuclearCuspMaxError<=.015
                    &&electronCuspError<=.015&&electronExchangeError<=1e-12&&nuclearExchangeError<=1e-12
                    &&gradientError<=3e-6&&laplacianError<=5e-4;}}
    private record DerivativeAudit(double maxGradientError,double laplacianError){}
}
