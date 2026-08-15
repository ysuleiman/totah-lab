package totah.lab.prometheus.execution.quantum;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.execution.EvidenceExecutionException;
import totah.lab.prometheus.neural.CoulombRadialNeuralState;
import totah.lab.prometheus.neural.DenseLayer;
import totah.lab.prometheus.neural.FeedForwardNetwork;
import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.neural.HeliumCorrelatedNeuralState;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.neural.IdentityActivation;
import totah.lab.prometheus.neural.ParameterTensor;
import totah.lab.prometheus.neural.TanhActivation;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.variational.BlockMatrixFreeHydrogenMoleculeOptimizer;
import totah.lab.prometheus.variational.HeliumHamiltonian;
import totah.lab.prometheus.variational.HeliumImportancePointSet;
import totah.lab.prometheus.variational.HeliumRayleighFunctional;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.HydrogenMoleculeStreamingRayleighEvaluator;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.ThreeDimensionalRayleighFunctional;
import totah.lab.prometheus.variational.TransformedRadialPointSet;
import totah.lab.prometheus.variational.force.AnalyticDifferentialSwctForceEstimator;
import totah.lab.prometheus.variational.force.GeneralAnalyticDifferentialSwctForceEstimator;
import totah.lab.prometheus.validation.WaterMoleculeStep3Calculation;

/** Concrete, stateless Java-only backend for the frozen H/He/H2 Step-0 regressions. */
public final class JavaNeuralQuantumBackend implements QuantumBackend {
    private static final String METHOD="PROMETHEUS_NEURAL_VMC", BASIS="NEURAL_STATE_V1";
    private static final List<Double> H_PARAMS=List.of(-2.383965931404784,-.5338421928191946,.5338421924995539,2.383965931683480,.5353332181324436,.3168617924069697,-.3168617896680404,-.5353332184452000,.03970913104140896,.003081402141653947,-.003081402142781574,-.03970913104299929,.08576115365636498);
    private static final List<Double> HE_PARAMS=List.of(.216187037152171,-.08291169805969312,.06479344849516018,.08440902704626406,-.03977144127333949);
    private static final List<Double> H2_PARAMS=List.of(.8576772116910546,.11919655001255025,-.06709570692540537,.04370894911240642,-.32732397143757097,.21519667708138937,-.06386208428749664,.04232059707741613,.017563345336565027,-.12118637444956007,.11444052280585346,.26554487072063354,.19811737981250818,.07860098998305089,-.2778578205251936,-.16701609069702947,.07580798604963333,-.15755013283163458,.22812063643399538,-.1453261891402233);

    @Override public String backendId(){return JavaNeuralRuntimePolicy.BACKEND_ID;}
    @Override public QuantumBackendCapabilities capabilities(){return new QuantumBackendCapabilities(
            EnumSet.of(QuantumSolverMode.VARIATIONAL_QUANTUM_STATE),
            EnumSet.of(CalculationType.SINGLE_POINT,CalculationType.OPTIMIZATION,CalculationType.FORCE_EVALUATION),
            EnumSet.of(QuantumObservable.ABSOLUTE_ENERGY,QuantumObservable.CARTESIAN_GRADIENT,QuantumObservable.CARTESIAN_FORCE),false);}

