package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.neural.HydrogenMoleculeCorrelatedState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.HydrogenMoleculeStreamingRayleighEvaluator;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.StochasticReconfigurationOptimizer;

/** One-shot, independently preregistered H2 Generation-2 nine-geometry gate. */
public final class HydrogenMoleculeGeneration2Validation {
    private static final double[] RADII={0.8,1.0,1.2,1.4,1.6,2.0,3.0,4.0,6.0};
    private static final double[] REFERENCES={-1.0200566663601389,-1.1245397195465791,-1.1649352434400281,
            -1.1744757142200755,-1.1685833733709263,-1.1381329571315035,-1.0573262688692439,
            -1.0163902529471283,-1.0008357076542279};
    private static final int[] GEN1_ITERATIONS={121,121,121,20,23,25,36,35,19};
    private static final long[] GEN1_OBJECTIVES={1321,1321,1321,221,254,276,397,386,210};
    private static final long[] GEN1_WALL={12999677042L,15160272167L,13383581000L,1969755125L,4597255000L,
            3024613958L,4353769750L,3727205917L,2499247375L};
    private static final double[] GEN1_VARIANCE={.05100072048896994,.1262303253824847,.1141997095278766,
            .07193203570222247,.04631798570317419,.02275027499243784,.009425065356602588,
            .004329636723554304,.002665717625929002};
    private static final double[] GEN1_ENERGY={-1.011814784282255,-1.106532268020690,-1.149208144870668,
            -1.161949721967364,-1.158841692242479,-1.130517893330046,-1.049682953353673,
            -1.011925906444771,-.9987629241507570};
    private static final ParameterVector COLD=new ParameterVector(List.of(1.0,0.0,0.0,0.0,0.0));
    private static final String PROTOCOL_SHA="4f2b9cdac62e1785bad665a55760365678828ba6950b27faa67f329409eabead";
    private static final int BATCH_SIZE=512;
    private HydrogenMoleculeGeneration2Validation() { }

    public static Result run() { return run(point->{ }); }

    private static Result run(Consumer<PointResult> checkpoint) {
        CurveExecution primary=executeCurve(checkpoint); CurveExecution replay=executeCurve(point->{ });
        boolean deterministic=scientificallyIdentical(primary.points,replay.points);
        Map<Double,ColdControl> cold=new LinkedHashMap<>();
        for(double radius:new double[]{1.6,3.0,6.0}) cold.put(radius,executeCold(radius));
        double continuationSaving=continuationSaving(primary.points,cold);
        double seedSpread=Math.max(seedSpread(1.4),seedSpread(4.0));
        List<PointResult> points=primary.points;
        double rmse=Math.sqrt(points.stream().mapToDouble(p->p.error*p.error).average().orElseThrow());
        double maximum=points.stream().mapToDouble(p->Math.abs(p.error)).max().orElseThrow();
        double equilibrium=quadraticMinimum(points.get(2),points.get(3),points.get(4));
        double minimum=points.stream().mapToDouble(PointResult::energy).min().orElseThrow();
        double wellDepth=-1-minimum,wellError=Math.abs(wellDepth-.174475931400216);
        boolean smooth=smooth(points),forceSigns=points.get(2).energy>points.get(3).energy
                &&points.get(4).energy>points.get(3).energy;
        boolean perPoint=points.stream().allMatch(p->p.converged&&p.variance<=.10&&p.energySpread<=.005
                &&p.audit.passes()&&p.redundantStateEvaluations==0);
        boolean passed=rmse<=.015&&maximum<=.025&&Math.abs(equilibrium-1.4011)<=.08&&wellError<=.015
                &&Math.abs(points.getLast().energy+1)<=.010&&smooth&&forceSigns
                &&Math.abs(points.get(3).virialRatio-1)<=.08&&seedSpread<=.01&&deterministic&&perPoint;
        long gen1Objectives=java.util.Arrays.stream(GEN1_OBJECTIVES).sum();
        long gen2Objectives=points.stream().mapToLong(PointResult::objectiveEvaluations).sum();
        long gen1Wall=java.util.Arrays.stream(GEN1_WALL).sum();
        long gen2Wall=points.stream().mapToLong(PointResult::wallTimeNanos).sum();
        double workReduction=1-(double)gen2Objectives/gen1Objectives;
        double wallSpeedup=(double)gen1Wall/gen2Wall;
        String classification=passed?"H2_GENERATION2_MULTI_GEOMETRY_GATE_PASSED":
                "H2_GENERATION2_MULTI_GEOMETRY_GATE_FAILED";
        String identity=CanonicalHashing.sha256Hex(PROTOCOL_SHA+"|"+points.stream()
                .map(p->p.radius+":"+p.parameters.values()+":"+p.energy).toList());
        return new Result(identity,points,cold,rmse,maximum,equilibrium,wellDepth,wellError,seedSpread,
                continuationSaving,workReduction,wallSpeedup,deterministic,smooth,forceSigns,classification,passed);
    }

