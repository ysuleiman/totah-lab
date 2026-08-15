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
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.HydrogenMoleculeNuclearForceEstimator;
import totah.lab.prometheus.variational.NonstationaryParameterResponseAudit;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.force.AssarafCaffarelZvForceEstimator;
import totah.lab.prometheus.variational.force.AssarafCaffarelZvzbForceEstimator;
import totah.lab.prometheus.variational.force.CorrelatedSamplingFiniteDifferenceForceEstimator;
import totah.lab.prometheus.variational.force.DirectHfPulayForceTrace;
import totah.lab.prometheus.variational.force.HydrogenMoleculeSpaceWarpForceEstimator;
import totah.lab.prometheus.variational.force.WeightedForceDiagnostics;

/** One-shot execution of the preregistered frozen-state nuclear-force estimator panel. */
public final class NuclearForceEstimatorCapabilityStudy {
    private static final double[] RADII={1,1.4,3};
    private static final double[] REFERENCES={.3621964426997232,.009120324827245340,-.06087135209218764};
    private static final double[] FROZEN_DIRECT={.3119796341184747,-.01693434536884336,-.07331633902930705};
    private static final ParameterVector PARAMETERS=new ParameterVector(List.of(.8576772116910546,.11919655001255025,
            -.06709570692540537,.04370894911240642,-.32732397143757097,.21519667708138937,
            -.06386208428749664,.04232059707741613,.017563345336565027,-.12118637444956007,
            .11444052280585346,.26554487072063354,.19811737981250818,.07860098998305089,
            -.2778578205251936,-.16701609069702947,.07580798604963333,-.15755013283163458,
            .22812063643399538,-.1453261891402233));
    private NuclearForceEstimatorCapabilityStudy(){ }