    @Override public boolean supports(QuantumExecutionRequest r){
        if(!capabilities().satisfies(r)||!r.specification().constraints().isEmpty())return false;
        var p=r.specification().protocol();
        if(!METHOD.equals(p.method())||!BASIS.equals(p.basis())||!"none".equals(p.dispersion())
                ||!"isolated".equals(p.environment())||p.counterpoise()||!"Prometheus".equals(p.software())
                ||!JavaNeuralRuntimePolicy.BACKEND_VERSION.equals(p.softwareVersion())||!"bohr".equals(r.geometry().unit()))return false;
        String id=r.specification().molecule().moleculeId();
        return switch(id){
            case "prometheus-regression-hydrogen" -> validAtom(r,"H",0,2)&&energyOnly(r)&&r.specification().calculationType()==CalculationType.SINGLE_POINT;
            case "prometheus-regression-helium" -> validAtom(r,"He",0,1)&&energyOnly(r)&&r.specification().calculationType()==CalculationType.SINGLE_POINT;
            case "prometheus-regression-h2" -> validH2(r)&&validH2Request(r);
            case "prometheus-general-force-h2-fixture" -> validH2(r)&&r.specification().calculationType()==CalculationType.FORCE_EVALUATION&&r.requiredObservables().equals(QuantumExecutionRequest.energyAndForces());
            case "prometheus-step3-water" -> validWater(r)&&r.specification().calculationType()==CalculationType.FORCE_EVALUATION&&r.requiredObservables().equals(QuantumExecutionRequest.energyAndForces());
            default -> false;
        };
    }

    @Override public QuantumResult execute(QuantumExecutionRequest request)throws EvidenceExecutionException{
        if(!supports(request))throw new EvidenceExecutionException("request is outside frozen Java neural Step-0 scope");
        try{
            Computed c=compute(request);Path directory=request.options().workingDirectory();Files.createDirectories(directory);
            Path artifact=directory.resolve("quantum-result-"+request.scientificIdentity()+".json");
            writeArtifact(artifact,request,c);String sha=ArtifactChecksums.sha256(artifact);
            return new QuantumResult(request.scientificIdentity(),backendId(),JavaNeuralRuntimePolicy.BACKEND_VERSION,
                    ConvergenceStatus.CONVERGED,Optional.of(new QuantumResult.Energy(c.energy,QuantumResult.EnergyUnit.HARTREE)),
                    c.gradient, c.force,Map.of(artifact.getFileName().toString(),sha),c.provenance,Instant.now());
        }catch(IOException|RuntimeException e){throw new EvidenceExecutionException("Java neural execution failed",e);}
    }

    private static Computed compute(QuantumExecutionRequest r){
        String id=r.specification().molecule().moleculeId();
        if(id.endsWith("hydrogen")){
            var n=new FeedForwardNetwork(List.of(new DenseLayer(ParameterTensor.of(4,1,-2,-.7,.7,2),new double[]{1,.3,-.3,-1},new TanhActivation()),new DenseLayer(ParameterTensor.of(1,4,.02,-.02,.02,-.02),new double[]{.12},new IdentityActivation())));
            double e=new ThreeDimensionalRayleighFunctional().evaluate(new CoulombRadialNeuralState(1,n).withParameters(new ParameterVector(H_PARAMS)),new totah.lab.prometheus.variational.HydrogenAtomHamiltonian(1),TransformedRadialPointSet.create(801,1)).objective();
            return scalar(e,"FROZEN_HYDROGEN_REGRESSION");
        }
        if(id.endsWith("helium")){
            double e=new HeliumRayleighFunctional().evaluate(new HeliumCorrelatedNeuralState(new ParameterVector(HE_PARAMS)),new HeliumHamiltonian(),HeliumImportancePointSet.create(30000,1.8,1009)).objective();
            return scalar(e,"FROZEN_HELIUM_REGRESSION");
        }
        if(id.equals("prometheus-general-force-h2-fixture"))return computeGeneralForce(r);
        if(id.equals("prometheus-step3-water"))return computeStep3Water(r);
        double radius=Math.abs(r.geometry().atoms().get(1).z()-r.geometry().atoms().get(0).z());
        var state=new GeometryConditionedHydrogenMoleculeState(radius,new ParameterVector(H2_PARAMS));
        var h=new HydrogenMoleculeHamiltonian(radius);var batches=new HydrogenMoleculeImportanceBatches(2500,radius,1.15,43,512);
        Map<String,String> provenance=new LinkedHashMap<>();provenance.put("sampling","BOUNDED_DETERMINISTIC_IMPORTANCE");
        if(r.specification().calculationType()==CalculationType.OPTIMIZATION){
            var cfg=new BlockMatrixFreeHydrogenMoleculeOptimizer.Configuration(1,.05,1e-3,.10,200,1e-12,1e-14);
            var optimized=new BlockMatrixFreeHydrogenMoleculeOptimizer(cfg).optimize(state,h,batches);state=state.withParameters(optimized.parameters());
            provenance.put("optimizer",optimized.optimizer());provenance.put("residual_definition",optimized.residualDefinition());provenance.put("dense_solver",JavaNeuralRuntimePolicy.DENSE_SOLVER);
        }
        double energy=new HydrogenMoleculeStreamingRayleighEvaluator().evaluate(state,h,batches).objective();
        if(r.specification().calculationType()!=CalculationType.FORCE_EVALUATION)return new Computed(energy,Optional.empty(),Optional.empty(),Map.copyOf(provenance));
        var forceResult=new AnalyticDifferentialSwctForceEstimator().evaluate(state,h,batches);double f=forceResult.forceHartreePerBohr();
        var forces=List.of(new QuantumResult.Vector3(0,0,-f),new QuantumResult.Vector3(0,0,f));
        var gradients=List.of(new QuantumResult.Vector3(0,0,f),new QuantumResult.Vector3(0,0,-f));
        provenance.put("force_estimator",JavaNeuralRuntimePolicy.FORCE_ESTIMATOR);provenance.put("numerical_swct",JavaNeuralRuntimePolicy.NUMERICAL_SWCT);
        return new Computed(forceResult.energyHartree(),Optional.of(new QuantumResult.CartesianField(gradients,QuantumResult.CartesianUnit.HARTREE_PER_BOHR)),Optional.of(new QuantumResult.CartesianField(forces,QuantumResult.CartesianUnit.HARTREE_PER_BOHR)),Map.copyOf(provenance));
    }

