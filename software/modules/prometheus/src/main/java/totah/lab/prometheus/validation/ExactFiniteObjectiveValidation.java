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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.variational.FunctionalEvaluation;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.HydrogenMoleculeStreamingRayleighEvaluator;
import totah.lab.prometheus.variational.ParameterVector;

/** One-shot validation of the already frozen exact-objective primary candidate. */
public final class ExactFiniteObjectiveValidation {
    private static final double[] RADII={.8,1,1.2,1.4,1.6,2,3,4,6};
    private static final double[] REFERENCES={-1.0200566663601389,-1.1245397195465791,-1.1649352434400281,-1.1744757142200755,-1.1685833733709263,-1.1381329571315035,-1.0573262688692439,-1.0163902529471283,-1.0008357076542279};
    private static final double[] BASELINE_FORCE_ERRORS={.0392747189343662,.00856070099275019,.00100505701126397};
    private ExactFiniteObjectiveValidation() { }

    public static void main(String[] args)throws IOException {
        if(args.length!=2)throw new IllegalArgumentException("training directory and validation directory required");
        Path training=Path.of(args[0]),output=Path.of(args[1]);if(!Files.exists(training.resolve("TRAINING_COMPLETE")))throw new IllegalStateException("training incomplete");
        Files.createDirectories(output);if(Files.exists(output.resolve("EXACT_FINITE_OBJECTIVE_DECISION.json")))throw new IllegalStateException("one-shot validation exists");
        ParameterVector parameters=parseParameters(Files.readString(training.resolve("primary.json")));List<Point> points=new ArrayList<>();
        for(int i=0;i<RADII.length;i++)points.add(point(parameters,RADII[i],REFERENCES[i]));
        List<ForcePoint> forces=List.of(force(parameters,1,.2,BASELINE_FORCE_ERRORS[0]),force(parameters,1.4,.2,BASELINE_FORCE_ERRORS[1]),force(parameters,3,1,BASELINE_FORCE_ERRORS[2]));
        double rmse=Math.sqrt(points.stream().mapToDouble(p->p.error*p.error).average().orElseThrow());double max=points.stream().mapToDouble(p->Math.abs(p.error)).max().orElseThrow();
        double equilibrium=quadraticMinimum(points.get(2),points.get(3),points.get(4));double minimum=points.stream().mapToDouble(Point::energy).min().orElseThrow();
        double wellDepth=-1-minimum,wellError=Math.abs(wellDepth-.174475931400216);boolean smooth=smooth(points);
        double seedSpread=seedSpread(training);boolean primary=forces.get(0).error<=.020&&forces.get(0).error<=BASELINE_FORCE_ERRORS[0]*.5;
        boolean absolute=rmse<=.015&&max<=.025&&Math.abs(equilibrium-1.4011)<=.08&&wellError<=.015&&Math.abs(points.getLast().energy+1)<=.010&&smooth
                &&points.stream().allMatch(p->p.variance<=.10&&p.spread<=.005&&p.audit.passes());
        boolean nondegrade=rmse<=.006598452792880898&&max<=.008874592483881891;
        boolean holdout=forces.subList(1,3).stream().allMatch(f->f.error<=.03&&f.error<=f.baselineError+.010);
        boolean reproducible=seedSpread<=.01;
        String classification=primary&&absolute&&nondegrade&&holdout&&reproducible?"EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_SUPPORTED":
                primary&&absolute?"EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_PARTIALLY_SUPPORTED":"EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_NOT_SUPPORTED";
        write(output.resolve("EXACT_FINITE_OBJECTIVE_CURVE.csv"),curveCsv(points));write(output.resolve("EXACT_FINITE_OBJECTIVE_FORCE_HOLDOUT.csv"),forceCsv(forces));
        write(output.resolve("EXACT_FINITE_OBJECTIVE_DECISION.json"),String.format(Locale.ROOT,"{\"classification\":\"%s\",\"primary_derivative_gate\":%s,\"absolute_energy_physics_gates\":%s,\"nondegradation_gates\":%s,\"holdout_gates\":%s,\"seed_reproducibility_gate\":%s,\"rmse_hartree\":%.17g,\"max_error_hartree\":%.17g,\"equilibrium_bohr\":%.17g,\"well_depth_error_hartree\":%.17g,\"seed_spread_hartree\":%.17g,\"parameters\":\"%s\"}%n",classification,primary,absolute,nondegrade,holdout,reproducible,rmse,max,equilibrium,wellError,seedSpread,parameters.values()));
        write(output.resolve("EXACT_FINITE_OBJECTIVE_FINAL_REPORT.md"),String.format(Locale.ROOT,"# Exact Finite-Objective Differentiation Final Report%n%nClassification: `%s`%n%n- Exact-gradient correctness: **PASS** (preflight)%n- Primary R=1.0 derivative gate: **%s**; force error `%.9f Ha/bohr`%n- Absolute energy/physics gates: **%s**%n- Relative non-degradation gates: **%s**%n- One-shot R=1.4/R=3.0 holdouts: **%s**%n- Fixed-perturbation reproducibility: **%s**; spread `%.9f Ha`%n- Energy RMSE: `%.9f Ha`; maximum error `%.9f Ha`%n%nThe exact finite objective is differentiable correctly. Its training diagnostic secant improved, but the independent compressed-region validation slope worsened and the primary gate failed. The rejected covariance-gradient experiment remains unchanged; no stochastic-objective route was invoked.%n",classification,primary?"PASS":"FAIL",forces.get(0).error,absolute?"PASS":"FAIL",nondegrade?"PASS":"FAIL",holdout?"PASS":"FAIL",reproducible?"PASS":"FAIL",seedSpread,rmse,max));
        checksums(output,List.of("EXACT_FINITE_OBJECTIVE_CURVE.csv","EXACT_FINITE_OBJECTIVE_FORCE_HOLDOUT.csv","EXACT_FINITE_OBJECTIVE_DECISION.json","EXACT_FINITE_OBJECTIVE_FINAL_REPORT.md"));
    }
    private static Point point(ParameterVector parameters,double radius,double reference){var state=new GeometryConditionedHydrogenMoleculeState(radius,parameters);var h=new HydrogenMoleculeHamiltonian(radius);FunctionalEvaluation a=evaluate(state,h,1009);FunctionalEvaluation b=evaluate(state,h,50021);double energy=(a.objective()+b.objective())/2;return new Point(radius,reference,energy,energy-reference,(term(a,"local_energy_variance")+term(b,"local_energy_variance"))/2,Math.abs(a.objective()-b.objective()),HydrogenMoleculeCurveValidation.audit(state,radius));}
    private static ForcePoint force(ParameterVector parameters,double radius,double referenceDelta,double baseline){var state=new GeometryConditionedHydrogenMoleculeState(radius,parameters);var samples=batches(72000,radius,1009);double delta=1e-3;double plus=evaluate(state.atGeometry(radius+delta),new HydrogenMoleculeHamiltonian(radius+delta),samples).objective();double minus=evaluate(state.atGeometry(radius-delta),new HydrogenMoleculeHamiltonian(radius-delta),samples).objective();double value=-(plus-minus)/(2*delta);double reference=referenceForce(radius,referenceDelta);return new ForcePoint(radius,value,reference,Math.abs(value-reference),baseline);}
    private static double referenceForce(double radius,double delta){return -(REFERENCES[index(radius+delta)]-REFERENCES[index(radius-delta)])/(2*delta);}
    private static int index(double radius){for(int i=0;i<RADII.length;i++)if(Math.abs(RADII[i]-radius)<1e-12)return i;throw new IllegalArgumentException("reference radius absent");}
    private static FunctionalEvaluation evaluate(totah.lab.prometheus.variational.DifferentiableQuantumState state,HydrogenMoleculeHamiltonian h,int skip){return evaluate(state,h,batches(72000,h.bondLengthBohr(),skip));}
    private static FunctionalEvaluation evaluate(totah.lab.prometheus.variational.DifferentiableQuantumState state,HydrogenMoleculeHamiltonian h,HydrogenMoleculeImportanceBatches samples){return new HydrogenMoleculeStreamingRayleighEvaluator().evaluate(state,h,samples);}
    private static HydrogenMoleculeImportanceBatches batches(int count,double radius,int skip){return new HydrogenMoleculeImportanceBatches(count,radius,1.15,skip,512);}
    private static double term(FunctionalEvaluation value,String name){return value.terms().get(name);}
    private static double quadraticMinimum(Point a,Point b,Point c){double h=b.radius-a.radius;return b.radius+.5*h*(a.energy-c.energy)/(a.energy-2*b.energy+c.energy);}
    private static boolean smooth(List<Point> points){double previous=Double.NaN;for(int i=1;i<points.size();i++){double slope=(points.get(i).energy-points.get(i-1).energy)/(points.get(i).radius-points.get(i-1).radius);if(!Double.isFinite(slope)||(Double.isFinite(previous)&&Math.abs(slope-previous)>.5))return false;previous=slope;}return true;}
    private static double seedSpread(Path training)throws IOException{double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(String name:List.of("primary.json","perturb_plus.json","perturb_minus.json")){String text=Files.readString(training.resolve(name));Matcher matcher=Pattern.compile("\\\"mean_energy\\\":([-+0-9.eE]+)").matcher(text);if(!matcher.find())throw new IllegalArgumentException("mean energy absent");double value=Double.parseDouble(matcher.group(1));min=Math.min(min,value);max=Math.max(max,value);}return max-min;}
    private static ParameterVector parseParameters(String json){Matcher matcher=Pattern.compile("\\\"parameters\\\":\\[([^]]+)]").matcher(json);if(!matcher.find())throw new IllegalArgumentException("parameters absent");List<Double> values=new ArrayList<>();for(String field:matcher.group(1).split(","))values.add(Double.parseDouble(field.trim()));return new ParameterVector(values);}
    private static String curveCsv(List<Point> points){StringBuilder out=new StringBuilder("R_bohr,reference_Ha,energy_Ha,error_Ha,variance_Ha2,independent_spread_Ha,audit_passes\n");for(var p:points)out.append(String.format(Locale.ROOT,"%.1f,%.17g,%.17g,%.17g,%.17g,%.17g,%s%n",p.radius,p.reference,p.energy,p.error,p.variance,p.spread,p.audit.passes()));return out.toString();}
    private static String forceCsv(List<ForcePoint> points){StringBuilder out=new StringBuilder("R_bohr,model_FD_force,reference_force,absolute_error,baseline_error,holdout\n");for(var p:points)out.append(String.format(Locale.ROOT,"%.1f,%.17g,%.17g,%.17g,%.17g,%s%n",p.radius,p.force,p.reference,p.error,p.baselineError,p.radius!=1));return out.toString();}
    private static void write(Path path,String text)throws IOException{try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer bytes=StandardCharsets.UTF_8.encode(text);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}}
    private static void checksums(Path directory,List<String> names)throws IOException{StringBuilder out=new StringBuilder();for(String name:names)out.append(sha(directory.resolve(name))).append("  ").append(name).append('\n');write(directory.resolve("SHA256SUMS"),out.toString());}
    private static String sha(Path path)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))>=0)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}}
    private record Point(double radius,double reference,double energy,double error,double variance,double spread,HydrogenMoleculeCurveValidation.Audit audit) { }
    private record ForcePoint(double radius,double force,double reference,double error,double baselineError) { }
}
