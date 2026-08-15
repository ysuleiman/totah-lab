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

import totah.lab.prometheus.evidence.ScientificCapabilityClass;
import totah.lab.prometheus.variational.ParameterVector;

/** Executes only the locked correctness preflight; training is forbidden if it fails. */
public final class DerivativeAwarePesObjectivePreflight {
    private static final double[] RADII={.8,1,1.2,1.4,1.6,2,3,4,6};
    private static final double[] REFERENCES={-1.0200566663601389,-1.1245397195465791,-1.1649352434400281,
            -1.1744757142200755,-1.1685833733709263,-1.1381329571315035,-1.0573262688692439,
            -1.0163902529471283,-1.0008357076542279};
    private static final double[] FROZEN_PARAMETERS={.8576772116910546,.11919655001255025,-.06709570692540537,
            .04370894911240642,-.32732397143757097,.21519667708138937,-.06386208428749664,
            .04232059707741613,.017563345336565027,-.12118637444956007,.11444052280585346,
            .26554487072063354,.19811737981250818,.07860098998305089,-.2778578205251936,
            -.16701609069702947,.07580798604963333,-.15755013283163458,.22812063643399538,
            -.1453261891402233};
    private static final double AUDIT_STEP=2e-6,AUDIT_TOLERANCE=3e-5;
    private DerivativeAwarePesObjectivePreflight() { }

    public static Result run() {
        var optimizer=new DerivativeAwarePesDiagnosticOptimizer(
                new DerivativeAwarePesDiagnosticOptimizer.Configuration(120,18,8,.05,1e-3,
                        1.3333333333333333e-5,.10,2500),RADII,REFERENCES);
        ParameterVector parameters=vector(FROZEN_PARAMETERS);long start=System.nanoTime();
        var analytic=optimizer.objectiveEvaluation(parameters);List<Row> rows=new ArrayList<>();
        long stateEvaluations=analytic.stateEvaluations(),localEnergyEvaluations=analytic.localEnergyEvaluations();
        double maximum=0,sumSquares=0;
        for(int index=0;index<FROZEN_PARAMETERS.length;index++) {
            var plus=optimizer.objectiveEvaluation(perturb(parameters,index,AUDIT_STEP));
            var minus=optimizer.objectiveEvaluation(perturb(parameters,index,-AUDIT_STEP));
            stateEvaluations+=plus.stateEvaluations()+minus.stateEvaluations();
            localEnergyEvaluations+=plus.localEnergyEvaluations()+minus.localEnergyEvaluations();
            double finite=(plus.loss()-minus.loss())/(2*AUDIT_STEP);
            double error=Math.abs(finite-analytic.rawGradient()[index]);maximum=Math.max(maximum,error);sumSquares+=error*error;
            rows.add(new Row(index,analytic.rawGradient()[index],finite,error,error<=AUDIT_TOLERANCE));
        }
        double rms=Math.sqrt(sumSquares/FROZEN_PARAMETERS.length);boolean passes=maximum<=AUDIT_TOLERANCE;
        String classification=passes?"PREFLIGHT_PASSES_TRAINING_AUTHORIZED":"DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT";
        return new Result(classification,ScientificCapabilityClass.REFERENCE_ASSISTED_DIAGNOSTIC,passes,
                analytic.loss(),analytic.force(),maximum,rms,stateEvaluations,localEnergyEvaluations,
                1+2L*FROZEN_PARAMETERS.length,System.nanoTime()-start,List.copyOf(rows));
    }

    public static void main(String[] args)throws IOException {
        if(args.length!=2)throw new IllegalArgumentException("protocol directory and output directory required");
        Path protocolDirectory=Path.of(args[0]),output=Path.of(args[1]);Files.createDirectories(output);
        if(Files.exists(output.resolve("DERIVATIVE_AWARE_PES_DECISION.json")))throw new IllegalStateException("one-shot evidence exists");
        verifyProtocol(protocolDirectory);Result result=run();
        synchronousWrite(output.resolve("DERIVATIVE_AWARE_GRADIENT_AUDIT.csv"),result.auditCsv());
        synchronousWrite(output.resolve("DERIVATIVE_AWARE_PES_DECISION.json"),result.json());
        synchronousWrite(output.resolve("DERIVATIVE_AWARE_PES_FINAL_REPORT.md"),result.report());
        synchronousWrite(output.resolve("DERIVATIVE_AWARE_PES_COST.csv"),result.costCsv());
        synchronousWrite(output.resolve("SOFTWARE_ENVIRONMENT.txt"),environment());
        writeChecksums(output,List.of("DERIVATIVE_AWARE_GRADIENT_AUDIT.csv","DERIVATIVE_AWARE_PES_DECISION.json",
                "DERIVATIVE_AWARE_PES_FINAL_REPORT.md","DERIVATIVE_AWARE_PES_COST.csv","SOFTWARE_ENVIRONMENT.txt"));
    }

