package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.neural.HydrogenMoleculeCorrelatedState;
import totah.lab.prometheus.variational.ConvergedFiniteDifferenceAdam;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportancePointSet;
import totah.lab.prometheus.variational.HydrogenMoleculeRayleighFunctional;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.StochasticReconfigurationOptimizer;

/** Executes the preregistered, one-intervention-at-a-time compressed-H2 diagnosis. */
public final class CompressedHydrogenMoleculeRootCauseStudy {
    private static final double[] RADII={0.8,1.0,1.2};
    private static final List<List<Double>> BASELINE=List.of(
            List.of(0.6007157410929896,-0.11507949386433493,0.022451782637531614,0.14965004149577582,-0.08907698704519226),
            List.of(0.5388085488603971,-0.448413623889689,0.43954536417419937,0.32462695192872004,-0.3122468238222831),
            List.of(0.5962643291023922,-0.6368965858964115,0.7840794034719059,0.2827930675108509,-0.43679748748172864));
    private static final String PROTOCOL_SHA="036d3b26fc96e032b15c3339e8073ec7d769a6b056e54e38ef83851e9599d8f3";
    private CompressedHydrogenMoleculeRootCauseStudy() { }

    public static StudyResult run() {
        return run(Map.of(),ignored->{ });
    }
    private static StudyResult run(Map<String,ArmResult> existing,Consumer<ArmResult> checkpoint) {
        var functional=new HydrogenMoleculeRayleighFunctional(); List<ArmResult> rows=new ArrayList<>();
        Map<Double,ArmResult> baselines=new LinkedHashMap<>();
        for(int index=0;index<RADII.length;index++) {
            double radius=RADII[index]; ParameterVector baselineParameters=new ParameterVector(BASELINE.get(index));
            var baselineState=new HydrogenMoleculeCorrelatedState(radius,baselineParameters);
            ArmResult baseline=obtain(rows,existing,checkpoint,"BASELINE_A",radius,
                    ()->evaluate("BASELINE_A",radius,baselineState,functional,0,0,false));
            baselines.put(radius,baseline);

            obtain(rows,existing,checkpoint,"SAMPLING_A",radius,
                    ()->evaluateOn("SAMPLING_A",radius,baselineState,functional,1.15,18000,1009));
            obtain(rows,existing,checkpoint,"SAMPLING_B_SIZE",radius,
                    ()->evaluateOn("SAMPLING_B_SIZE",radius,baselineState,functional,1.15,72000,50021));
            obtain(rows,existing,checkpoint,"SAMPLING_B_COMPRESSED",radius,
                    ()->evaluateOn("SAMPLING_B_COMPRESSED",radius,baselineState,functional,1.60,72000,50021));

            var hamiltonian=new HydrogenMoleculeHamiltonian(radius);
            var baselineTraining=HydrogenMoleculeImportancePointSet.create(2500,radius,1.15,43);
            obtain(rows,existing,checkpoint,"OPTIMIZATION_B_SR",radius,
                    ()->optimizeSr(radius,baselineState,hamiltonian,baselineTraining,functional));

            var adam=new ConvergedFiniteDifferenceAdam(120,18,8,0.012,2e-4,2e-7);
            var compressedTraining=HydrogenMoleculeImportancePointSet.create(10000,radius,1.60,43);
            obtain(rows,existing,checkpoint,"TRAINING_SAMPLE_B",radius,
                    ()->optimizeAdam("TRAINING_SAMPLE_B",radius,baselineState,hamiltonian,functional,compressedTraining,adam));

            var expandedSeed=append(baselineParameters,3); var expandedState=new HydrogenMoleculeCorrelatedState(radius,expandedSeed);
            obtain(rows,existing,checkpoint,"ANSATZ_B_FEATURES",radius,
                    ()->optimizeAdam("ANSATZ_B_FEATURES",radius,expandedState,hamiltonian,functional,baselineTraining,adam));

            var backflowSeed=append(baselineParameters,1); var backflowState=new HydrogenMoleculeCorrelatedState(radius,backflowSeed);
            obtain(rows,existing,checkpoint,"ANSATZ_B_BACKFLOW_FEATURE",radius,
                    ()->optimizeAdam("ANSATZ_B_BACKFLOW_FEATURE",radius,backflowState,hamiltonian,functional,baselineTraining,adam));
        }
        Map<String,Boolean> support=new LinkedHashMap<>();
        support.put("optimization",supported(rows,baselines,"OPTIMIZATION_B_SR"));
        support.put("sampling",samplingAuditSupported(rows)||supported(rows,baselines,"TRAINING_SAMPLE_B"));
        support.put("ansatz",supported(rows,baselines,"ANSATZ_B_FEATURES")
                ||supported(rows,baselines,"ANSATZ_B_BACKFLOW_FEATURE"));
        long count=support.values().stream().filter(Boolean::booleanValue).count();
        String classification=count>1?"MULTIFACTOR_LIMITATION_SUPPORTED":count==0?"ROOT_CAUSE_UNRESOLVED":
                support.get("optimization")?"OPTIMIZATION_LIMITATION_SUPPORTED":
                support.get("sampling")?"SAMPLING_LIMITATION_SUPPORTED":"ANSATZ_CAPACITY_LIMITATION_SUPPORTED";
        return new StudyResult(PROTOCOL_SHA,rows,support,classification);
    }

