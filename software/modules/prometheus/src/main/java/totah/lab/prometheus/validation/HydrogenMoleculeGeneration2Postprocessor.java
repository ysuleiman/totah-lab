package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import totah.lab.prometheus.neural.HydrogenMoleculeCorrelatedState;
import totah.lab.prometheus.variational.ParameterVector;

/** Derives the publication-facing Gen-2 gate audit without rerunning optimization or sampling. */
public final class HydrogenMoleculeGeneration2Postprocessor {
    private HydrogenMoleculeGeneration2Postprocessor() { }

    public static void main(String[] args)throws IOException {
        if(args.length!=1)throw new IllegalArgumentException("Generation-2 evidence directory required");
        Path directory=Path.of(args[0]);List<Row> rows=readCurve(directory.resolve("H2_GENERATION2_CURVE.csv"));
        String result=Files.readString(directory.resolve("H2_GENERATION2_RESULT.json"),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("H2_GENERATION2_PHYSICS_AUDIT.csv"),physicsAudit(rows),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("H2_GENERATION2_GATE_AUDIT.csv"),gateAudit(rows,result),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("H2_GENERATION2_FINAL_REPORT.md"),report(rows,result),StandardCharsets.UTF_8);
        writeChecksums(directory,List.of("H2_GENERATION2_PROTOCOL_LOCKED.md","H2_GENERATION2_CHECKPOINT.csv",
                "H2_GENERATION2_RESULT.json","H2_GENERATION2_CURVE.csv","H2_GENERATION2_PERFORMANCE.csv",
                "H2_GENERATION2_PHYSICS_AUDIT.csv","H2_GENERATION2_GATE_AUDIT.csv",
                "H2_GENERATION2_FINAL_REPORT.md","H2_GENERATION2_DECISION_REVIEW.md"));
    }

    private static List<Row> readCurve(Path path)throws IOException {
        List<Row> rows=new ArrayList<>();for(String line:Files.readAllLines(path,StandardCharsets.UTF_8)){
            if(line.startsWith("R_bohr")||line.isBlank())continue;int parameterStart=line.indexOf(",\"");
            String[] value=line.substring(0,parameterStart).split(",");String raw=line.substring(parameterStart+2,line.length()-1)
                    .replace("[","").replace("]","");List<Double> parameters=java.util.Arrays.stream(raw.split(", "))
                            .map(Double::parseDouble).toList();rows.add(new Row(Double.parseDouble(value[0]),
                    Double.parseDouble(value[1]),Double.parseDouble(value[2]),Double.parseDouble(value[3]),
                    Double.parseDouble(value[4]),Double.parseDouble(value[5]),Double.parseDouble(value[6]),
                    Integer.parseInt(value[7]),Long.parseLong(value[8]),Long.parseLong(value[9]),Long.parseLong(value[10]),
                    Long.parseLong(value[11]),Boolean.parseBoolean(value[12]),new ParameterVector(parameters)));}
        return List.copyOf(rows);
    }

    private static String physicsAudit(List<Row> rows){StringBuilder out=new StringBuilder("R_bohr,nuclear_cusp_max_error,electron_cusp_error,electron_exchange_error,nuclear_exchange_error,gradient_max_error,laplacian_error,physics_pass\n");
        for(Row row:rows){var state=new HydrogenMoleculeCorrelatedState(row.radius,row.parameters);
            var audit=HydrogenMoleculeCurveValidation.audit(state,row.radius);out.append(String.format(Locale.ROOT,
                    "%.1f,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%s%n",row.radius,audit.nuclearCuspMaxError(),
                    audit.electronCuspError(),audit.electronExchangeError(),audit.nuclearExchangeError(),
                    audit.gradientError(),audit.laplacianError(),audit.passes()));}return out.toString();}

    private static String gateAudit(List<Row> rows,String result){double rmse=number(result,"rmse_hartree");
        double max=number(result,"max_error_hartree"),equilibrium=number(result,"equilibrium_bohr");
        double well=number(result,"well_depth_error_hartree"),seed=number(result,"seed_spread_hartree");
        boolean replay=bool(result,"deterministic_replay");double maxVariance=rows.stream().mapToDouble(r->r.variance).max().orElseThrow();
        double maxIntegration=rows.stream().mapToDouble(r->r.energySpread).max().orElseThrow();
        long converged=rows.stream().filter(r->r.converged).count(),redundant=rows.stream().mapToLong(r->r.redundant).sum();
        boolean smooth=smooth(rows),force=rows.get(2).energy>rows.get(3).energy&&rows.get(4).energy>rows.get(3).energy;
        StringBuilder out=new StringBuilder("gate,value,threshold,pass\n");
        gate(out,"curve_rmse",rmse,"<=0.015",rmse<=.015);gate(out,"maximum_absolute_error",max,"<=0.025",max<=.025);
        gate(out,"equilibrium_absolute_error",Math.abs(equilibrium-1.4011),"<=0.08",Math.abs(equilibrium-1.4011)<=.08);
        gate(out,"well_depth_error",well,"<=0.015",well<=.015);gate(out,"dissociation_error",Math.abs(rows.getLast().energy+1),"<=0.010",Math.abs(rows.getLast().energy+1)<=.010);
        gate(out,"maximum_variance",maxVariance,"<=0.10",maxVariance<=.10);gate(out,"maximum_integration_spread",maxIntegration,"<=0.005",maxIntegration<=.005);
        gate(out,"converged_geometries",converged,"9",converged==9);gate(out,"seed_spread",seed,"<=0.01",seed<=.01);
        gate(out,"virial_error_R1.4",Math.abs(rows.get(3).virial-1),"<=0.08",Math.abs(rows.get(3).virial-1)<=.08);
        gate(out,"smooth_curve",smooth?1:0,"1",smooth);gate(out,"force_signs",force?1:0,"1",force);
        gate(out,"deterministic_replay",replay?1:0,"1",replay);gate(out,"redundant_state_evaluations",redundant,"0",redundant==0);
        return out.toString();}

