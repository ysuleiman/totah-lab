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

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.neural.CubicChebyshevGeometryEncoder;
import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.variational.FunctionalEvaluation;
import totah.lab.prometheus.variational.GeometryConditionedStochasticReconfigurationOptimizer;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.HydrogenMoleculeNuclearForceEstimator;
import totah.lab.prometheus.variational.HydrogenMoleculeStreamingRayleighEvaluator;
import totah.lab.prometheus.variational.ParameterVector;

/** One-shot shared-geometry H2 curve and native nuclear-force capability gate. */
public final class HydrogenMoleculeSharedGeometryForceValidation {
    private static final double[] RADII={.8,1,1.2,1.4,1.6,2,3,4,6};
    private static final double[] REFERENCES={-1.0200566663601389,-1.1245397195465791,-1.1649352434400281,
            -1.1744757142200755,-1.1685833733709263,-1.1381329571315035,-1.0573262688692439,
            -1.0163902529471283,-1.0008357076542279};
    private static final int BATCH=512;
    private static final long GEN2_OBJECTIVE_PASSES=1057;
    private HydrogenMoleculeSharedGeometryForceValidation() { }

    public static Result run(String protocolSha) {
        var primary=optimize(CubicChebyshevGeometryEncoder.coldStart());
        var replay=optimize(CubicChebyshevGeometryEncoder.coldStart());
        boolean deterministic=primary.parameters().equals(replay.parameters())
                &&primary.meanEnergy()==replay.meanEnergy()&&primary.iterations()==replay.iterations();
        double seedSpread=seedSpread();
        List<Point> points=new ArrayList<>();
        for(int i=0;i<RADII.length;i++) points.add(evaluatePoint(primary,RADII[i],REFERENCES[i]));
        double rmse=Math.sqrt(points.stream().mapToDouble(p->p.error()*p.error()).average().orElseThrow());
        double maximum=points.stream().mapToDouble(p->Math.abs(p.error())).max().orElseThrow();
        double equilibrium=quadraticMinimum(points.get(2),points.get(3),points.get(4));
        double minimum=points.stream().mapToDouble(Point::energy).min().orElseThrow();
        double wellDepth=-1-minimum,wellError=Math.abs(wellDepth-.174475931400216);
        boolean smooth=smooth(points),forceSigns=points.get(2).energy()>points.get(3).energy()
                &&points.get(4).energy()>points.get(3).energy();
        boolean pointGates=points.stream().allMatch(p->p.variance()<=.10&&p.spread()<=.005&&p.audit().passes());
        boolean sharedPass=rmse<=.015&&maximum<=.025&&Math.abs(equilibrium-1.4011)<=.08&&wellError<=.015
                &&Math.abs(points.getLast().energy()+1)<=.010&&smooth&&forceSigns
                &&Math.abs(points.get(3).virial()-1)<=.08&&seedSpread<=.01&&deterministic&&pointGates
                &&primary.converged();
        List<ForcePoint> forces=List.of(force(primary.parameters(),1,.2),force(primary.parameters(),1.4,.2),
                force(primary.parameters(),3,1));
        boolean forcePass=forces.stream().allMatch(f->f.modelFiniteDifferenceError()<=.01
                &&f.referenceError()<=.03&&f.result().redundantStateEvaluations()==0);
        String classification=classification(sharedPass,forcePass,deterministic,points,forces);
        String identity=CanonicalHashing.sha256Hex(protocolSha+"|"+primary.parameters().values()+"|"+points+"|"+forces);
        return new Result(identity,protocolSha,primary,points,forces,rmse,maximum,equilibrium,wellDepth,wellError,
                seedSpread,deterministic,smooth,forceSigns,sharedPass,forcePass,classification);
    }

    private static GeometryConditionedStochasticReconfigurationOptimizer.Result optimize(ParameterVector seed) {
        return new GeometryConditionedStochasticReconfigurationOptimizer(
                new GeometryConditionedStochasticReconfigurationOptimizer.Configuration(120,18,8,.05,1e-3,2e-7,.10))
                .optimize(seed,java.util.Arrays.stream(RADII).boxed().toList());
    }