    private static Computed computeGeneralForce(QuantumExecutionRequest r){List<totah.lab.prometheus.molecular.NuclearCenter> nuclei=new ArrayList<>();for(int i=0;i<r.geometry().atoms().size();i++){var a=r.geometry().atoms().get(i);nuclei.add(new totah.lab.prometheus.molecular.NuclearCenter(i,a.element(),new totah.lab.prometheus.molecular.NuclearCharge(1),new totah.lab.prometheus.molecular.CartesianPosition(a.x(),a.y(),a.z(),totah.lab.prometheus.molecular.LengthUnit.BOHR)));}var molecule=new totah.lab.prometheus.molecular.Molecule(r.specification().molecule().moleculeId(),nuclei,new totah.lab.prometheus.molecular.MolecularCharge(0),new totah.lab.prometheus.molecular.ElectronCount(2),new totah.lab.prometheus.molecular.SpinSector(1,1,1));var state=GeneralSlaterJastrowState.cuspInitialized(molecule);double radius=Math.abs(r.geometry().atoms().get(1).z()-r.geometry().atoms().get(0).z());var batches=new HydrogenMoleculeImportanceBatches(2500,radius,1.15,43,512);totah.lab.prometheus.variational.GeneralMolecularSampleSource source=consumer->batches.forEachBatch(batch->batch.forEach(point->{var old=point.coordinates().particles();var coordinates=new totah.lab.prometheus.variational.QuantumCoordinates(List.of(new totah.lab.prometheus.variational.QuantumCoordinates.ParticleCoordinate(0,old.get(0).xBohr(),old.get(0).yBohr(),old.get(0).zBohr(),totah.lab.prometheus.variational.SpinProjection.ALPHA),new totah.lab.prometheus.variational.QuantumCoordinates.ParticleCoordinate(1,old.get(1).xBohr(),old.get(1).yBohr(),old.get(1).zBohr(),totah.lab.prometheus.variational.SpinProjection.BETA)));consumer.accept(point.weight(),coordinates);}));var result=new GeneralAnalyticDifferentialSwctForceEstimator().evaluate(state,source);List<QuantumResult.Vector3> force=result.forces().stream().map(f->new QuantumResult.Vector3(f.fx(),f.fy(),f.fz())).toList();List<QuantumResult.Vector3> gradient=force.stream().map(f->new QuantumResult.Vector3(-f.x(),-f.y(),-f.z())).toList();Map<String,String> provenance=Map.of("force_estimator","GENERAL_ANALYTIC_DIFFERENTIAL_SWCT","state_traversals",Long.toString(result.stateTraversals()),"local_energy_evaluations",Long.toString(result.localEnergyEvaluations()),"directional_ad_passes",Long.toString(result.directionalAdPasses()),"force_unit",result.forceUnit());return new Computed(result.energyHartree(),Optional.of(new QuantumResult.CartesianField(gradient,QuantumResult.CartesianUnit.HARTREE_PER_BOHR)),Optional.of(new QuantumResult.CartesianField(force,QuantumResult.CartesianUnit.HARTREE_PER_BOHR)),provenance);}

