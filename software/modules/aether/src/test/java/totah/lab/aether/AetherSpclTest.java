package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherSpclTest {
    // Test-only reuse of immutable completed runs avoids repeatedly evaluating the same large ERI tensor.
    private static final Map<String,RhfScfResult> RUNS=new TreeMap<>();
    private static RhfScfResult run(String name) throws IOException {
        var result=RUNS.get(name);
        if(result==null) { result=SpclReceiptReplay.solve(name);RUNS.put(name,result); }
        return result;
    }

    @Test void independentIntegralsJkFockSuppliedEnergyAndScfAudit() throws IOException {
        var references=new TreeMap<String,List<String[]>>();
        for(String line:SpclReceiptReplay.fixture("spcl.csv","2ee480fd7d1280858cd5293475b328d5654b72ef546b35a6fe3a5fb147a79354").lines().skip(1).toList()) {
            var c=line.split(",");references.computeIfAbsent(c[0],key->new ArrayList<>()).add(c);
        }
        var inputs=SpclReceiptReplay.densities();var errors=new TreeMap<String,Double>();var scfErrors=new TreeMap<String,Double>();
        int entries=0,eriEntries=0,convergedSystems=0;
        for(var entry:inputs.entrySet()) {
            String name=entry.getKey();var p=entry.getValue();long start=System.nanoTime();
            var s=OverlapMatrix.compute(p.functions());var t=KineticMatrix.compute(p.functions());var v=NuclearAttractionMatrix.compute(p.functions(),p.system().nuclei());
            var calculator=new CoreHamiltonianCalculator(p.system(),p.functions());var core=calculator.calculate(calculator.bind(t),calculator.bind(v));
            var eri=new ElectronRepulsionCalculator(p.system(),p.functions()).calculate();long integralTime=System.nanoTime()-start;
            var jk=JkCalculator.calculate(p,eri);var oneShot=OneShotRhfCalculator.solve(p,s,core,jk);
            var energy=RhfEnergyCalculator.evaluate(p,core,oneShot.fock());var scf=run(name);
            boolean expectedConvergence=!List.of("chlorobenzene","ethyl_methyl_sulfide").contains(name);
            assertEquals(expectedConvergence?RhfScfResult.Status.CONVERGED:RhfScfResult.Status.MAX_ITERATIONS,scf.status(),name+": "+scf.receipt().reason());
            if(expectedConvergence)convergedSystems++;
            else { assertTrue(scf.convergedState().isEmpty());assertEquals(128,scf.iterations().size()); }
            assertEquals(ScientificStatus.SCREENING_ONLY,scf.scientificStatus());
            if(p.system().nuclei().stream().anyMatch(n->n.charge()>=15))assertTrue(scf.receipt().protocol().contains(Sto3gBasis.SPCL_RESOURCE_SHA256));
            for(var c:references.get(name)) {
                int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]),k=Integer.parseInt(c[4]),l=Integer.parseInt(c[5]);
                double expected=Double.parseDouble(c[6]);
                double actual=switch(c[1]) {
                    case "S"->s.get(i,j);case "T"->t.get(i,j);case "V"->v.get(i,j);case "Hcore"->core.get(i,j);
                    case "ERI"->eri.get(i,j,k,l);case "J"->jk.coulomb().get(i,j);case "K"->jk.exchange().get(i,j);
                    case "Fock"->oneShot.fock().get(i,j);case "density"->p.get(i,j);case "orbital_energies"->oneShot.energies().get(i);
                    case "electronic"->energy.electronicHartree();case "nuclear"->energy.nuclearHartree();case "total"->energy.totalHartree();
                    case "electrons"->OccupiedDensityCalculator.occupation(p.system(),p.size()).electrons();
                    case "occupied"->OccupiedDensityCalculator.occupation(p.system(),p.size()).occupiedOrbitals();
                    case "core_converged"->scf.status()==RhfScfResult.Status.CONVERGED?1:0;
                    default->throw new AssertionError(c[1]);
                };
                assertEquals(expected,actual,2e-9,name+" "+String.join(",",c));
                errors.merge(c[1],Math.abs(actual-expected),Math::max);entries++;
                if(c[1].equals("ERI")) {
                    eriEntries++;
                    for(double value:new double[]{eri.get(j,i,k,l),eri.get(i,j,l,k),eri.get(j,i,l,k),eri.get(k,l,i,j),eri.get(l,k,i,j),eri.get(k,l,j,i),eri.get(l,k,j,i)})assertEquals(actual,value);
                }
                if(expectedConvergence && List.of("total","electronic","nuclear","density","Fock","orbital_energies").contains(c[1])) {
                    var state=scf.convergedState().orElseThrow();
                    double value=switch(c[1]) {
                        case "total"->state.energy().totalHartree();case "electronic"->state.energy().electronicHartree();case "nuclear"->state.energy().nuclearHartree();
                        case "density"->state.inputDensity().density().get(i,j);case "Fock"->state.orbitals().fock().get(i,j);case "orbital_energies"->state.orbitals().energies().get(i);
                        default->throw new AssertionError(c[1]);
                    };
                    assertEquals(expected,value,2e-8,name+" converged "+c[1]);scfErrors.merge(c[1],Math.abs(value-expected),Math::max);
                }
            }
            System.out.println("M10 "+name+" basis="+p.size()+" uniqueERI="+eri.uniqueQuartetCount()+" primitiveQuartets="+eri.performanceCounters().primitiveQuartetsEvaluated()
                    +" integralNanos="+integralTime+" status="+scf.status()+" referenceStateEnergy="+energy.totalHartree()
                    +" finalScfEnergy="+scf.iterations().getLast().energy().totalHartree()+" "+scf.performanceCounters());
        }
        assertEquals(10,inputs.size());assertEquals(8,convergedSystems);
        System.out.println("M10 entries="+entries+" ERIentries="+eriEntries+" supplied-reference-errors="+errors+" converged-scf-errors="+scfErrors);
    }

    @ParameterizedTest @ValueSource(ints={15,16,17})
    void completeNineFunctionBasisHasNormalizedShells(int z) throws IOException {
        var f=Sto3gBasis.load().atBohr(z,new Point3D(0,0,0));assertEquals(9,f.size());
        assertEquals(List.of(CartesianAngularMomentum.S,CartesianAngularMomentum.S,CartesianAngularMomentum.S,
                CartesianAngularMomentum.PX,CartesianAngularMomentum.PY,CartesianAngularMomentum.PZ,
                CartesianAngularMomentum.PX,CartesianAngularMomentum.PY,CartesianAngularMomentum.PZ),f.stream().map(ContractedGaussian::angularMomentum).toList());
        var s=OverlapMatrix.compute(f);
        for(int i=0;i<f.size();i++) { assertEquals(3,f.get(i).terms().size());assertEquals(1,s.get(i,i),2e-14);assertTrue(f.get(i).normalization()>0); }
        assertEquals(0,s.get(3,4));assertEquals(0,s.get(4,5));assertEquals(0,s.get(0,3));
        assertNotEquals(OverlapMatrix.compute(List.of(f.get(3))).receipt().basisGeometryHash(),OverlapMatrix.compute(List.of(f.get(6))).receipt().basisGeometryHash());
    }

    @Test void chargedSulfoniumElectronCountAndProvenance() throws IOException {
        var system=SpclReceiptReplay.systems().get("trimethylsulfonium");var basis=Sto3gBasis.load().forSystem(system);
        assertEquals(1,system.molecularCharge());var occupation=OccupiedDensityCalculator.occupation(system,basis.size());
        assertEquals(42,occupation.electrons());assertEquals(21,occupation.occupiedOrbitals());
        var result=run("trimethylsulfonium");assertEquals(RhfScfResult.Status.CONVERGED,result.status());
        var state=result.convergedState().orElseThrow();assertEquals(42,state.inputDensity().receipt().tracePS(),1e-10);
        assertEquals(system,state.inputDensity().density().system());
        var odd=new QuantumSystem(system.nuclei(),0,2);
        assertEquals(RhfScfResult.Status.UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(odd,basis).status());
        assertThrows(IllegalArgumentException.class,()->DensityMatrix.fromRowMajor(odd,basis,JkTestCases.rowMajor(state.inputDensity().density())));
        // A different even-electron charge cannot reuse the charged state's Fock evidence.
        var changed=new QuantumSystem(system.nuclei(),3,1);
        var wrong=DensityMatrix.fromRowMajor(changed,basis,JkTestCases.rowMajor(state.inputDensity().density()));
        var core=new CoreHamiltonianCalculator(system,basis).calculate();
        assertThrows(IllegalArgumentException.class,()->RhfEnergyCalculator.evaluate(wrong,core,state.orbitals().fock()));
    }

    @Test void failedCoreGuessesAreNotPromotedToConvergedResults() throws IOException {
        for(String name:List.of("chlorobenzene","ethyl_methyl_sulfide")) {
            var result=run(name);assertEquals(RhfScfResult.Status.MAX_ITERATIONS,result.status());assertTrue(result.convergedState().isEmpty());
            var last=result.iterations().getLast().receipt();assertFalse(last.energyCriterionPassed()&&last.densityCriterionPassed());
            assertTrue(last.densityResidual()>1e-3);assertEquals(RhfScfCalculator.INITIAL_GUESS,result.receipt().initialGuess());
            assertEquals(128,result.receipt().maximumIterations());
        }
    }

    @Test void phosphorusAndChlorineRotationTranslationAndBasisPermutation() throws IOException {
        var library=Sto3gBasis.load();
        for(String name:List.of("ph3","hcl")) {
            var system=SpclReceiptReplay.systems().get(name);var original=run(name).convergedState().orElseThrow();
            var moved=new QuantumSystem(system.nuclei().stream().map(n->{var p=n.centerBohr();return new NuclearCenter(new Point3D(.36*p.x()-.48*p.y()+.8*p.z()+.7,.8*p.x()+.6*p.y()-.3,-.48*p.x()+.64*p.y()+.6*p.z()+.2),n.charge());}).toList(),system.molecularCharge(),1);
            var rotated=RhfScfCalculator.solve(moved,library.forSystem(moved)).convergedState().orElseThrow();
            var permuted=RhfScfCalculator.solve(system,library.forSystem(system).reversed()).convergedState().orElseThrow();
            assertEquals(original.energy().totalHartree(),rotated.energy().totalHartree(),2e-9);assertEquals(original.energy().totalHartree(),permuted.energy().totalHartree(),2e-9);
            int n=original.inputDensity().density().size();
            for(int i=0;i<n;i++) {
                assertEquals(original.orbitals().energies().get(i),rotated.orbitals().energies().get(i),2e-9);
                for(int j=0;j<n;j++)assertEquals(original.inputDensity().density().get(i,j),permuted.inputDensity().density().get(n-1-i,n-1-j),2e-9);
            }
        }
    }

    @Test void previousCnoReceiptsAreByteFrozen() throws IOException {
        var hydrogen = JkTestCases.densities().get("h2_rhf");
        var wrongBasis = List.of(KineticTestCases.single(.8, new Point3D(0,0,0)), hydrogen.functions().get(1));
        var rejected = RhfScfCalculator.solve(hydrogen.system(), wrongBasis);
        assertEquals("ee35237483167ec6c9959ec72bfbba607ec9b12f2bb6ba77597f27d8380e151f", ContentHash.sha256(rejected.receipt().toString()));
        var hashes=Map.of("ch4","536b9d739dc6a9a706b524ee4140fc0ca8186b8b14666f832bf13b9dbc2c4013",
                "nh3","eed61f075b8e4cf87b6db9fdddd74db7b8a5f30224367d553e42dadc32e44498",
                "h2o","5aad4ae313ce354fec19970033face9c99240da3d6d4cb9dfec40cb67d06aced",
                "co","179b2533abd71d8892c55e4f4683c5331af06b418ad610eb0a32c2ec7c12be18",
                "n2","e3ec9390511fdeed1eca8fadfe4e5a5aaed605ed59cbb7628131bde617e6cf44",
                "ethene","903b35babf827e3da4db3b00172b8b70ed65a1e05463bc8a16212a4a16384b05");
        var library=Sto3gBasis.load();for(var entry:CnoReceiptReplay.systems().entrySet())
            assertEquals(hashes.get(entry.getKey()),ContentHash.sha256(RhfScfCalculator.solve(entry.getValue(),library.forSystem(entry.getValue())).receipt().toString()));
    }

    @ParameterizedTest @ValueSource(strings={"ph3","hcl","trimethylsulfonium"})
    void separateJvmByteIdenticalChargedAndElementReceipts(String name,@TempDir Path temp) throws Exception {
        byte[] expected=run(name).receipt().toString().getBytes(StandardCharsets.UTF_8);
        for(String locale:List.of("en","tr")) {
            var output=temp.resolve(locale+".receipt");
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx512m","-Duser.language="+locale,
                    "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),SpclReceiptReplay.class.getName(),name)
                    .redirectOutput(output.toFile()).redirectError(temp.resolve(locale+".err").toFile()).start();
            assertTrue(process.waitFor(60,TimeUnit.SECONDS));assertEquals(0,process.exitValue());assertArrayEquals(expected,Files.readAllBytes(output));
        }
    }
}