    private static double seedSpread() {
        List<ParameterVector> seeds=new ArrayList<>();seeds.add(CubicChebyshevGeometryEncoder.coldStart());
        for(double sign:new double[]{-1,1}) {List<Double> values=new ArrayList<>(CubicChebyshevGeometryEncoder.coldStart().values());
            for(int i=0;i<values.size();i++) values.set(i,values.get(i)+sign*(i%4==0?.02:.005));
            seeds.add(new ParameterVector(values));}
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(ParameterVector seed:seeds){double energy=optimize(seed).meanEnergy();min=Math.min(min,energy);max=Math.max(max,energy);}
        return max-min;
    }

    private static Point evaluatePoint(GeometryConditionedStochasticReconfigurationOptimizer.Result optimized,
            double radius,double reference) {
        var state=new GeometryConditionedHydrogenMoleculeState(radius,optimized.parameters());
        var h=new HydrogenMoleculeHamiltonian(radius);var first=evaluate(state,h,1009);var second=evaluate(state,h,50021);
        double energy=(first.objective()+second.objective())/2;
        return new Point(radius,reference,energy,energy-reference,
                (term(first,"local_energy_variance")+term(second,"local_energy_variance"))/2,
                Math.abs(first.objective()-second.objective()),
                (term(first,"virial_ratio")+term(second,"virial_ratio"))/2,
                HydrogenMoleculeCurveValidation.audit(state,radius));
    }

    private static ForcePoint force(ParameterVector parameters,double radius,double referenceDelta) {
        var state=new GeometryConditionedHydrogenMoleculeState(radius,parameters);
        var samples=batches(72000,radius,1009);
        var result=new HydrogenMoleculeNuclearForceEstimator().evaluate(state,new HydrogenMoleculeHamiltonian(radius),samples);
        double delta=1e-3;
        double plus=evaluate(state.atGeometry(radius+delta),new HydrogenMoleculeHamiltonian(radius+delta),samples).objective();
        double minus=evaluate(state.atGeometry(radius-delta),new HydrogenMoleculeHamiltonian(radius-delta),samples).objective();
        double modelFd=-(plus-minus)/(2*delta);
        double reference=referenceForce(radius,referenceDelta);
        return new ForcePoint(radius,result,modelFd,reference,Math.abs(result.forceHartreePerBohr()-modelFd),
                Math.abs(result.forceHartreePerBohr()-reference));
    }

    private static double referenceForce(double radius,double delta) {
        int minus=index(radius-delta),plus=index(radius+delta);
        return -(REFERENCES[plus]-REFERENCES[minus])/(2*delta);
    }
    private static int index(double radius){for(int i=0;i<RADII.length;i++)if(Math.abs(RADII[i]-radius)<1e-12)return i;
        throw new IllegalArgumentException("reference radius not present: "+radius);}
    private static FunctionalEvaluation evaluate(totah.lab.prometheus.variational.DifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian h,int skip){return new HydrogenMoleculeStreamingRayleighEvaluator()
                    .evaluate(state,h,batches(72000,h.bondLengthBohr(),skip));}
    private static FunctionalEvaluation evaluate(totah.lab.prometheus.variational.DifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian h,HydrogenMoleculeImportanceBatches samples){return
            new HydrogenMoleculeStreamingRayleighEvaluator().evaluate(state,h,samples);}
    private static HydrogenMoleculeImportanceBatches batches(int count,double radius,int skip){return
            new HydrogenMoleculeImportanceBatches(count,radius,1.15,skip,BATCH);}
    private static double term(FunctionalEvaluation value,String name){return value.terms().get(name);}
    private static double quadraticMinimum(Point a,Point b,Point c){double h=b.radius()-a.radius();
        return b.radius()+.5*h*(a.energy()-c.energy())/(a.energy()-2*b.energy()+c.energy());}
    private static boolean smooth(List<Point> points){double previous=Double.NaN;for(int i=1;i<points.size();i++){
        double slope=(points.get(i).energy()-points.get(i-1).energy())/(points.get(i).radius()-points.get(i-1).radius());
        if(!Double.isFinite(slope)||(Double.isFinite(previous)&&Math.abs(slope-previous)>.5))return false;previous=slope;}return true;}
    private static String classification(boolean shared,boolean force,boolean deterministic,List<Point> points,List<ForcePoint> forces){
        boolean correctness=deterministic&&points.stream().allMatch(p->p.audit().passes())
                &&forces.stream().allMatch(f->Double.isFinite(f.result().forceHartreePerBohr()));
        if(!correctness)return "FUNDAMENTAL_CORRECTNESS_DEFECT";
        if(shared&&force)return "GEOMETRY_CONDITIONED_H2_FORCE_VALIDATED";
        if(shared)return "SHARED_STATE_PASSES_FORCE_FAILS";
        if(force)return "SHARED_STATE_FAILS_FORCE_PASSES";
        return "GEOMETRY_CONDITIONED_H2_FORCE_FAILED";
    }