    private static Computed computeStep3Water(QuantumExecutionRequest r){List<totah.lab.prometheus.molecular.NuclearCenter> nuclei=new ArrayList<>();for(int i=0;i<r.geometry().atoms().size();i++){var a=r.geometry().atoms().get(i);int z="O".equals(a.element())?8:1;nuclei.add(new totah.lab.prometheus.molecular.NuclearCenter(i,a.element(),new totah.lab.prometheus.molecular.NuclearCharge(z),new totah.lab.prometheus.molecular.CartesianPosition(a.x(),a.y(),a.z(),totah.lab.prometheus.molecular.LengthUnit.BOHR)));}var molecule=new totah.lab.prometheus.molecular.Molecule(r.specification().molecule().moleculeId(),nuclei,new totah.lab.prometheus.molecular.MolecularCharge(0),new totah.lab.prometheus.molecular.ElectronCount(10),new totah.lab.prometheus.molecular.SpinSector(5,5,1));var result=new WaterMoleculeStep3Calculation().run(molecule);List<QuantumResult.Vector3> force=result.forces().stream().map(f->new QuantumResult.Vector3(f.fx(),f.fy(),f.fz())).toList();List<QuantumResult.Vector3> gradient=force.stream().map(f->new QuantumResult.Vector3(-f.x(),-f.y(),-f.z())).toList();Map<String,String> provenance=new LinkedHashMap<>();provenance.put("validation_gate","STEP_3_H2O");provenance.put("optimizer",result.optimizer());provenance.put("force_estimator",result.forceEstimator());provenance.put("energy_block_variance",Double.toString(result.energyBlockVariance()));provenance.put("energy_standard_error",Double.toString(result.energyStandardError()));provenance.put("sr_relative_true_residuals",result.srRelativeTrueResiduals().toString());provenance.put("optimized_parameters",result.parameters().toString());provenance.put("optimization_state_evaluations",Long.toString(result.optimizationStateEvaluations()));provenance.put("streamed_operator_passes",Long.toString(result.streamedOperatorPasses()));provenance.put("force_state_traversals",Long.toString(result.forceStateTraversals()));provenance.put("local_energy_evaluations",Long.toString(result.localEnergyEvaluations()));provenance.put("directional_ad_passes",Long.toString(result.directionalAdPasses()));provenance.put("wall_time_nanos",Long.toString(result.wallTimeNanos()));provenance.put("peak_heap_growth_bytes",Long.toString(result.peakHeapGrowthBytes()));for(var u:result.forceUncertainty()){provenance.put("force_se_"+u.canonicalNucleusIndex()+"_x",Double.toString(u.fxStandardError()));provenance.put("force_se_"+u.canonicalNucleusIndex()+"_y",Double.toString(u.fyStandardError()));provenance.put("force_se_"+u.canonicalNucleusIndex()+"_z",Double.toString(u.fzStandardError()));}return new Computed(result.energyHartree(),Optional.of(new QuantumResult.CartesianField(gradient,QuantumResult.CartesianUnit.HARTREE_PER_BOHR)),Optional.of(new QuantumResult.CartesianField(force,QuantumResult.CartesianUnit.HARTREE_PER_BOHR)),Map.copyOf(provenance));}