    public static StudyResult run(){List<Row> rows=new ArrayList<>();List<ResponseRow> responses=new ArrayList<>();
        boolean baselineReplay=true;for(int i=0;i<RADII.length;i++){double r=RADII[i],reference=REFERENCES[i];
            var state=new GeometryConditionedHydrogenMoleculeState(r,PARAMETERS);var h=new HydrogenMoleculeHamiltonian(r);
            var batches=new HydrogenMoleculeImportanceBatches(72000,r,1.15,1009,512);
            long start=System.nanoTime();List<DirectHfPulayForceTrace.LinearContribution> directTerms=new ArrayList<>();
            var directTrace=new DirectHfPulayForceTrace().evaluate(state,h,batches,directTerms::add);
            var direct=new HydrogenMoleculeNuclearForceEstimator().evaluate(state,h,batches);long directWall=System.nanoTime()-start;
            baselineReplay&=Double.doubleToLongBits(direct.forceHartreePerBohr())==Double.doubleToLongBits(FROZEN_DIRECT[i]);
            var directDiagnostic=linearDiagnostics(directTerms,directTrace.sampledMeanEnergyHartree(),false);
            rows.add(row("DIRECT_HF_PULAY_BASELINE",r,reference,direct.forceHartreePerBohr(),directDiagnostic,direct.forceEstimatorVarianceHartree2PerBohr2(),
                    directWall,direct.stateEvaluations()+directTrace.stateEvaluations(),direct.stateEvaluations()+directTrace.stateEvaluations(),0,72000,2,512,"RAW_ACCEPTANCE"));
            var bareDiagnostic=bareDiagnostics(directTerms);rows.add(row("BARE_HELLMANN_FEYNMAN",r,reference,directTrace.bareForceHartreePerBohr(),
                    bareDiagnostic,bareDiagnostic.raw().variance(),0,0,0,0,0,0,512,"REUSED_DIRECT_TRACE"));

            start=System.nanoTime();var correlated=new CorrelatedSamplingFiniteDifferenceForceEstimator().evaluate(state,batches);
            long correlatedWall=System.nanoTime()-start;var correlatedDiagnostic=equalDiagnostics(correlated.equalWeightPairedForceContributions());
            rows.add(row("CORRELATED_SAMPLING_FINITE_DIFFERENCE",r,reference,correlated.forceHartreePerBohr(),correlatedDiagnostic,
                    correlatedDiagnostic.raw().variance(),correlatedWall,correlated.stateEvaluations(),correlated.localEnergyEvaluations(),0,72000,1,correlated.peakBatchSize(),"INDEPENDENT_CONTROL"));

            start=System.nanoTime();List<HydrogenMoleculeSpaceWarpForceEstimator.LinearContribution> swctTerms=new ArrayList<>();
            var swct=new HydrogenMoleculeSpaceWarpForceEstimator().evaluate(state,h,batches,swctTerms::add);long swctWall=System.nanoTime()-start;
            var swctDiagnostic=swctDiagnostics(swctTerms,swct.energyHartree());rows.add(row("SPACE_WARP_COORDINATE_TRANSFORMATION",r,reference,
                    swct.forceHartreePerBohr(),swctDiagnostic,swct.forceEstimatorVarianceHartree2PerBohr2(),swctWall,swct.stateEvaluations(),
                    swct.localEnergyEvaluations(),swct.stateEvaluations(),72000,1,swct.peakBatchSize(),"QIAN_EQ_14_NUMERICAL_DERIVATIVE"));

            start=System.nanoTime();List<AssarafCaffarelZvForceEstimator.Contribution> zvTerms=new ArrayList<>();
            var zv=new AssarafCaffarelZvForceEstimator().evaluate(state,h,batches,1,zvTerms::add);long zvWall=System.nanoTime()-start;
            var zvDiagnostic=vectorDiagnostics(zvTerms);double zvForce=zv.rawStatistics().meanHartreePerBohr().z();
            rows.add(row("ASSARAF_CAFFAREL_ZV",r,reference,zvForce,zvDiagnostic,zv.rawStatistics().varianceHartree2PerBohr2().z(),zvWall,
                    zv.rawStatistics().stateEvaluations(),0,0,72000,1,zv.rawStatistics().peakBatchSize(),"QIAN_PRINTED_EQ_11"));

            start=System.nanoTime();List<AssarafCaffarelZvzbForceEstimator.LinearContribution> zvzbTerms=new ArrayList<>();
            var zvzb=new AssarafCaffarelZvzbForceEstimator().evaluate(state,h,batches,1,zvzbTerms::add);long zvzbWall=System.nanoTime()-start;
            var zvzbDiagnostic=zvzbDiagnostics(zvzbTerms,zvzb.sampledMeanEnergyHartree());double zvzbForce=zvzb.rawStatistics().meanHartreePerBohr().z();
            rows.add(row("ASSARAF_CAFFAREL_ZVZB",r,reference,zvzbForce,zvzbDiagnostic,zvzb.rawStatistics().varianceHartree2PerBohr2().z(),zvzbWall,
                    zvzb.rawStatistics().stateEvaluations(),zvzb.rawStatistics().stateEvaluations(),0,72000,1,zvzb.rawStatistics().peakBatchSize(),"QIAN_PRINTED_EQ_6"));

            start=System.nanoTime();var response=new NonstationaryParameterResponseAudit().evaluate(state,batches);long responseWall=System.nanoTime()-start;
            double correction=response.implicitResponseForceHartreePerBohr().orElse(Double.NaN);
            responses.add(new ResponseRow(r,response.classification().name(),response.gradientL2Norm(),response.gradientMaximumAbsolute(),
                    response.responseRank(),response.responseDimension(),response.pivotRatio(),correction,
                    Double.isFinite(correction)?direct.forceHartreePerBohr()+correction:Double.NaN,response.stateEvaluations(),responseWall));
        }
        boolean swctPass=passes(rows,"SPACE_WARP_COORDINATE_TRANSFORMATION");boolean zvzbPass=passes(rows,"ASSARAF_CAFFAREL_ZVZB");
        String hybridStatus=swctPass&&zvzbPass?"NOT_IMPLEMENTED_CORRECTNESS_DEFECT":"NOT_EVALUATED_LOCKED_PREREQUISITES_FAILED";
        String classification;if(!baselineReplay)classification="FROZEN_BASELINE_REPLAY_MISMATCH";
        else if(swctPass||zvzbPass)classification="LITERATURE_FORCE_ESTIMATOR_CAPABILITY_QUALIFIED";
        else if(varianceReduced(rows))classification="VARIANCE_REDUCED_BUT_FORCE_GATE_FAILS";
        else classification="NO_ESTIMATOR_RESOLVES_FROZEN_FORCE_FAILURE";
        return new StudyResult(List.copyOf(rows),List.copyOf(responses),baselineReplay,hybridStatus,classification);
    }