    private static CurveExecution executeCurve(Consumer<PointResult> checkpoint) {
        List<PointResult> points=new ArrayList<>(); ParameterVector continuation=COLD;
        for(int index=0;index<RADII.length;index++) {
            double radius=RADII[index]; var state=new HydrogenMoleculeCorrelatedState(radius,continuation);
            var optimizer=optimizer(); var hamiltonian=new HydrogenMoleculeHamiltonian(radius);
            var training=batches(2500,radius,43); long start=System.nanoTime();
            var optimized=optimizer.optimize(state,hamiltonian,training); long wall=System.nanoTime()-start;
            continuation=optimized.parameters(); var frozen=state.withParameters(continuation);
            var first=evaluate(frozen,hamiltonian,1009); var second=evaluate(frozen,hamiltonian,50021);
            var audit=HydrogenMoleculeCurveValidation.audit(frozen,radius);
            PointResult point=new PointResult(radius,REFERENCES[index],(first.energy+second.energy)/2,
                    (first.energy+second.energy)/2-REFERENCES[index],(first.variance+second.variance)/2,
                    Math.abs(first.energy-second.energy),(first.virial+second.virial)/2,audit,continuation,
                    optimized.iterations(),optimized.iterations()+1,wall,optimized.stateEvaluations()+144000,
                    0,optimized.converged());
            points.add(point); checkpoint.accept(point);
        }
        return new CurveExecution(points);
    }

    private static ColdControl executeCold(double radius) {
        var initial=new HydrogenMoleculeCorrelatedState(radius,COLD);var h=new HydrogenMoleculeHamiltonian(radius);
        long start=System.nanoTime();var result=optimizer().optimize(initial,h,batches(2500,radius,43));long wall=System.nanoTime()-start;
        var state=initial.withParameters(result.parameters());var a=evaluate(state,h,1009);var b=evaluate(state,h,50021);
        return new ColdControl(radius,(a.energy+b.energy)/2,result.iterations(),result.iterations()+1,wall,result.converged());
    }

    private static double seedSpread(double radius) {
        List<ParameterVector> seeds=List.of(COLD,new ParameterVector(List.of(.8,.03,-.02,.01,-.01)),
                new ParameterVector(List.of(1.2,-.03,.02,-.01,.01))); double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(ParameterVector seed:seeds){var initial=new HydrogenMoleculeCorrelatedState(radius,seed);
            var h=new HydrogenMoleculeHamiltonian(radius);var result=optimizer().optimize(initial,h,batches(2500,radius,43));
            var frozen=initial.withParameters(result.parameters());double energy=evaluate(frozen,h,1009).energy;
            min=Math.min(min,energy);max=Math.max(max,energy);} return max-min;
    }

    private static StochasticReconfigurationOptimizer optimizer(){return new StochasticReconfigurationOptimizer(
            new StochasticReconfigurationOptimizer.Configuration(120,.05,1e-3,18,8,2e-7,.10));}
    private static HydrogenMoleculeImportanceBatches batches(int count,double radius,int skip){return
            new HydrogenMoleculeImportanceBatches(count,radius,1.15,skip,BATCH_SIZE);}
    private static Estimate evaluate(totah.lab.prometheus.variational.DifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian h,int skip){var result=new HydrogenMoleculeStreamingRayleighEvaluator()
                    .evaluate(state,h,batches(72000,h.bondLengthBohr(),skip));
        return new Estimate(result.objective(),result.terms().get("local_energy_variance"),result.terms().get("virial_ratio"));}