    private static void verifyProtocol(Path directory)throws IOException {
        List<String> lines=Files.readAllLines(directory.resolve("PROTOCOL_SHA256"),StandardCharsets.UTF_8);
        for(String line:lines){String[] fields=line.trim().split("\\s+",2);if(fields.length!=2||!sha(directory.resolve(fields[1])).equals(fields[0]))
            throw new IllegalStateException("locked protocol checksum mismatch: "+line);}
    }
    private static ParameterVector perturb(ParameterVector source,int index,double delta){List<Double> values=new ArrayList<>(source.values());
        values.set(index,values.get(index)+delta);return new ParameterVector(values);}
    private static ParameterVector vector(double[] values){List<Double> boxed=new ArrayList<>(values.length);for(double value:values)boxed.add(value);return new ParameterVector(boxed);}
    private static String environment(){return "java.version="+System.getProperty("java.version")+"\njava.vendor="+System.getProperty("java.vendor")
            +"\nos.name="+System.getProperty("os.name")+"\nos.arch="+System.getProperty("os.arch")+"\navailable.processors="+Runtime.getRuntime().availableProcessors()+"\n";}
    private static void synchronousWrite(Path path,String text)throws IOException{try(FileChannel channel=FileChannel.open(path,
            StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer bytes=StandardCharsets.UTF_8.encode(text);
        while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}}
    private static void writeChecksums(Path directory,List<String> names)throws IOException{StringBuilder out=new StringBuilder();
        for(String name:names)out.append(sha(directory.resolve(name))).append("  ").append(name).append('\n');
        synchronousWrite(directory.resolve("SHA256SUMS"),out.toString());}
    private static String sha(Path path)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))>=0)digest.update(buffer,0,n);}
        return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    record Row(int parameterIndex,double covarianceGradient,double finiteDifferenceGradient,double absoluteError,boolean passes) { }
    public record Result(String classification,ScientificCapabilityClass capabilityClass,boolean gradientGatePasses,
            double diagnosticLoss,double diagnosticForce,double maximumGradientError,double rmsGradientError,
            long stateEvaluations,long localEnergyEvaluations,long objectiveEvaluations,long wallTimeNanos,List<Row> rows) {
        public Result{rows=List.copyOf(rows);}
        String auditCsv(){StringBuilder out=new StringBuilder("parameter_index,covariance_gradient,finite_difference_gradient,absolute_error,tolerance,passes\n");
            for(Row row:rows)out.append(String.format(Locale.ROOT,"%d,%.17g,%.17g,%.17g,%.17g,%s%n",row.parameterIndex(),row.covarianceGradient(),row.finiteDifferenceGradient(),row.absoluteError(),AUDIT_TOLERANCE,row.passes()));return out.toString();}
        String json(){return String.format(Locale.ROOT,"{\"classification\":\"%s\",\"capability_class\":\"%s\",\"production_ab_initio_evidence_eligible\":false,\"gradient_gate_passes\":%s,\"diagnostic_loss\":%.17g,\"diagnostic_force_hartree_per_bohr\":%.17g,\"maximum_gradient_error\":%.17g,\"rms_gradient_error\":%.17g,\"training_executed\":false,\"holdouts_opened\":false,\"state_evaluations\":%d,\"local_energy_evaluations\":%d,\"objective_evaluations\":%d,\"wall_time_ns\":%d}%n",classification,capabilityClass,gradientGatePasses,diagnosticLoss,diagnosticForce,maximumGradientError,rmsGradientError,stateEvaluations,localEnergyEvaluations,objectiveEvaluations,wallTimeNanos);}
        String costCsv(){return String.format(Locale.ROOT,"metric,value%nobjective_evaluations,%d%nstate_evaluations,%d%nlocal_energy_evaluations,%d%nforce_derivative_evaluations,0%ndisplaced_energy_evaluations,%d%nwall_time_ns,%d%ntraining_iterations,0%ntraining_sample_count,0%n",objectiveEvaluations,stateEvaluations,localEnergyEvaluations,stateEvaluations*2/11,wallTimeNanos);}
        String report(){return String.format(Locale.ROOT,"# Derivative-Aware PES Diagnostic Final Report%n%nClassification: `%s`%n%nCapability class: `%s` (not production-ab-initio eligible).%n%nThe locked pre-training correctness gate failed: the VMC covariance RHS is an expectation-level energy derivative estimator, but it is not the exact derivative of the finite deterministic quadrature loss used by the reference-assisted diagnostic. Maximum component mismatch was `%.9g`, versus the locked `3e-5` gate; RMS mismatch was `%.9g`.%n%nTraining was not executed, parameters were not changed, derivative holdouts were not opened, and no energy/force/PES claim is made for a candidate model. The negative result is preserved. A new experiment would require separately preregistered mathematics that either differentiates the finite objective exactly or uses a statistically valid gradient-equivalence gate; this run cannot be repaired post hoc.%n",classification,capabilityClass,maximumGradientError,rmsGradientError);}
    }
}