    public static void main(String[] args)throws IOException {
        if(args.length!=2)throw new IllegalArgumentException("protocol path and output directory required");
        Path protocol=Path.of(args[0]),directory=Path.of(args[1]);Files.createDirectories(directory);
        Path resultPath=directory.resolve("H2_SHARED_GEOMETRY_FORCE_RESULT.json");
        if(Files.exists(resultPath))throw new IllegalStateException("one-shot result already exists; rerun forbidden");
        String protocolSha=sha(protocol);synchronousWrite(directory.resolve("PROTOCOL_SHA256"),protocolSha+"\n");
        Result result=run(protocolSha);
        synchronousWrite(resultPath,result.toJson());
        synchronousWrite(directory.resolve("H2_SHARED_GEOMETRY_CURVE.csv"),result.curveCsv());
        synchronousWrite(directory.resolve("H2_NATIVE_FORCE_VALIDATION.csv"),result.forceCsv());
        synchronousWrite(directory.resolve("H2_SHARED_GEOMETRY_PERFORMANCE.csv"),result.performanceCsv());
        synchronousWrite(directory.resolve("H2_SHARED_GEOMETRY_FORCE_FINAL_REPORT.md"),result.report());
        writeChecksums(directory,List.of("PROTOCOL_SHA256","H2_SHARED_GEOMETRY_FORCE_RESULT.json",
                "H2_SHARED_GEOMETRY_CURVE.csv","H2_NATIVE_FORCE_VALIDATION.csv",
                "H2_SHARED_GEOMETRY_PERFORMANCE.csv","H2_SHARED_GEOMETRY_FORCE_FINAL_REPORT.md"));
    }