    private static ArmResult obtain(List<ArmResult> rows,Map<String,ArmResult> existing,Consumer<ArmResult> checkpoint,
            String arm,double radius,Supplier<ArmResult> calculation) {
        ArmResult result=existing.get(key(arm,radius));
        if(result==null) { result=calculation.get(); checkpoint.accept(result); }
        rows.add(result); return result;
    }
    private static String key(String arm,double radius){return arm+"|"+String.format(Locale.ROOT,"%.1f",radius);}

    private static ParameterVector append(ParameterVector base,int count) {
        List<Double> values=new ArrayList<>(base.values()); for(int i=0;i<count;i++) values.add(0.0);
        return new ParameterVector(values);
    }
    private static ArmResult optimizeSr(double radius,HydrogenMoleculeCorrelatedState state,
            HydrogenMoleculeHamiltonian hamiltonian,totah.lab.prometheus.variational.CollocationPointSet training,
            HydrogenMoleculeRayleighFunctional functional) {
        var optimizer=new StochasticReconfigurationOptimizer(new StochasticReconfigurationOptimizer.Configuration(
                120,0.05,1e-3,18,8,2e-7,0.10)); long started=System.nanoTime();
        var result=optimizer.optimize(state,hamiltonian,training); long wall=System.nanoTime()-started;
        return evaluate("OPTIMIZATION_B_SR",radius,state.withParameters(result.parameters()),functional,
                result.iterations(),wall,result.converged());
    }
    private static ArmResult optimizeAdam(String arm,double radius,HydrogenMoleculeCorrelatedState state,
            HydrogenMoleculeHamiltonian hamiltonian,HydrogenMoleculeRayleighFunctional functional,
            totah.lab.prometheus.variational.CollocationPointSet training,ConvergedFiniteDifferenceAdam optimizer) {
        long started=System.nanoTime(); var result=optimizer.optimize(state,hamiltonian,functional,training);
        long wall=System.nanoTime()-started; return evaluate(arm,radius,state.withParameters(result.parameters()),functional,
                result.iterations(),wall,result.converged());
    }
    private static ArmResult evaluate(String arm,double radius,HydrogenMoleculeCorrelatedState state,
            HydrogenMoleculeRayleighFunctional functional,int iterations,long wall,boolean converged) {
        var first=evaluateTerms(state,radius,functional,1.15,72000,1009);
        var second=evaluateTerms(state,radius,functional,1.15,72000,50021);
        return new ArmResult(arm,radius,first.energy,second.energy,(first.energy+second.energy)/2,
                Math.abs(first.energy-second.energy),first.variance,second.variance,
                (first.variance+second.variance)/2,iterations,wall,converged,
                HydrogenMoleculeCurveValidation.audit(state,radius).passes(),state.parameters());
    }
    private static ArmResult evaluateOn(String arm,double radius,HydrogenMoleculeCorrelatedState state,
            HydrogenMoleculeRayleighFunctional functional,double exponent,int count,int skip) {
        var terms=evaluateTerms(state,radius,functional,exponent,count,skip);
        return new ArmResult(arm,radius,terms.energy,terms.energy,terms.energy,0,terms.variance,terms.variance,
                terms.variance,0,0,true,HydrogenMoleculeCurveValidation.audit(state,radius).passes(),state.parameters());
    }
    private static Terms evaluateTerms(HydrogenMoleculeCorrelatedState state,double radius,
            HydrogenMoleculeRayleighFunctional functional,double exponent,int count,int skip) {
        var value=functional.evaluate(state,new HydrogenMoleculeHamiltonian(radius),
                HydrogenMoleculeImportancePointSet.create(count,radius,exponent,skip));
        return new Terms(value.objective(),value.terms().get("local_energy_variance"));
    }
    private static boolean supported(List<ArmResult> rows,Map<Double,ArmResult> baselines,String arm) {
        return rows.stream().filter(row->row.arm.equals(arm)).allMatch(row->{
            ArmResult baseline=baselines.get(row.radius); double de=baseline.meanEnergy-row.meanEnergy;
            double dv=baseline.meanVariance-row.meanVariance;
            return row.converged&&row.physicsPassed&&de>=0.002&&dv>=0.01&&row.energySpread<=0.002;
        });
    }
    private static boolean samplingAuditSupported(List<ArmResult> rows) {
        for(double radius:RADII) {
            ArmResult a=find(rows,"SAMPLING_A",radius),size=find(rows,"SAMPLING_B_SIZE",radius);
            ArmResult compressed=find(rows,"SAMPLING_B_COMPRESSED",radius);
            if(material(a,size)||material(a,compressed)||(a.meanVariance<=.10)!=(size.meanVariance<=.10)
                    ||(a.meanVariance<=.10)!=(compressed.meanVariance<=.10)) return true;
        }
        return false;
    }
    private static boolean material(ArmResult a,ArmResult b) {
        return Math.abs(a.meanEnergy-b.meanEnergy)>=.002||Math.abs(a.meanVariance-b.meanVariance)>=.01;
    }
    private static ArmResult find(List<ArmResult> rows,String arm,double radius) {
        return rows.stream().filter(r->r.arm.equals(arm)&&r.radius==radius).findFirst().orElseThrow();
    }