    private static Row row(String estimator,double r,double reference,double force,WeightedForceDiagnostics.Result diagnostics,
            double variance,long wall,long states,long local,long autodiff,long samples,long passes,int peak,String note){double error=force-reference;
        return new Row(estimator,r,force,reference,error,Math.abs(error),Math.abs(reference)>1e-12?error/reference:Double.NaN,
                variance,Math.sqrt(Math.max(0,variance)),diagnostics.raw().standardError(),diagnostics.raw().effectiveSampleSize(),
                diagnostics.clipped().mean(),diagnostics.clipped().variance(),diagnostics.clippedSamples(),diagnostics.lowerFence(),diagnostics.upperFence(),
                wall,states,local,autodiff,samples,passes,peak,variance*wall,error*error*wall,note);}
    private static WeightedForceDiagnostics.Result linearDiagnostics(List<DirectHfPulayForceTrace.LinearContribution> terms,double energy,boolean bare){
        List<WeightedForceDiagnostics.Sample> samples=new ArrayList<>(terms.size());for(var t:terms)samples.add(new WeightedForceDiagnostics.Sample(t.importanceWeight(),bare?t.bareForce():t.constantForce()+energy*t.sampledMeanEnergyCoefficient()));return WeightedForceDiagnostics.summarize(samples);}
    private static WeightedForceDiagnostics.Result bareDiagnostics(List<DirectHfPulayForceTrace.LinearContribution> terms){return linearDiagnostics(terms,0,true);}
    private static WeightedForceDiagnostics.Result equalDiagnostics(List<Double> terms){List<WeightedForceDiagnostics.Sample> samples=new ArrayList<>(terms.size());for(double value:terms)samples.add(new WeightedForceDiagnostics.Sample(1,value));return WeightedForceDiagnostics.summarize(samples);}
    private static WeightedForceDiagnostics.Result swctDiagnostics(List<HydrogenMoleculeSpaceWarpForceEstimator.LinearContribution> terms,double energy){List<WeightedForceDiagnostics.Sample> samples=new ArrayList<>(terms.size());for(var t:terms)samples.add(new WeightedForceDiagnostics.Sample(t.importanceWeight(),t.baseForceHartreePerBohr()+2*energy*t.sampledMeanEnergyCoefficientPerBohr()));return WeightedForceDiagnostics.summarize(samples);}
    private static WeightedForceDiagnostics.Result vectorDiagnostics(List<AssarafCaffarelZvForceEstimator.Contribution> terms){List<WeightedForceDiagnostics.Sample> samples=new ArrayList<>(terms.size());for(var t:terms)samples.add(new WeightedForceDiagnostics.Sample(t.importanceWeight(),t.forceHartreePerBohr().z()));return WeightedForceDiagnostics.summarize(samples);}
    private static WeightedForceDiagnostics.Result zvzbDiagnostics(List<AssarafCaffarelZvzbForceEstimator.LinearContribution> terms,double energy){List<WeightedForceDiagnostics.Sample> samples=new ArrayList<>(terms.size());for(var t:terms)samples.add(new WeightedForceDiagnostics.Sample(t.importanceWeight(),t.constant().z()+energy*t.sampledMeanEnergyCoefficient().z()));return WeightedForceDiagnostics.summarize(samples);}
    private static boolean passes(List<Row> rows,String estimator){return rows.stream().filter(r->r.estimator().equals(estimator)).count()==3
            &&rows.stream().filter(r->r.estimator().equals(estimator)).allMatch(r->r.absoluteError()<=.03&&Math.signum(r.force())==Math.signum(r.referenceForce()));}
    private static boolean varianceReduced(List<Row> rows){for(double r:RADII){double baseline=rows.stream().filter(x->x.radius()==r&&x.estimator().equals("DIRECT_HF_PULAY_BASELINE")).findFirst().orElseThrow().variance();
        if(rows.stream().anyMatch(x->x.radius()==r&&!x.estimator().equals("DIRECT_HF_PULAY_BASELINE")&&x.variance()<baseline))return true;}return false;}

