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

import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.ParameterVector;

/** One-shot correctness and cost gate for the locked exact finite-objective experiment. */
public final class ExactFiniteObjectivePreflight {
    private static final double[] RADII = {.8,1,1.2,1.4,1.6,2,3,4,6};
    private static final double[] REFERENCES = {-1.0200566663601389,-1.1245397195465791,-1.1649352434400281,
            -1.1744757142200755,-1.1685833733709263,-1.1381329571315035,-1.0573262688692439,
            -1.0163902529471283,-1.0008357076542279};
    private static final double[] FROZEN = {.8576772116910546,.11919655001255025,-.06709570692540537,
            .04370894911240642,-.32732397143757097,.21519667708138937,-.06386208428749664,
            .04232059707741613,.017563345336565027,-.12118637444956007,.11444052280585346,
            .26554487072063354,.19811737981250818,.07860098998305089,-.2778578205251936,
            -.16701609069702947,.07580798604963333,-.15755013283163458,.22812063643399538,
            -.1453261891402233};
    private static final double STEP = 2e-6, MAX_TOLERANCE = 3e-5, RMS_TOLERANCE = 1e-5;
    private static final long MAX_PROJECTED_NANOS = 30L * 60 * 1_000_000_000L;
    private static final long MAX_HEAP_BYTES = 1L << 30;
    private ExactFiniteObjectivePreflight() { }

    public static Result run() {
        ParameterVector parameters = vector(FROZEN); List<StateRow> states = stateAudit(parameters);
        var fixture = new ExactFiniteObjectiveDifferentiator(RADII, REFERENCES, 100);
        long auditStart = System.nanoTime(); var analytic = fixture.evaluate(parameters); List<GradientRow> gradients = new ArrayList<>();
        double maximum = 0, square = 0;
        for (int index = 0; index < FROZEN.length; index++) {
            double finite = (fixture.evaluate(perturb(parameters,index,STEP)).loss()
                    - fixture.evaluate(perturb(parameters,index,-STEP)).loss()) / (2*STEP);
            double error = Math.abs(finite-analytic.gradient()[index]); maximum=Math.max(maximum,error); square+=error*error;
            gradients.add(new GradientRow(index,analytic.gradient()[index],finite,error,error<=MAX_TOLERANCE));
        }
        long auditNanos=System.nanoTime()-auditStart; double rms=Math.sqrt(square/FROZEN.length);
        boolean statePass=states.stream().allMatch(StateRow::passes),gradientPass=maximum<=MAX_TOLERANCE&&rms<=RMS_TOLERANCE;
        long beforeHeap=usedHeap(),costStart=System.nanoTime();
        var full=new ExactFiniteObjectiveDifferentiator(RADII,REFERENCES,2500).evaluate(parameters);
        long passNanos=System.nanoTime()-costStart,peakHeap=Math.max(beforeHeap,usedHeap());
        long projected=passNanos*121L*4L;boolean costPass=projected<=MAX_PROJECTED_NANOS&&peakHeap<=MAX_HEAP_BYTES;
        String classification=!statePass||!gradientPass?"EXACT_FINITE_OBJECTIVE_CORRECTNESS_DEFECT":
                !costPass?"EXACT_FINITE_OBJECTIVE_COMPUTATIONALLY_PROHIBITIVE":"PREFLIGHT_PASSES_TRAINING_AUTHORIZED";
        return new Result(classification,statePass,gradientPass,costPass,maximum,rms,auditNanos,passNanos,projected,peakHeap,
                analytic.loss(),full.loss(),full.force(),List.copyOf(states),List.copyOf(gradients));
    }