    private static Computed scalar(double e,String kernel){return new Computed(e,Optional.empty(),Optional.empty(),Map.of("kernel",kernel,"sampling","BOUNDED_DETERMINISTIC"));}
    private static boolean energyOnly(QuantumExecutionRequest r){return r.requiredObservables().equals(SetHolder.ENERGY);}
    private static boolean validAtom(QuantumExecutionRequest r,String element,int charge,int multiplicity){return r.geometry().atoms().size()==1&&element.equals(r.geometry().atoms().get(0).element())&&r.specification().formalCharge()==charge&&r.specification().multiplicity()==multiplicity;}
    private static boolean validH2(QuantumExecutionRequest r){var a=r.geometry().atoms();return a.size()==2&&a.stream().allMatch(x->"H".equals(x.element()))&&r.specification().formalCharge()==0&&r.specification().multiplicity()==1&&a.get(0).x()==0&&a.get(0).y()==0&&a.get(1).x()==0&&a.get(1).y()==0&&Double.doubleToLongBits(a.get(0).z())==Double.doubleToLongBits(-a.get(1).z())&&a.get(0).z()<0;}
    private static boolean validWater(QuantumExecutionRequest r){var a=r.geometry().atoms();return a.size()==3&&"O".equals(a.get(0).element())&&"H".equals(a.get(1).element())&&"H".equals(a.get(2).element())&&r.specification().formalCharge()==0&&r.specification().multiplicity()==1;}
    private static boolean validH2Request(QuantumExecutionRequest r){return switch(r.specification().calculationType()){case SINGLE_POINT,OPTIMIZATION->energyOnly(r);case FORCE_EVALUATION->r.requiredObservables().equals(QuantumExecutionRequest.energyAndForces());default->false;};}

    private static void writeArtifact(Path path,QuantumExecutionRequest r,Computed c)throws IOException{
        Map<String,Object> root=new LinkedHashMap<>();root.put("scientific_identity",r.scientificIdentity());root.put("specification_checksum",r.specification().checksum());root.put("canonical_atom_map_hash",r.canonicalAtomMapHash());root.put("geometry_checksum",r.specification().geometry().sha256());root.put("geometry_unit",r.geometry().unit());
        List<Map<String,Object>> atoms=new ArrayList<>();for(int i=0;i<r.geometry().atoms().size();i++){var a=r.geometry().atoms().get(i);atoms.add(Map.of("index",i,"element",a.element(),"x",exact(a.x()),"y",exact(a.y()),"z",exact(a.z())));}root.put("atom_order",atoms);root.put("energy",exact(c.energy));root.put("energy_unit","hartree");root.put("gradient",field(c.gradient));root.put("force",field(c.force));root.put("provenance",c.provenance);
        byte[] bytes=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(root);
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){channel.write(java.nio.ByteBuffer.wrap(bytes));channel.force(true);}
    }
    private static Object field(Optional<QuantumResult.CartesianField> field){if(field.isEmpty())return null;return Map.of("unit",field.get().unit().name().toLowerCase(),"vectors",field.get().vectors().stream().map(v->List.of(exact(v.x()),exact(v.y()),exact(v.z()))).toList());}
    private static Map<String,String> exact(double v){return Map.of("decimal",Double.toString(v),"hex",Double.toHexString(v),"raw_ieee754_bits",String.format("%016x",Double.doubleToRawLongBits(v)));}
    private record Computed(double energy,Optional<QuantumResult.CartesianField> gradient,Optional<QuantumResult.CartesianField> force,Map<String,String> provenance){}
    private static final class SetHolder{private static final java.util.Set<QuantumObservable> ENERGY=java.util.Set.of(QuantumObservable.ABSOLUTE_ENERGY);}
}
