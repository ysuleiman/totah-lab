package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.execution.EvidenceExecutionException;
import totah.lab.prometheus.execution.quantum.GeneralMolecularExecutionRequest;
import totah.lab.prometheus.execution.quantum.JavaNeuralQuantumBackend;
import totah.lab.prometheus.execution.quantum.JavaNeuralQuantumRuntime;
import totah.lab.prometheus.execution.quantum.JavaNeuralRuntimePolicy;
import totah.lab.prometheus.execution.quantum.QuantumBackend;
import totah.lab.prometheus.execution.quantum.QuantumBackendCapabilities;
import totah.lab.prometheus.execution.quantum.QuantumBackendSelector;
import totah.lab.prometheus.execution.quantum.QuantumExecutionOptions;
import totah.lab.prometheus.execution.quantum.QuantumExecutionRequest;
import totah.lab.prometheus.execution.quantum.QuantumExecutionService;
import totah.lab.prometheus.execution.quantum.QuantumObservable;
import totah.lab.prometheus.execution.quantum.QuantumResult;
import totah.lab.prometheus.execution.quantum.QuantumSolverMode;
import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;
import totah.lab.prometheus.ingest.authoritative.CartesianGeometry;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;

/** Executes and reports the checksum-locked three-geometry Step-3 H2O gate. */
public final class WaterMoleculeStep3Validation {
    private static final double ANGSTROM_TO_BOHR=1.8897261254578281;
    private static final List<GeometryChoice> GEOMETRIES=List.of(new GeometryChoice("EQ",.95,.95,105),new GeometryChoice("COMPRESSED",.85,.95,100),new GeometryChoice("STRETCHED",.95,1.10,115));

    public Summary run(Path referenceFile,Path outputDirectory)throws IOException {
        Files.createDirectories(outputDirectory);Path work=outputDirectory.resolve("evidence");Path registryPath=outputDirectory.resolve("registry");
        H2o13ReferenceReader reader=new H2o13ReferenceReader();AtomicInteger calls=new AtomicInteger();QuantumBackend delegate=new JavaNeuralQuantumBackend();
        QuantumBackend counted=new QuantumBackend(){public String backendId(){return delegate.backendId();}public QuantumBackendCapabilities capabilities(){return delegate.capabilities();}public boolean supports(QuantumExecutionRequest request){return delegate.supports(request);}public QuantumResult execute(QuantumExecutionRequest request)throws EvidenceExecutionException{calls.incrementAndGet();return delegate.execute(request);}};
        var runtime=new JavaNeuralQuantumRuntime(new QuantumExecutionService(new QuantumBackendSelector(List.of(counted))),new GeneratedEvidenceRegistry(registryPath));
        List<Row> rows=new ArrayList<>();List<String> failures=new ArrayList<>();boolean reuse=true,exactSign=true;
        for(GeometryChoice choice:GEOMETRIES){var reference=reader.read(referenceFile,choice.oh1,choice.oh2,choice.angle);Molecule molecule=molecule(reference);QuantumExecutionRequest request=request(molecule,reference,work.resolve(choice.id));
            try{int before=calls.get();var first=runtime.executeOrReuse(request);if(first.generatedResult().isEmpty()){failures.add(choice.id+": pre-existing record cannot supply current-run instrumentation");continue;}QuantumResult result=first.generatedResult().orElseThrow();int after=calls.get();var second=runtime.executeOrReuse(request);reuse&=after==calls.get()&&second.disposition()==JavaNeuralQuantumRuntime.Disposition.REUSE_EXISTING;exactSign&=result.forceIsNegativeGradient(0);rows.add(row(choice,reference,result,first.disposition().name(),after-before));}
            catch(Exception e){failures.add(choice.id+": "+e.getClass().getSimpleName()+": "+e.getMessage());}
        }
        Summary summary=summarize(rows,failures,reuse,exactSign,calls.get());writeCsv(outputDirectory.resolve("STEP_3_H2O_RESULTS.csv"),rows);writeDecision(outputDirectory.resolve("STEP_3_DECISION.json"),summary);writeReport(outputDirectory.resolve("STEP_3_MULTI_NUCLEAR_VALIDATION_REPORT.md"),summary,rows);return summary;
    }