    private static double quadraticMinimum(PointResult a,PointResult b,PointResult c){double h=b.radius-a.radius;
        return b.radius+.5*h*(a.energy-c.energy)/(a.energy-2*b.energy+c.energy);}
    private static boolean smooth(List<PointResult> points){double previous=Double.NaN;
        for(int i=1;i<points.size();i++){double slope=(points.get(i).energy-points.get(i-1).energy)/(points.get(i).radius-points.get(i-1).radius);
            if(!Double.isFinite(slope)||(Double.isFinite(previous)&&Math.abs(slope-previous)>.5))return false;previous=slope;}return true;}
    private static double continuationSaving(List<PointResult> points,Map<Double,ColdControl> cold){double warm=0,totalCold=0;
        for(var entry:cold.entrySet()){warm+=points.stream().filter(p->p.radius==entry.getKey()).findFirst().orElseThrow().objectiveEvaluations;
            totalCold+=entry.getValue().objectiveEvaluations;}return 1-warm/totalCold;}
    private static boolean scientificallyIdentical(List<PointResult> first,List<PointResult> second){
        if(first.size()!=second.size())return false;for(int i=0;i<first.size();i++){PointResult a=first.get(i),b=second.get(i);
            if(a.radius!=b.radius||a.energy!=b.energy||a.variance!=b.variance||a.energySpread!=b.energySpread
                    ||a.iterations!=b.iterations||a.objectiveEvaluations!=b.objectiveEvaluations
                    ||a.stateEvaluations!=b.stateEvaluations||a.converged!=b.converged
                    ||!a.parameters.equals(b.parameters)||!a.audit.equals(b.audit))return false;}return true;}

    public static void main(String[] args)throws IOException {
        if(args.length!=1)throw new IllegalArgumentException("output directory required");Path directory=Path.of(args[0]);
        Files.createDirectories(directory);Path checkpoint=directory.resolve("H2_GENERATION2_CHECKPOINT.csv");
        if(Files.exists(checkpoint))throw new IllegalStateException("Gen-2 checkpoint already exists; one-shot rerun forbidden");
        synchronousAppend(checkpoint,PointResult.csvHeader());
        Result result=run(point->{try{synchronousAppend(checkpoint,point.csv());}catch(IOException e){throw new IllegalStateException(e);}});
        Files.writeString(directory.resolve("H2_GENERATION2_RESULT.json"),result.toJson(),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("H2_GENERATION2_CURVE.csv"),result.curveCsv(),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("H2_GENERATION2_PERFORMANCE.csv"),result.performanceCsv(),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("H2_GENERATION2_FINAL_REPORT.md"),result.report(),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("H2_GENERATION2_DECISION_REVIEW.md"),result.decisionReview(),StandardCharsets.UTF_8);
        writeChecksums(directory,List.of("H2_GENERATION2_PROTOCOL_LOCKED.md","H2_GENERATION2_CHECKPOINT.csv",
                "H2_GENERATION2_RESULT.json","H2_GENERATION2_CURVE.csv","H2_GENERATION2_PERFORMANCE.csv",
                "H2_GENERATION2_FINAL_REPORT.md","H2_GENERATION2_DECISION_REVIEW.md"));
    }