    public static void main(String[] args)throws IOException{if(args.length!=1)throw new IllegalArgumentException("output directory required");Path directory=Path.of(args[0]);Files.createDirectories(directory);
        Path result=directory.resolve("NUCLEAR_FORCE_ESTIMATOR_RESULTS.csv");if(Files.exists(result))throw new IllegalStateException("one-shot results already exist");StudyResult study=run();
        synchronous(result,study.csv());synchronous(directory.resolve("PARAMETER_RESPONSE_AUDIT.csv"),study.responseCsv());
        synchronous(directory.resolve("NUCLEAR_FORCE_ESTIMATOR_COST.csv"),study.costCsv());synchronous(directory.resolve("NUCLEAR_FORCE_ESTIMATOR_PARETO.csv"),study.paretoCsv());
        synchronous(directory.resolve("NUCLEAR_FORCE_ESTIMATOR_DECISION.json"),study.json());synchronous(directory.resolve("NUCLEAR_FORCE_ESTIMATOR_FINAL_REPORT.md"),study.report());
        writeChecksums(directory,List.of("FROZEN_H2_FORCE_FAILURE.json","NUCLEAR_FORCE_ESTIMATOR_CAPABILITY_PROTOCOL_LOCKED.md","QIAN_2022_EQUATION_PROVENANCE.md",
                "NUCLEAR_FORCE_ESTIMATOR_RESULTS.csv","PARAMETER_RESPONSE_AUDIT.csv","NUCLEAR_FORCE_ESTIMATOR_COST.csv","NUCLEAR_FORCE_ESTIMATOR_PARETO.csv",
                "NUCLEAR_FORCE_ESTIMATOR_DECISION.json","NUCLEAR_FORCE_ESTIMATOR_FINAL_REPORT.md"));}
    private static void synchronous(Path path,String text)throws IOException{try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer bytes=StandardCharsets.UTF_8.encode(text);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}}
    private static void writeChecksums(Path directory,List<String> names)throws IOException{StringBuilder out=new StringBuilder();for(String name:names)out.append(sha(directory.resolve(name))).append("  ").append(name).append('\n');synchronous(directory.resolve("STUDY_SHA256SUMS"),out.toString());}
    private static String sha(Path path)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))>=0)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    public record Row(String estimator,double radius,double force,double referenceForce,double signedError,double absoluteError,double relativeError,
            double variance,double standardDeviation,double standardError,double effectiveSampleSize,double clippedForce,double clippedVariance,long clippedSamples,
            double lowerFence,double upperFence,long wallNanos,long stateEvaluations,long localEnergyEvaluations,long autodiffEvaluations,long samplerSteps,
            long estimatorPasses,int peakBatchSize,double varianceTimesCost,double errorSquaredTimesCost,String note){String csv(){return String.format(Locale.ROOT,
                    "%s,%.1f,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%d,%.16g,%.16g,%d,%d,%d,%d,%d,%d,%d,%.16g,%.16g,%s%n",estimator,radius,force,referenceForce,signedError,absoluteError,relativeError,variance,standardDeviation,standardError,effectiveSampleSize,clippedForce,clippedVariance,clippedSamples,lowerFence,upperFence,wallNanos,stateEvaluations,localEnergyEvaluations,autodiffEvaluations,samplerSteps,estimatorPasses,peakBatchSize,varianceTimesCost,errorSquaredTimesCost,note);}}
    public record ResponseRow(double radius,String classification,double gradientL2,double gradientMax,int rank,int dimension,double pivotRatio,double responseForce,double correctedForce,long stateEvaluations,long wallNanos){ }
    public record StudyResult(List<Row> rows,List<ResponseRow> responses,boolean baselineReplay,String hybridStatus,String classification){public StudyResult{rows=List.copyOf(rows);responses=List.copyOf(responses);}
        String csv(){StringBuilder out=new StringBuilder("estimator,R_bohr,raw_force,reference_force,signed_error,absolute_error,relative_error,raw_variance,raw_sd,raw_se,effective_sample_size,clipped_force,clipped_variance,clipped_samples,lower_fence,upper_fence,wall_ns,state_evaluations,local_energy_evaluations,autodiff_evaluations,sampler_steps,estimator_passes,peak_batch,variance_times_cost,error2_times_cost,note\n");rows.forEach(r->out.append(r.csv()));return out.toString();}
        String responseCsv(){StringBuilder out=new StringBuilder("R_bohr,classification,gradient_l2,gradient_max,rank,dimension,pivot_ratio,response_force,corrected_force,state_evaluations,wall_ns\n");for(var r:responses)out.append(String.format(Locale.ROOT,"%.1f,%s,%.16g,%.16g,%d,%d,%.16g,%.16g,%.16g,%d,%d%n",r.radius(),r.classification(),r.gradientL2(),r.gradientMax(),r.rank(),r.dimension(),r.pivotRatio(),r.responseForce(),r.correctedForce(),r.stateEvaluations(),r.wallNanos()));return out.toString();}
        String costCsv(){StringBuilder out=new StringBuilder("estimator,R_bohr,wall_ns,state_evaluations,local_energy_evaluations,autodiff_evaluations,sampler_steps,passes,peak_batch\n");for(var r:rows)out.append(String.format(Locale.ROOT,"%s,%.1f,%d,%d,%d,%d,%d,%d,%d%n",r.estimator(),r.radius(),r.wallNanos(),r.stateEvaluations(),r.localEnergyEvaluations(),r.autodiffEvaluations(),r.samplerSteps(),r.estimatorPasses(),r.peakBatchSize()));return out.toString();}
        String paretoCsv(){StringBuilder out=new StringBuilder("estimator,R_bohr,absolute_error,variance,wall_ns,variance_times_cost,error2_times_cost\n");for(var r:rows)out.append(String.format(Locale.ROOT,"%s,%.1f,%.16g,%.16g,%d,%.16g,%.16g%n",r.estimator(),r.radius(),r.absoluteError(),r.variance(),r.wallNanos(),r.varianceTimesCost(),r.errorSquaredTimesCost()));return out.toString();}
        String json(){return String.format(Locale.ROOT,"{\"classification\":\"%s\",\"baseline_replay\":%s,\"zvzb_swct_status\":\"%s\",\"wavefunction_changed\":false,\"thresholds_changed\":false}%n",classification,baselineReplay,hybridStatus);}
        String report(){Row accurate=rows.stream().min(java.util.Comparator.comparingDouble(Row::absoluteError)).orElseThrow();Row variance=rows.stream().min(java.util.Comparator.comparingDouble(Row::variance)).orElseThrow();Row cheap=rows.stream().filter(r->r.wallNanos()>0).min(java.util.Comparator.comparingLong(Row::wallNanos)).orElseThrow();Row trade=rows.stream().filter(r->r.wallNanos()>0).min(java.util.Comparator.comparingDouble(Row::errorSquaredTimesCost)).orElseThrow();return "# Nuclear-Force Estimator Capability Study\n\nClassification: `"+classification+"`\n\n- Frozen baseline replay: "+baselineReplay+"\n- Most accurate row: "+accurate.estimator()+" at R="+accurate.radius()+"\n- Lowest-variance row: "+variance.estimator()+" at R="+variance.radius()+"\n- Cheapest measured row: "+cheap.estimator()+" at R="+cheap.radius()+"\n- Best error-squared/cost row: "+trade.estimator()+" at R="+trade.radius()+"\n- ZVZB+SWCT: "+hybridStatus+"\n\nRaw statistics control classification. 3-IQR values are diagnostic only. The frozen shared wavefunction and thresholds were not changed.\n";}}
}