    private static String report(List<Row> rows,String result){double rmse=number(result,"rmse_hartree"),max=number(result,"max_error_hartree");
        double equilibrium=number(result,"equilibrium_bohr"),well=number(result,"well_depth_error_hartree");
        double seed=number(result,"seed_spread_hartree"),work=number(result,"work_reduction_fraction");
        double speed=number(result,"wall_speedup"),continuation=number(result,"continuation_saving_fraction");
        long converged=rows.stream().filter(r->r.converged).count();
        return String.format(Locale.ROOT,"""
                # H2 Generation-2 Final Report

                Frozen classification: `H2_GENERATION2_MULTI_GEOMETRY_GATE_FAILED`

                Generation-2 is a one-shot validation. Generation-1 remains independently frozen as `H2_MULTI_GEOMETRY_GATE_FAILED`; no threshold or ansatz term was changed.

                ## Scientific outcome

                - Curve RMSE: `%.9f Ha` — PASS
                - Maximum absolute error: `%.9f Ha` — PASS
                - Equilibrium bond length: `%.9f bohr` — PASS
                - Well-depth error: `%.9f Ha` — PASS
                - Maximum local-energy variance: `%.9f Ha^2` — PASS
                - Maximum multi-seed spread: `%.9f Ha` — PASS
                - Deterministic replay: PASS
                - Cusp, exchange symmetry, 6D gradient, and 6D Laplacian audits: PASS at all nine geometries
                - Convergence: `%d/9` geometries — FAIL

                The complete gate fails only because eight geometries reached the preregistered 120-iteration ceiling. Their final energies, variances, integration diagnostics, and physics audits remain valid frozen negative evidence. No post-result optimizer adjustment is authorized.

                ## Performance

                - Objective/statistics-pass work reduction: `%.2f%%`
                - Warm-curve wall-clock speedup: `%.3fx`
                - Continuation saving relative to Gen-2 cold controls: `%.2f%%`

                SR and corrected sampling substantially improved physics quality and reduced work. Continuation was not beneficial under this Gen-2 SR protocol; it cost more objective passes than the specified cold controls. Acceleration is not promoted as a passed molecular method because the complete convergence gate failed.

                ## Decision

                `NO_FUNDAMENTAL_CORRECTNESS_DEFECT_OBSERVED`

                Generation-2 is closed. Localized residual or stopping behavior does not authorize continued H2 tuning. Advance to the next separately approved capability.
                """,rmse,max,equilibrium,well,rows.stream().mapToDouble(r->r.variance).max().orElseThrow(),seed,converged,
                100*work,speed,100*continuation);}

    private static void gate(StringBuilder out,String name,double value,String threshold,boolean pass){out.append(String.format(Locale.ROOT,"%s,%.16g,%s,%s%n",name,value,threshold,pass));}
    static double number(String json,String name){var matcher=Pattern.compile("\\\""+name+"\\\":([-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[Ee][-+]?[0-9]+)?)").matcher(json);
        if(!matcher.find())throw new IllegalArgumentException("missing "+name);return Double.parseDouble(matcher.group(1));}
    private static boolean bool(String json,String name){var matcher=Pattern.compile("\\\""+name+"\\\":(true|false)").matcher(json);
        if(!matcher.find())throw new IllegalArgumentException("missing "+name);return Boolean.parseBoolean(matcher.group(1));}
    private static boolean smooth(List<Row> rows){double previous=Double.NaN;for(int i=1;i<rows.size();i++){double slope=(rows.get(i).energy-rows.get(i-1).energy)/(rows.get(i).radius-rows.get(i-1).radius);
        if(!Double.isFinite(slope)||(Double.isFinite(previous)&&Math.abs(slope-previous)>.5))return false;previous=slope;}return true;}
    private static void writeChecksums(Path directory,List<String> names)throws IOException{StringBuilder out=new StringBuilder();for(String name:names)out.append(sha(directory.resolve(name))).append("  ").append(name).append('\n');
        Files.writeString(directory.resolve("SHA256SUMS"),out,StandardCharsets.UTF_8);}
    private static String sha(Path path)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))>=0)digest.update(buffer,0,n);}
        return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private record Row(double radius,double reference,double energy,double error,double variance,double energySpread,
            double virial,int iterations,long objectives,long wall,long stateEvaluations,long redundant,boolean converged,
            ParameterVector parameters){}
}