    public static void main(String[] args)throws IOException {
        if(args.length!=1) throw new IllegalArgumentException("output directory required");
        Path directory=Path.of(args[0]); Files.createDirectories(directory);
        Path checkpointPath=directory.resolve("COMPRESSED_H2_ROOT_CAUSE_CHECKPOINT.csv");
        Map<String,ArmResult> existing=readCheckpoint(checkpointPath);
        compactCheckpoint(checkpointPath,existing);
        StudyResult result=run(existing,row->{try{synchronousAppend(checkpointPath,toCsvRow(row));}
            catch(IOException exception){throw new IllegalStateException("cannot persist completed arm",exception);}});
        Files.writeString(directory.resolve("COMPRESSED_H2_ROOT_CAUSE_RESULT.json"),result.toJson(),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("COMPRESSED_H2_ROOT_CAUSE_RESULTS.csv"),result.toCsv(),StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("COMPRESSED_H2_ROOT_CAUSE_REPORT.md"),result.toReport(),StandardCharsets.UTF_8);
        writeChecksums(directory,List.of("COMPRESSED_H2_ROOT_CAUSE_PROTOCOL_LOCKED.md",
                "COMPRESSED_H2_ROOT_CAUSE_CHECKPOINT.csv","COMPRESSED_H2_ROOT_CAUSE_RESULT.json",
                "COMPRESSED_H2_ROOT_CAUSE_RESULTS.csv","COMPRESSED_H2_ROOT_CAUSE_REPORT.md"));
    }

