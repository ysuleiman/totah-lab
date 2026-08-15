package totah.lab.prometheus.execution.quantum;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.execution.EvidenceExecutionException;
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

final class JavaNeuralQuantumRuntimeTest {
    @TempDir Path temp;

    @Test void acceptedResultIsSynchronouslyReusableAcrossRestart()throws Exception{
        AtomicInteger calls=new AtomicInteger();QuantumBackend delegate=new JavaNeuralQuantumBackend();
        QuantumBackend counted=new QuantumBackend(){public String backendId(){return delegate.backendId();}public QuantumBackendCapabilities capabilities(){return delegate.capabilities();}public boolean supports(QuantumExecutionRequest r){return delegate.supports(r);}public QuantumResult execute(QuantumExecutionRequest r)throws EvidenceExecutionException{calls.incrementAndGet();return delegate.execute(r);}};
        var request=request(CalculationType.SINGLE_POINT,Set.of(QuantumObservable.ABSOLUTE_ENERGY));
        var runtime=new JavaNeuralQuantumRuntime(new QuantumExecutionService(new QuantumBackendSelector(List.of(counted))),new GeneratedEvidenceRegistry(temp.resolve("registry")));
        var first=runtime.executeOrReuse(request);assertEquals(JavaNeuralQuantumRuntime.Disposition.GENERATED_NEW,first.disposition());assertEquals(1,calls.get());
        var restarted=new JavaNeuralQuantumRuntime(new QuantumExecutionService(new QuantumBackendSelector(List.of(counted))),new GeneratedEvidenceRegistry(temp.resolve("registry")));
        var second=restarted.executeOrReuse(request);assertEquals(JavaNeuralQuantumRuntime.Disposition.REUSE_EXISTING,second.disposition());assertEquals(1,calls.get());assertTrue(second.generatedResult().isEmpty());
    }

    @Test void forcePathUsesOnlyAnalyticSwctAndExactNegativeGradient()throws Exception{
        var result=new JavaNeuralQuantumBackend().execute(request(CalculationType.FORCE_EVALUATION,QuantumExecutionRequest.energyAndForces()));
        assertTrue(result.forceIsNegativeGradient(0));assertEquals(JavaNeuralRuntimePolicy.FORCE_ESTIMATOR,result.executionProvenance().get("force_estimator"));assertEquals(JavaNeuralRuntimePolicy.NUMERICAL_SWCT,result.executionProvenance().get("numerical_swct"));
        String json=java.nio.file.Files.readString(temp.resolve("work").resolve(result.artifactChecksums().keySet().iterator().next()));assertTrue(json.contains("raw_ieee754_bits"));assertTrue(json.contains("atom_order"));
    }

    @Test void optimizationSelectsBlockMatrixFreePolicyAndNeverDense()throws Exception{
        var result=new JavaNeuralQuantumBackend().execute(request(CalculationType.OPTIMIZATION,Set.of(QuantumObservable.ABSOLUTE_ENERGY)));
        assertEquals(JavaNeuralRuntimePolicy.OPTIMIZER,result.executionProvenance().get("optimizer"));assertEquals(JavaNeuralRuntimePolicy.DENSE_SOLVER,result.executionProvenance().get("dense_solver"));
    }

    @Test void generalMolecularIdentityStillReusesWithZeroSecondExecution()throws Exception{
        AtomicInteger calls=new AtomicInteger();QuantumBackend delegate=new JavaNeuralQuantumBackend();QuantumBackend counted=new QuantumBackend(){public String backendId(){return delegate.backendId();}public QuantumBackendCapabilities capabilities(){return delegate.capabilities();}public boolean supports(QuantumExecutionRequest r){return delegate.supports(r);}public QuantumResult execute(QuantumExecutionRequest r)throws EvidenceExecutionException{calls.incrementAndGet();return delegate.execute(r);}};
        double r=1.4;Molecule molecule=new Molecule("prometheus-regression-h2",List.of(new NuclearCenter(0,"H",new NuclearCharge(1),new CartesianPosition(0,0,-r/2,LengthUnit.BOHR)),new NuclearCenter(1,"H",new NuclearCharge(1),new CartesianPosition(0,0,r/2,LengthUnit.BOHR))),new MolecularCharge(0),new ElectronCount(2),new SpinSector(1,1,1));
        QuantumExecutionRequest complete=new GeneralMolecularExecutionRequest(molecule,request(CalculationType.SINGLE_POINT,Set.of(QuantumObservable.ABSOLUTE_ENERGY)),"born-oppenheimer-coulomb-v1",GeneralSlaterJastrowState.REPRESENTATION_ID,JavaNeuralRuntimePolicy.OPTIMIZER).identityCompleteRequest();var runtime=new JavaNeuralQuantumRuntime(new QuantumExecutionService(new QuantumBackendSelector(List.of(counted))),new GeneratedEvidenceRegistry(temp.resolve("general-registry")));assertEquals(JavaNeuralQuantumRuntime.Disposition.GENERATED_NEW,runtime.executeOrReuse(complete).disposition());assertEquals(JavaNeuralQuantumRuntime.Disposition.REUSE_EXISTING,runtime.executeOrReuse(complete).disposition());assertEquals(1,calls.get());
    }

    private QuantumExecutionRequest request(CalculationType type,Set<QuantumObservable> observables){
        double r=1.4;var geometry=new CartesianGeometry(List.of(new CartesianGeometry.Atom("H",0,0,-r/2),new CartesianGeometry.Atom("H",0,0,r/2)),"bohr");
        var molecule=new MoleculeIdentity("prometheus-regression-h2","Hydrogen molecule","H2");
        var identity=new GeometryIdentity("a".repeat(64),2);var protocol=new QmProtocol("PROMETHEUS_NEURAL_VMC","NEURAL_STATE_V1","none","isolated",false,"Prometheus",JavaNeuralRuntimePolicy.BACKEND_VERSION);
        var specification=new CalculationSpecification("step0-test","Step-0 regression",molecule,identity,0,1,protocol,List.of(),type,observables.stream().map(Enum::name).sorted().toList(),List.of("finite","converged"),DatasetRole.DEVELOPMENT,CostEstimate.zero());
        return new QuantumExecutionRequest(specification,geometry,"b".repeat(64),QuantumSolverMode.VARIATIONAL_QUANTUM_STATE,observables,new QuantumExecutionOptions(1,1024,temp.resolve("work"),List.of(),Optional.empty()));
    }
}