    private static Row row(GeometryChoice choice,H2o13ReferenceReader.Reference reference,QuantumResult result,String disposition,int calls){double energy=result.energy().orElseThrow().value();List<QuantumResult.Vector3> force=result.force().orElseThrow().vectors();List<Component> components=new ArrayList<>();double sumSquared=0,max=0,normError=0,maxSe=0;for(int i=0;i<3;i++){var calculated=force.get(i);var expected=reference.atoms().get(i);double[] c={calculated.x(),calculated.y(),calculated.z()},r={expected.fxHartreePerBohr(),expected.fyHartreePerBohr(),expected.fzHartreePerBohr()};for(int axis=0;axis<3;axis++){double error=c[axis]-r[axis],se=Double.parseDouble(result.executionProvenance().get("force_se_"+i+"_"+"xyz".charAt(axis)));components.add(new Component(i,"xyz".charAt(axis),c[axis],r[axis],error,se));sumSquared+=error*error;max=Math.max(max,Math.abs(error));maxSe=Math.max(maxSe,se);}double dx=calculated.x()-expected.fxHartreePerBohr(),dy=calculated.y()-expected.fyHartreePerBohr(),dz=calculated.z()-expected.fzHartreePerBohr();normError=Math.max(normError,Math.sqrt(dx*dx+dy*dy+dz*dz));}double tx=force.stream().mapToDouble(QuantumResult.Vector3::x).sum(),ty=force.stream().mapToDouble(QuantumResult.Vector3::y).sum(),tz=force.stream().mapToDouble(QuantumResult.Vector3::z).sum();double translation=Math.sqrt(tx*tx+ty*ty+tz*tz),maxZ=force.stream().mapToDouble(v->Math.abs(v.z())).max().orElseThrow();double permutation="EQ".equals(choice.id)?Math.abs(norm(force.get(1))-norm(force.get(2))):0;Map<String,String> p=result.executionProvenance();return new Row(choice.id,reference.headerLine(),energy,reference.absoluteEnergyHartree(),energy-reference.absoluteEnergyHartree(),Double.parseDouble(p.get("energy_block_variance")),Double.parseDouble(p.get("energy_standard_error")),List.copyOf(components),Math.sqrt(sumSquared/9),max,normError,maxSe,translation,maxZ,permutation,p.get("sr_relative_true_residuals"),Long.parseLong(p.get("optimization_state_evaluations")),Long.parseLong(p.get("force_state_traversals")),Long.parseLong(p.get("directional_ad_passes")),Long.parseLong(p.get("wall_time_nanos")),Long.parseLong(p.get("peak_heap_growth_bytes")),disposition,calls);}
    private static Summary summarize(List<Row> rows,List<String> failures,boolean reuse,boolean exactSign,int calls){double energyRmse=Math.sqrt(rows.stream().mapToDouble(r->r.energyError*r.energyError).average().orElse(Double.NaN));double forceRmse=Math.sqrt(rows.stream().flatMap(r->r.components.stream()).mapToDouble(c->c.error*c.error).average().orElse(Double.NaN));double maxEnergy=rows.stream().mapToDouble(r->Math.abs(r.energyError)).max().orElse(Double.NaN),maxForce=rows.stream().mapToDouble(Row::maxForceComponentError).max().orElse(Double.NaN),maxEnergySe=rows.stream().mapToDouble(Row::energyStandardError).max().orElse(Double.NaN),maxForceSe=rows.stream().mapToDouble(Row::maxForceStandardError).max().orElse(Double.NaN),maxTranslation=rows.stream().mapToDouble(Row::translationForceNorm).max().orElse(Double.NaN),maxZ=rows.stream().mapToDouble(Row::maxPlanarZForce).max().orElse(Double.NaN),permutation=rows.stream().mapToDouble(Row::hydrogenPermutationError).max().orElse(Double.NaN);long peakHeap=rows.stream().mapToLong(Row::peakHeapGrowthBytes).max().orElse(0);boolean sr=rows.size()==3&&rows.stream().allMatch(r->parseNumbers(r.srResiduals).stream().allMatch(x->x<=1e-10));boolean passed=failures.isEmpty()&&rows.size()==3&&maxEnergy<=.015&&energyRmse<=.010&&forceRmse<=.010&&maxForce<=.025&&maxEnergySe<=.005&&maxForceSe<=.010&&exactSign&&maxTranslation<=.005&&maxZ<=.005&&permutation<=.005&&sr&&reuse&&peakHeap<512L*1024*1024;String blocker=passed?"NONE":dominant(rows,failures,energyRmse,forceRmse,maxEnergySe,maxForceSe,sr,reuse,peakHeap);return new Summary(passed?"STEP_3_MULTI_NUCLEAR_VALIDATION_PASSED":"STEP_3_MULTI_NUCLEAR_VALIDATION_FAILED",blocker,rows.size(),energyRmse,maxEnergy,forceRmse,maxForce,maxEnergySe,maxForceSe,maxTranslation,maxZ,permutation,sr,rows.size()==3&&exactSign,rows.size()==3&&reuse,peakHeap,calls,List.copyOf(failures));}
    private static String dominant(List<Row> rows,List<String> failures,double energyRmse,double forceRmse,double energySe,double forceSe,boolean sr,boolean reuse,long heap){if(!failures.isEmpty())return failures.stream().anyMatch(x->x.contains("OutOfMemory")||x.contains("heap"))?"numerical_scaling":"optimization_convergence";if(!sr)return "optimization_convergence";if(energySe>.005||forceSe>.010)return "sampling_variance";if(energyRmse>.010)return "energy_accuracy";if(forceRmse>.010)return "force_accuracy";if(!reuse)return "runtime/evidence_wiring";if(heap>=512L*1024*1024)return "numerical_scaling";return "symmetry/correctness";}
    private static List<Double> parseNumbers(String value){String body=value.substring(1,value.length()-1).trim();if(body.isEmpty())return List.of();return java.util.Arrays.stream(body.split(",")).map(String::trim).map(Double::parseDouble).toList();}
    private static double norm(QuantumResult.Vector3 v){return Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z());}

    private static Molecule molecule(H2o13ReferenceReader.Reference reference){List<NuclearCenter> nuclei=new ArrayList<>();for(int i=0;i<reference.atoms().size();i++){var atom=reference.atoms().get(i);nuclei.add(new NuclearCenter(i,atom.element(),new NuclearCharge("O".equals(atom.element())?8:1),new CartesianPosition(atom.xAngstrom()*ANGSTROM_TO_BOHR,atom.yAngstrom()*ANGSTROM_TO_BOHR,atom.zAngstrom()*ANGSTROM_TO_BOHR,LengthUnit.BOHR)));}return new Molecule("prometheus-step3-water",nuclei,new MolecularCharge(0),new ElectronCount(10),new SpinSector(5,5,1));}
    private static QuantumExecutionRequest request(Molecule molecule,H2o13ReferenceReader.Reference reference,Path work){List<CartesianGeometry.Atom> atoms=molecule.nuclei().stream().map(n->{var p=n.position().inBohr();return new CartesianGeometry.Atom(n.element(),p.x(),p.y(),p.z());}).toList();var geometry=new CartesianGeometry(atoms,"bohr");String geometryHash=CanonicalHashing.sha256Hex(atoms.toString()+"|bohr");var specification=new CalculationSpecification("step3-h2o-"+reference.headerLine(),"Step-3 frozen multi-nuclear validation",new MoleculeIdentity("prometheus-step3-water","Water molecule","H2O"),new GeometryIdentity(geometryHash,3),0,1,new QmProtocol("PROMETHEUS_NEURAL_VMC","NEURAL_STATE_V1","none","isolated",false,"Prometheus",JavaNeuralRuntimePolicy.BACKEND_VERSION),List.of(),CalculationType.FORCE_EVALUATION,List.of("ABSOLUTE_ENERGY","CARTESIAN_GRADIENT","CARTESIAN_FORCE"),List.of("step3 locked gates","canonical atom order","force=-gradient"),DatasetRole.HOLDOUT,CostEstimate.zero());var base=new QuantumExecutionRequest(specification,geometry,CanonicalHashing.sha256Hex("O0-H1-H2"),QuantumSolverMode.VARIATIONAL_QUANTUM_STATE,QuantumExecutionRequest.energyAndForces(),new QuantumExecutionOptions(1,1024,work,List.of(),Optional.empty()));return new GeneralMolecularExecutionRequest(molecule,base,"born-oppenheimer-coulomb-v1",GeneralSlaterJastrowState.REPRESENTATION_ID,JavaNeuralRuntimePolicy.OPTIMIZER).identityCompleteRequest();}

    private static void writeCsv(Path path,List<Row> rows)throws IOException{StringBuilder out=new StringBuilder("geometry,source_line,atom,axis,prometheus_energy_hartree,reference_energy_hartree,energy_error_hartree,energy_se_hartree,prometheus_force_hartree_per_bohr,reference_force_hartree_per_bohr,force_error_hartree_per_bohr,force_se_hartree_per_bohr,wall_time_seconds,peak_heap_growth_bytes,state_evaluations,directional_ad_passes,disposition,executor_calls\n");for(Row row:rows)for(Component c:row.components)out.append(String.format(Locale.ROOT,"%s,%d,%d,%c,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.16g,%.6f,%d,%d,%d,%s,%d%n",row.geometry,row.sourceLine,c.atom,c.axis,row.energy,row.referenceEnergy,row.energyError,row.energyStandardError,c.calculated,c.reference,c.error,c.standardError,row.wallTimeNanos/1e9,row.peakHeapGrowthBytes,row.optimizationStateEvaluations+row.forceStateTraversals,row.directionalAdPasses,row.disposition,row.executorCalls));Files.writeString(path,out,StandardCharsets.UTF_8);}
    private static void writeDecision(Path path,Summary s)throws IOException{Files.writeString(path,String.format(Locale.ROOT,"{\n  \"classification\": \"%s\",\n  \"dominant_blocker\": \"%s\",\n  \"completed_geometries\": %d,\n  \"energy_rmse_hartree\": %s,\n  \"force_component_rmse_hartree_per_bohr\": %s,\n  \"force_gradient_exact_sign\": %s,\n  \"immediate_reuse_zero_recomputation\": %s,\n  \"python\": false\n}%n",s.classification,s.dominantBlocker,s.completedGeometries,jsonNumber(s.energyRmse),jsonNumber(s.forceComponentRmse),s.exactForceGradientSign,s.immediateReuse),StandardCharsets.UTF_8);}
    private static String jsonNumber(double value){return Double.isFinite(value)?String.format(Locale.ROOT,"%.16g",value):"null";}
    private static void writeReport(Path path,Summary s,List<Row> rows)throws IOException{StringBuilder out=new StringBuilder("# Step 3 multi-nuclear H2O validation\n\n## Classification\n\n`").append(s.classification).append("`\n\nDominant blocker: `").append(s.dominantBlocker).append("`\n\n## Frozen-gate results\n\n").append(String.format(Locale.ROOT,"- Completed geometries: %d/3\n- Energy RMSE: %.8f Ha (gate <= 0.010)\n- Maximum absolute energy error: %.8f Ha (gate <= 0.015)\n- Force-component RMSE: %.8f Ha/bohr (gate <= 0.010)\n- Maximum force-component error: %.8f Ha/bohr (gate <= 0.025)\n- Maximum energy SE: %.8f Ha\n- Maximum force-component SE: %.8f Ha/bohr\n- Exact force=-gradient: %s\n- Immediate reuse with zero recomputation: %s\n- Maximum peak-heap growth: %d bytes\n",s.completedGeometries,s.energyRmse,s.maxEnergyError,s.forceComponentRmse,s.maxForceComponentError,s.maxEnergyStandardError,s.maxForceStandardError,s.exactForceGradientSign,s.immediateReuse,s.peakHeapGrowthBytes));if(!s.failures.isEmpty())out.append("\n## Execution failures\n\n").append(String.join("\n",s.failures.stream().map(x->"- "+x).toList())).append('\n');out.append("\nStep 3 is frozen at this classification. No correction, tuning, Step 4, or new molecule was started.\n");Files.writeString(path,out,StandardCharsets.UTF_8);}

    public static void main(String[] args)throws Exception{if(args.length!=2)throw new IllegalArgumentException("reference-file and output-directory required");var summary=new WaterMoleculeStep3Validation().run(Path.of(args[0]),Path.of(args[1]));System.out.println(summary);}
    private record GeometryChoice(String id,double oh1,double oh2,double angle) { }
    private record Component(int atom,char axis,double calculated,double reference,double error,double standardError) { }
    private record Row(String geometry,long sourceLine,double energy,double referenceEnergy,double energyError,double energyBlockVariance,double energyStandardError,List<Component> components,double forceComponentRmse,double maxForceComponentError,double maxVectorForceNormError,double maxForceStandardError,double translationForceNorm,double maxPlanarZForce,double hydrogenPermutationError,String srResiduals,long optimizationStateEvaluations,long forceStateTraversals,long directionalAdPasses,long wallTimeNanos,long peakHeapGrowthBytes,String disposition,int executorCalls){private Row{components=List.copyOf(components);}}
    public record Summary(String classification,String dominantBlocker,int completedGeometries,double energyRmse,double maxEnergyError,double forceComponentRmse,double maxForceComponentError,double maxEnergyStandardError,double maxForceStandardError,double maxTranslationForceNorm,double maxPlanarZForce,double hydrogenPermutationError,boolean srConverged,boolean exactForceGradientSign,boolean immediateReuse,long peakHeapGrowthBytes,int executorCalls,List<String> failures){public Summary{failures=List.copyOf(failures);}}
}