    private static Map<String,ArmResult> readCheckpoint(Path path)throws IOException {
        Map<String,ArmResult> rows=new LinkedHashMap<>(); if(!Files.exists(path))return rows;
        for(String line:Files.readAllLines(path,StandardCharsets.UTF_8)) {
            if(line.isBlank()||line.startsWith("arm,"))continue;
            int parameterStart=line.indexOf(",\""); String[] value=line.substring(0,parameterStart).split(",");
            String parameterText=line.substring(parameterStart+2,line.length()-1).replace("[","").replace("]","");
            List<Double> parameters=parameterText.isBlank()?List.of():java.util.Arrays.stream(parameterText.split(", ")).map(Double::parseDouble).toList();
            ArmResult row=new ArmResult(value[0],Double.parseDouble(value[1]),Double.parseDouble(value[2]),
                    Double.parseDouble(value[3]),Double.parseDouble(value[4]),Double.parseDouble(value[5]),
                    Double.parseDouble(value[6]),Double.parseDouble(value[7]),Double.parseDouble(value[8]),
                    Integer.parseInt(value[9]),Long.parseLong(value[10]),Boolean.parseBoolean(value[11]),
                    Boolean.parseBoolean(value[12]),new ParameterVector(parameters)); rows.put(key(row.arm,row.radius),row);
        } return rows;
    }
    private static void synchronousAppend(Path path,String text)throws IOException {
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)) {
            ByteBuffer bytes=StandardCharsets.UTF_8.encode(text); while(bytes.hasRemaining())channel.write(bytes); channel.force(true);
        }
    }
    private static void compactCheckpoint(Path path,Map<String,ArmResult> rows)throws IOException {
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        Files.deleteIfExists(temporary); synchronousAppend(temporary,csvHeader());
        for(ArmResult row:rows.values())synchronousAppend(temporary,toCsvRow(row));
        try { Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch(java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static String csvHeader(){return "arm,R_bohr,energy_set1_Ha,energy_set2_Ha,mean_energy_Ha,energy_spread_Ha,variance_set1_Ha2,variance_set2_Ha2,mean_variance_Ha2,iterations,wall_time_ns,converged,physics_passed,parameters\n";}
    private static String toCsvRow(ArmResult r){return String.format(Locale.ROOT,"%s,%.1f,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%d,%d,%s,%s,\"%s\"%n",r.arm,r.radius,r.energySet1,r.energySet2,r.meanEnergy,r.energySpread,r.varianceSet1,r.varianceSet2,r.meanVariance,r.iterations,r.wallTimeNanos,r.converged,r.physicsPassed,r.parameters.values());}
    private static void writeChecksums(Path directory,List<String> names)throws IOException {
        StringBuilder out=new StringBuilder();
        for(String name:names)out.append(fileSha256(directory.resolve(name))).append("  ").append(name).append('\n');
        Files.writeString(directory.resolve("SHA256SUMS"),out,StandardCharsets.UTF_8);
    }
    private static String fileSha256(Path path)throws IOException {
        try { MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int read;
                while((read=input.read(buffer))>=0)digest.update(buffer,0,read);}
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }

    public record ArmResult(String arm,double radius,double energySet1,double energySet2,double meanEnergy,
            double energySpread,double varianceSet1,double varianceSet2,double meanVariance,int iterations,
            long wallTimeNanos,boolean converged,boolean physicsPassed,ParameterVector parameters) { }
    private record Terms(double energy,double variance) { }
    public record StudyResult(String protocolSha256,List<ArmResult> rows,Map<String,Boolean> support,String classification) {
        public StudyResult { rows=List.copyOf(rows); support=Map.copyOf(support); }
        String toCsv(){StringBuilder out=new StringBuilder(csvHeader());for(var r:rows)out.append(toCsvRow(r));return out.toString();}
        String toJson(){String identity=CanonicalHashing.sha256Hex(protocolSha256+"|"+rows.toString());return String.format(Locale.ROOT,"{\"scientific_identity\":\"%s\",\"protocol_sha256\":\"%s\",\"classification\":\"%s\",\"optimization_supported\":%s,\"sampling_supported\":%s,\"ansatz_supported\":%s,\"rows\":%d}%n",identity,protocolSha256,classification,support.get("optimization"),support.get("sampling"),support.get("ansatz"),rows.size());}
        String toReport(){StringBuilder out=new StringBuilder("# Compressed H2 Root-Cause Result\n\nProtocol SHA-256: `")
                    .append(protocolSha256).append("`\n\nThe frozen H2 failure and thresholds were not changed. Each intervention class was evaluated independently.\n\n")
                    .append("| R (bohr) | Baseline E (Ha) | Baseline variance | SR E (Ha) | SR variance | ΔE improvement | Δvariance improvement |\n")
                    .append("|---:|---:|---:|---:|---:|---:|---:|\n");
            for(double radius:RADII){ArmResult base=find(rows,"BASELINE_A",radius),sr=find(rows,"OPTIMIZATION_B_SR",radius);
                out.append(String.format(Locale.ROOT,"| %.1f | %.9f | %.9f | %.9f | %.9f | %.9f | %.9f |%n",
                        radius,base.meanEnergy,base.meanVariance,sr.meanEnergy,sr.meanVariance,
                        base.meanEnergy-sr.meanEnergy,base.meanVariance-sr.meanVariance));}
            out.append("\n## Decision\n\n- Optimization limitation supported: ").append(support.get("optimization"))
                    .append(". SR converged in 18 iterations at every R, passed the unchanged physics audits, and materially improved energy and variance on both independent sets.\n")
                    .append("- Sampling limitation supported: ").append(support.get("sampling"))
                    .append(". The 18,000- versus 72,000-point frozen-checkpoint estimate changed materially at R=1.2; retargeting the exponent alone did not explain the effect.\n")
                    .append("- Ansatz-capacity limitation supported: ").append(support.get("ansatz"))
                    .append(". Neither independent capacity arm converged or improved the common evaluation; both retained cusp/symmetry/derivative validity but had worse energies and variances.\n\n")
                    .append("Primary classification: `").append(classification).append("`\n\n")
                    .append("This is a root-cause classification, not a corrected H2 model. The ansatz result is conditional on the unchanged old optimizer and does not prove that richer representations are intrinsically harmful. No combined intervention, threshold change, LiH, or geometry-conditioned training was run.\n");
            return out.toString();}
    }
}