    private static void synchronousWrite(Path path,String text)throws IOException{try(FileChannel channel=FileChannel.open(path,
            StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer bytes=StandardCharsets.UTF_8.encode(text);
        while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}}
    private static void writeChecksums(Path directory,List<String> names)throws IOException{StringBuilder out=new StringBuilder();
        for(String name:names)out.append(sha(directory.resolve(name))).append("  ").append(name).append('\n');
        synchronousWrite(directory.resolve("SHA256SUMS"),out.toString());}
    private static String sha(Path path)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))>=0)digest.update(buffer,0,n);}
        return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    public record Point(double radius,double reference,double energy,double error,double variance,double spread,
            double virial,HydrogenMoleculeCurveValidation.Audit audit) { }
    public record ForcePoint(double radius,HydrogenMoleculeNuclearForceEstimator.Result result,double modelFiniteDifferenceForce,
            double referenceForce,double modelFiniteDifferenceError,double referenceError) { }
    public record Result(String identity,String protocolSha,GeometryConditionedStochasticReconfigurationOptimizer.Result optimization,
            List<Point> points,List<ForcePoint> forces,double rmse,double maximumError,double equilibrium,double wellDepth,
            double wellDepthError,double seedSpread,boolean deterministic,boolean smooth,boolean forceSigns,
            boolean sharedStatePassed,boolean forcePassed,String classification) {
        public Result{points=List.copyOf(points);forces=List.copyOf(forces);}
        String toJson(){return String.format(Locale.ROOT,"{\"scientific_identity\":\"%s\",\"protocol_sha256\":\"%s\",\"classification\":\"%s\",\"shared_state_passed\":%s,\"force_passed\":%s,\"rmse_hartree\":%.16g,\"max_error_hartree\":%.16g,\"equilibrium_bohr\":%.16g,\"well_depth_hartree\":%.16g,\"well_depth_error_hartree\":%.16g,\"seed_spread_hartree\":%.16g,\"deterministic_replay\":%s,\"optimizer_converged\":%s,\"iterations\":%d,\"objective_passes\":%d,\"state_evaluations\":%d,\"parameters\":\"%s\"}%n",identity,protocolSha,classification,sharedStatePassed,forcePassed,rmse,maximumError,equilibrium,wellDepth,wellDepthError,seedSpread,deterministic,optimization.converged(),optimization.iterations(),optimization.objectiveEvaluations(),optimization.stateEvaluations(),optimization.parameters().values());}
        String curveCsv(){StringBuilder out=new StringBuilder("R_bohr,reference_Ha,energy_Ha,error_Ha,variance_Ha2,independent_spread_Ha,virial_ratio,nuclear_cusp_error,electron_cusp_error,exchange_error,nuclear_interchange_error,gradient_error,laplacian_error\n");for(Point p:points)out.append(String.format(Locale.ROOT,"%.1f,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g%n",p.radius(),p.reference(),p.energy(),p.error(),p.variance(),p.spread(),p.virial(),p.audit().nuclearCuspMaxError(),p.audit().electronCuspError(),p.audit().electronExchangeError(),p.audit().nuclearExchangeError(),p.audit().gradientError(),p.audit().laplacianError()));return out.toString();}
        String forceCsv(){StringBuilder out=new StringBuilder("R_bohr,analytic_force_Ha_per_bohr,hellmann_feynman_force,pulay_force,force_variance,model_FD_force,reference_force,analytic_model_FD_error,reference_error,state_evaluations,redundant_evaluations\n");for(ForcePoint f:forces){var r=f.result();out.append(String.format(Locale.ROOT,"%.1f,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%d,%d%n",f.radius(),r.forceHartreePerBohr(),r.hellmannFeynmanForceHartreePerBohr(),r.pulayForceHartreePerBohr(),r.forceEstimatorVarianceHartree2PerBohr2(),f.modelFiniteDifferenceForce(),f.referenceForce(),f.modelFiniteDifferenceError(),f.referenceError(),r.stateEvaluations(),r.redundantStateEvaluations()));}return out.toString();}
        String performanceCsv(){double reduction=1-(double)optimization.objectiveEvaluations()/GEN2_OBJECTIVE_PASSES;return String.format(Locale.ROOT,"metric,value%nshared_parameters,20%nshared_objective_passes,%d%nstate_evaluations,%d%nwall_time_ns,%d%nmax_batch_size,%d%nshared_work_reduction_vs_gen2,%.16g%n",optimization.objectiveEvaluations(),optimization.stateEvaluations(),optimization.wallTimeNanos(),BATCH,reduction);}
        String report(){return String.format(Locale.ROOT,"# Shared-Geometry H2 and Native Nuclear-Force Final Report%n%nClassification: `%s`%n%n- Shared-state gate: %s%n- Nuclear-force gate: %s%n- Optimizer converged under locked rule: %s%n- RMSE: %.9f Ha%n- Maximum error: %.9f Ha%n- Equilibrium: %.9f bohr%n- Well-depth error: %.9f Ha%n- Seed spread: %.9f Ha%n- Deterministic replay: %s%n- Objective passes: %d%n- State evaluations: %d%n%nThis is a one-shot capability result, not H2 Generation-3. Frozen Gen-1/Gen-2 evidence remains unchanged. No LiH is authorized by this execution.%n",classification,sharedStatePassed,forcePassed,optimization.converged(),rmse,maximumError,equilibrium,wellDepthError,seedSpread,deterministic,optimization.objectiveEvaluations(),optimization.stateEvaluations());}
    }
}