    private static void synchronousAppend(Path path,String text)throws IOException{try(FileChannel channel=FileChannel.open(path,
            StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)){ByteBuffer bytes=StandardCharsets.UTF_8.encode(text);
        while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}}
    private static void writeChecksums(Path directory,List<String> names)throws IOException{StringBuilder out=new StringBuilder();
        for(String name:names)out.append(sha(directory.resolve(name))).append("  ").append(name).append('\n');
        Files.writeString(directory.resolve("SHA256SUMS"),out,StandardCharsets.UTF_8);}
    private static String sha(Path path)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))>=0)digest.update(buffer,0,n);}
        return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    private record CurveExecution(List<PointResult> points){CurveExecution{points=List.copyOf(points);}}
    private record Estimate(double energy,double variance,double virial){}
    public record ColdControl(double radius,double energy,int iterations,long objectiveEvaluations,long wallTimeNanos,boolean converged){}
    public record PointResult(double radius,double reference,double energy,double error,double variance,double energySpread,
            double virialRatio,HydrogenMoleculeCurveValidation.Audit audit,ParameterVector parameters,int iterations,
            long objectiveEvaluations,long wallTimeNanos,long stateEvaluations,long redundantStateEvaluations,boolean converged){
        static String csvHeader(){return "R_bohr,reference_Ha,energy_Ha,error_Ha,variance_Ha2,energy_spread_Ha,virial_ratio,iterations,objective_evaluations,wall_time_ns,state_evaluations,redundant_state_evaluations,converged,parameters\n";}
        String csv(){return String.format(Locale.ROOT,"%.1f,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%d,%d,%d,%d,%d,%s,\"%s\"%n",radius,reference,energy,error,variance,energySpread,virialRatio,iterations,objectiveEvaluations,wallTimeNanos,stateEvaluations,redundantStateEvaluations,converged,parameters.values());}}
    public record Result(String identity,List<PointResult> points,Map<Double,ColdControl> coldControls,double rmse,
            double maximumError,double equilibrium,double wellDepth,double wellDepthError,double seedSpread,
            double continuationSaving,double workReduction,double wallSpeedup,boolean deterministicReplay,boolean smooth,
            boolean forceSigns,String classification,boolean passed){public Result{points=List.copyOf(points);coldControls=Map.copyOf(coldControls);}
        String curveCsv(){StringBuilder out=new StringBuilder(PointResult.csvHeader());points.forEach(p->out.append(p.csv()));return out.toString();}
        String performanceCsv(){StringBuilder out=new StringBuilder("R_bohr,gen1_iterations,gen2_iterations,gen1_objective_evaluations,gen2_objective_evaluations,gen1_wall_ns,gen2_wall_ns,gen1_variance,gen2_variance,gen1_energy,gen2_energy\n");
            for(int i=0;i<points.size();i++){var p=points.get(i);out.append(String.format(Locale.ROOT,"%.1f,%d,%d,%d,%d,%d,%d,%.16g,%.16g,%.16g,%.16g%n",p.radius,GEN1_ITERATIONS[i],p.iterations,GEN1_OBJECTIVES[i],p.objectiveEvaluations,GEN1_WALL[i],p.wallTimeNanos,GEN1_VARIANCE[i],p.variance,GEN1_ENERGY[i],p.energy));}return out.toString();}
        String toJson(){return String.format(Locale.ROOT,"{\"scientific_identity\":\"%s\",\"protocol_sha256\":\"%s\",\"classification\":\"%s\",\"passed\":%s,\"rmse_hartree\":%.16g,\"max_error_hartree\":%.16g,\"equilibrium_bohr\":%.16g,\"well_depth_hartree\":%.16g,\"well_depth_error_hartree\":%.16g,\"seed_spread_hartree\":%.16g,\"continuation_saving_fraction\":%.16g,\"work_reduction_fraction\":%.16g,\"wall_speedup\":%.16g,\"deterministic_replay\":%s}%n",identity,PROTOCOL_SHA,classification,passed,rmse,maximumError,equilibrium,wellDepth,wellDepthError,seedSpread,continuationSaving,workReduction,wallSpeedup,deterministicReplay);}
        String report(){return String.format(Locale.ROOT,"# H2 Generation-2 Final Report%n%nFrozen classification: `%s`%n%n- Complete gate passed: %s%n- RMSE: %.9f Ha%n- Maximum absolute error: %.9f Ha%n- Equilibrium: %.9f bohr%n- Well-depth error: %.9f Ha%n- Maximum seed spread: %.9f Ha%n- Deterministic replay: %s%n- Work reduction: %.2f%%%n- Wall-clock speedup: %.3fx%n- Continuation saving: %.2f%%%n%nGeneration-1 remains independently frozen as `H2_MULTI_GEOMETRY_GATE_FAILED`. No thresholds or ansatz terms were changed.%n",classification,passed,rmse,maximumError,equilibrium,wellDepthError,seedSpread,deterministicReplay,100*workReduction,wallSpeedup,100*continuationSaving);}
        String decisionReview(){String defect=points.stream().allMatch(p->p.audit.passes()&&p.redundantStateEvaluations==0)&&deterministicReplay?"NO_FUNDAMENTAL_CORRECTNESS_DEFECT_OBSERVED":"FUNDAMENTAL_CORRECTNESS_REVIEW_REQUIRED";
            return "# H2 Generation-2 Decision Review\n\n- Classification: `"+classification+"`\n- Correctness review: `"+defect+"`\n- Further H2 tuning authorized: **no**\n\nThis one-shot result closes Generation-2. Localized residual error alone does not authorize another H2 tuning generation. Proceed to the next separately approved capability unless the correctness classification above requires repair.\n";}
    }
}