    private static List<StateRow> stateAudit(ParameterVector parameters) {
        List<StateRow> rows=new ArrayList<>(); var exact=new ExactFiniteObjectiveDifferentiator(RADII,REFERENCES,100);
        for(double radius:new double[]{.8,1,1.4,3}) {List<totah.lab.prometheus.variational.CollocationPointSet.WeightedPoint> points=new ArrayList<>();
            new HydrogenMoleculeImportanceBatches(100,radius,1.15,43,100).forEachBatch(points::addAll);
            for(int index=0;index<6;index++){var coordinates=points.get(index).coordinates();var actual=exact.state(parameters,radius,coordinates);
                var expected=new GeometryConditionedHydrogenMoleculeState(radius,parameters).evaluateWithGeometryDerivatives(coordinates).stateEvaluation();
                double valueError=Math.abs(actual.value()-expected.value().real());double lapError=Math.abs(actual.laplacian()-expected.coordinateLaplacian().value().real());
                rows.add(new StateRow(radius,index,valueError,lapError,valueError<=1e-11&&lapError<=1e-11));}}
        return rows;
    }

    public static void main(String[] args) throws IOException {
        if(args.length!=2)throw new IllegalArgumentException("protocol directory and output directory required");
        Path protocol=Path.of(args[0]),output=Path.of(args[1]);Files.createDirectories(output);verifyProtocol(protocol);
        if(Files.exists(output.resolve("EXACT_FINITE_OBJECTIVE_PREFLIGHT_DECISION.json")))throw new IllegalStateException("one-shot evidence exists");
        Result result=run();write(output.resolve("EXACT_FINITE_OBJECTIVE_STATE_AUDIT.csv"),result.stateCsv());
        write(output.resolve("EXACT_FINITE_OBJECTIVE_GRADIENT_AUDIT.csv"),result.gradientCsv());
        write(output.resolve("EXACT_FINITE_OBJECTIVE_COST.csv"),result.costCsv());
        write(output.resolve("EXACT_FINITE_OBJECTIVE_PREFLIGHT_DECISION.json"),result.json());
        write(output.resolve("EXACT_FINITE_OBJECTIVE_PREFLIGHT_REPORT.md"),result.report());
        write(output.resolve("SOFTWARE_ENVIRONMENT.txt"),environment());
        checksums(output,List.of("EXACT_FINITE_OBJECTIVE_STATE_AUDIT.csv","EXACT_FINITE_OBJECTIVE_GRADIENT_AUDIT.csv",
                "EXACT_FINITE_OBJECTIVE_COST.csv","EXACT_FINITE_OBJECTIVE_PREFLIGHT_DECISION.json",
                "EXACT_FINITE_OBJECTIVE_PREFLIGHT_REPORT.md","SOFTWARE_ENVIRONMENT.txt"));
    }
    private static ParameterVector perturb(ParameterVector source,int index,double delta){List<Double> values=new ArrayList<>(source.values());values.set(index,values.get(index)+delta);return new ParameterVector(values);}
    private static ParameterVector vector(double[] source){List<Double> values=new ArrayList<>();for(double value:source)values.add(value);return new ParameterVector(values);}
    private static long usedHeap(){Runtime runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
    private static String environment(){return "java.version="+System.getProperty("java.version")+"\njava.vendor="+System.getProperty("java.vendor")+"\nos.name="+System.getProperty("os.name")+"\nos.arch="+System.getProperty("os.arch")+"\navailable.processors="+Runtime.getRuntime().availableProcessors()+"\n";}
    private static void verifyProtocol(Path directory)throws IOException{for(String line:Files.readAllLines(directory.resolve("PROTOCOL_SHA256"),StandardCharsets.UTF_8)){String[] fields=line.trim().split("\\s+",2);if(fields.length!=2||!sha(directory.resolve(fields[1])).equals(fields[0]))throw new IllegalStateException("protocol checksum mismatch");}}
    private static void write(Path path,String text)throws IOException{try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer bytes=StandardCharsets.UTF_8.encode(text);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}}
    private static void checksums(Path directory,List<String> names)throws IOException{StringBuilder out=new StringBuilder();for(String name:names)out.append(sha(directory.resolve(name))).append("  ").append(name).append('\n');write(directory.resolve("SHA256SUMS"),out.toString());}
    private static String sha(Path path)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))>=0)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}}

    record StateRow(double radius,int sample,double valueError,double laplacianError,boolean passes) { }
    record GradientRow(int parameter,double analytic,double finite,double error,boolean passes) { }
    public record Result(String classification,boolean stateGatePasses,boolean gradientGatePasses,boolean costGatePasses,
            double maximumGradientError,double rmsGradientError,long auditWallNanos,long fullPassWallNanos,long projectedWallNanos,
            long peakObservedHeapBytes,double fixtureLoss,double fullLoss,double fullForce,List<StateRow> states,List<GradientRow> gradients) {
        public Result{states=List.copyOf(states);gradients=List.copyOf(gradients);}
        String stateCsv(){StringBuilder out=new StringBuilder("radius_bohr,sample,value_error,laplacian_error,passes\n");for(var row:states)out.append(String.format(Locale.ROOT,"%.17g,%d,%.17g,%.17g,%s%n",row.radius,row.sample,row.valueError,row.laplacianError,row.passes));return out.toString();}
        String gradientCsv(){StringBuilder out=new StringBuilder("parameter_index,analytic_gradient,finite_difference_gradient,absolute_error,max_tolerance,passes\n");for(var row:gradients)out.append(String.format(Locale.ROOT,"%d,%.17g,%.17g,%.17g,%.17g,%s%n",row.parameter,row.analytic,row.finite,row.error,MAX_TOLERANCE,row.passes));return out.toString();}
        String costCsv(){return String.format(Locale.ROOT,"metric,value%nfixture_audit_wall_ns,%d%nfull_objective_gradient_pass_wall_ns,%d%nprojected_four_run_wall_ns,%d%nprojected_limit_ns,%d%npeak_observed_heap_bytes,%d%nheap_limit_bytes,%d%n",auditWallNanos,fullPassWallNanos,projectedWallNanos,MAX_PROJECTED_NANOS,peakObservedHeapBytes,MAX_HEAP_BYTES);}
        String json(){return String.format(Locale.ROOT,"{\"classification\":\"%s\",\"training_executed\":false,\"holdouts_opened\":false,\"state_gate_passes\":%s,\"gradient_gate_passes\":%s,\"cost_gate_passes\":%s,\"maximum_gradient_error\":%.17g,\"rms_gradient_error\":%.17g,\"full_pass_wall_ns\":%d,\"projected_wall_ns\":%d,\"peak_observed_heap_bytes\":%d,\"full_loss\":%.17g,\"full_force_hartree_per_bohr\":%.17g}%n",classification,stateGatePasses,gradientGatePasses,costGatePasses,maximumGradientError,rmsGradientError,fullPassWallNanos,projectedWallNanos,peakObservedHeapBytes,fullLoss,fullForce);}
        String report(){return String.format(Locale.ROOT,"# Exact Finite-Objective Preflight%n%nClassification: `%s`%n%nThe predecessor remains frozen. State equivalence: **%s**. Exact analytic/AD versus independent finite-difference gradient: **%s** (maximum `%.9g`, RMS `%.9g`). Cost feasibility: **%s**; one full pass took `%.3f s`, projecting `%.2f min` for the locked four-run budget, with peak observed heap `%.1f MiB`.%n%nTraining has not executed and holdouts remain sealed. Training is permitted only when this classification is `PREFLIGHT_PASSES_TRAINING_AUTHORIZED`.%n",classification,stateGatePasses?"PASS":"FAIL",gradientGatePasses?"PASS":"FAIL",maximumGradientError,rmsGradientError,costGatePasses?"PASS":"FAIL",fullPassWallNanos/1e9,projectedWallNanos/60e9,peakObservedHeapBytes/(1024.0*1024));}
    }
}
