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
import totah.lab.aether.basis.Sto3gHydrogen;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.aether.matrix.RhfScfResult.Status.*;

class AetherRhfScfTest {
    @Test void comparesFreshPlainPyscfPhysicalSolutions() throws IOException {
        var results=Map.of("h2",ScfReceiptReplay.solve("h2",128),"h4",ScfReceiptReplay.solve("h4",128));
        byte[] bytes;
        try(var input=getClass().getResourceAsStream("reference/scf.csv")) { assertNotNull(input);bytes=input.readAllBytes(); }
        assertEquals("f75a262c07043f0750937de84d39e8500dbbd18f2443d056b7996a01067d598d",ContentHash.sha256(bytes));
        var errors=new TreeMap<String,Double>();int count=0;
        for(String line:new String(bytes,StandardCharsets.UTF_8).lines().skip(1).toList()) {
            var c=line.split(",");var result=results.get(c[0]);assertEquals(CONVERGED,result.status());
            var state=result.convergedState().orElseThrow();int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]);
            double actual=switch(c[1]) {
                case "electronic" -> state.energy().electronicHartree();
                case "nuclear" -> state.energy().nuclearHartree();
                case "total" -> state.energy().totalHartree();
                case "converged" -> result.status()==CONVERGED?1:0;
                case "density" -> state.inputDensity().density().get(i,j);
                case "fock" -> state.orbitals().fock().get(i,j);
                case "orbital_energies" -> state.orbitals().energies().get(i);
                case "core_guess_density" -> result.initialDensity().orElseThrow().density().get(i,j);
                case "occupied_projector" -> projector(state,i,j);
                default -> throw new AssertionError(c[1]);
            };
            double expected=Double.parseDouble(c[4]);assertEquals(expected,actual,1e-8,line);
            errors.merge(c[1],Math.abs(actual-expected),Math::max);count++;
        }
        assertEquals(94,count);
        System.out.println("M8 reference systems=2 entries="+count+" errors="+errors);
        results.forEach((name,r)->System.out.println("M8 "+name+" status="+r.status()+" iterations="+r.iterations().size()
                +" Etotal="+r.convergedState().orElseThrow().energy().totalHartree()+" performance="+r.performanceCounters()));
    }
    private static double projector(RhfScfResult.Iteration state,int i,int j) {
        var c=state.orbitals().coefficients();var s=OverlapMatrix.compute(state.inputDensity().density().functions());
        int occupied=state.inputDensity().receipt().occupation().occupiedOrbitals();double value=0;
        for(int a=0;a<c.size();a++) for(int k=0;k<occupied;k++) value+=c.get(i,k)*c.get(a,k)*s.get(a,j);
        return value;
    }

    @ParameterizedTest @ValueSource(strings={"h2","h4"})
    void everyIterationHasConsistentStateEnergyAndBothCriteria(String name) throws IOException {
        var r=ScfReceiptReplay.solve(name,128);assertEquals(CONVERGED,r.status());
        var initial=r.initialDensity().orElseThrow();var previous=initial;double previousEnergy=0;
        assertEquals(RhfScfCalculator.INITIAL_GUESS,r.receipt().initialGuess());
        for(var it:r.iterations()) {
            assertSame(previous,it.inputDensity());
            assertEquals(it.inputDensity().density().densityHash(),it.orbitals().receipt().densityHash());
            assertEquals(it.orbitals().receipt(),it.energy().receipt().fockSource());
            assertEquals(it.inputDensity().receipt(),it.energy().receipt().densityConstruction().orElseThrow());
            assertEquals(it.orbitals().receipt(),it.outputDensity().receipt().orbitalSource());
            assertEquals(it.orbitals().receipt().fockHash(),it.receipt().fockHash());
            assertEquals(it.inputDensity().density().densityHash(),it.receipt().densityHash());
            assertEquals(it.energy().totalHartree(),it.receipt().totalEnergy());
            double residual=0;
            for(int i=0;i<previous.density().size();i++) for(int j=0;j<previous.density().size();j++)
                residual=Math.max(residual,Math.abs(it.outputDensity().density().get(i,j)-previous.density().get(i,j)));
            assertEquals(residual,it.receipt().densityResidual());
            assertEquals(residual<=RhfScfCalculator.DENSITY_THRESHOLD,it.receipt().densityCriterionPassed());
            if(it.number()==1) {
                assertTrue(it.receipt().deltaEnergy().isEmpty());assertFalse(it.receipt().energyCriterionPassed());
            } else {
                double delta=Math.abs(it.energy().totalHartree()-previousEnergy);
                assertEquals(delta,it.receipt().deltaEnergy().orElseThrow());
                assertEquals(delta<=RhfScfCalculator.ENERGY_THRESHOLD,it.receipt().energyCriterionPassed());
            }
            if(it.number()<r.iterations().size()) assertFalse(it.receipt().energyCriterionPassed()&&it.receipt().densityCriterionPassed());
            previousEnergy=it.energy().totalHartree();previous=it.outputDensity();
        }
        var last=r.iterations().getLast().receipt();assertTrue(last.energyCriterionPassed());assertTrue(last.densityCriterionPassed());
        assertEquals(ScientificStatus.SCREENING_ONLY,r.scientificStatus());
        assertEquals(ScientificStatus.SCREENING_ONLY,r.receipt().scientificStatus());
        assertEquals(initial.receipt().receiptHash(),r.receipt().initialDensityReceiptHash());
        var perf=r.performanceCounters();assertEquals(r.iterations().size(),perf.iterationCount());
        assertEquals(initial.density().size(),perf.systemDimension());
        assertTrue(perf.totalJkNanos()>0);assertTrue(perf.totalEigensolveNanos()>0);
        assertTrue(perf.totalScfNanos()>=perf.totalJkNanos()+perf.totalEigensolveNanos());
        assertEquals(last.deltaEnergy(),perf.finalDeltaEnergy());assertEquals(last.densityResidual(),perf.finalDensityResidual().orElseThrow());
    }

    @Test void coreGuessReallyUsesCoreHamiltonian() throws IOException {
        var p=JkTestCases.densities().get("h4_arbitrary");var r=RhfScfCalculator.solve(p.system(),p.functions(),1);
        var zero=DensityMatrix.fromRowMajor(p.system(),p.functions(),java.util.Collections.nCopies(16,0.));
        var guess=OneShotReceiptReplay.solve(zero);var core=new CoreHamiltonianCalculator(p.system(),p.functions()).calculate();
        for(int i=0;i<4;i++) for(int j=0;j<4;j++) assertEquals(core.get(i,j),guess.fock().get(i,j));
        assertEquals(guess.receipt(),r.initialDensity().orElseThrow().receipt().orbitalSource());
    }

    @Test void stationaryEnergyAloneDoesNotStopScf() throws IOException {
        var r=ScfReceiptReplay.solve("h4",128);
        assertTrue(r.iterations().stream().anyMatch(it->it.receipt().energyCriterionPassed()&&!it.receipt().densityCriterionPassed()));
    }

    @ParameterizedTest @ValueSource(strings={"h2","h4"})
    void smallIterationCapFailsClosed(String name) throws IOException {
        var r=ScfReceiptReplay.solve(name,1);assertEquals(MAX_ITERATIONS,r.status());
        assertTrue(r.convergedState().isEmpty());assertEquals(1,r.iterations().size());
        assertFalse(r.iterations().getLast().receipt().energyCriterionPassed());
        assertEquals(ScientificStatus.SCREENING_ONLY,r.scientificStatus());
        assertEquals(1,r.receipt().maximumIterations());
        assertNotEquals(r.receipt().receiptHash(),ScfReceiptReplay.solve(name,128).receipt().receiptHash());
    }

    @ParameterizedTest @ValueSource(ints={0,-1})
    void invalidCapIsAConfigurationError(int cap) throws IOException {
        var p=JkTestCases.densities().get("h2_rhf");
        assertThrows(IllegalArgumentException.class,()->RhfScfCalculator.solve(p.system(),p.functions(),cap));
    }

    @Test void unsupportedOddOpenShellImpossibleOccupationAndBasisFailClosed() throws IOException {
        var p=JkTestCases.densities().get("h2_rhf");
        for(var system:List.of(new QuantumSystem(p.system().nuclei(),1,2),new QuantumSystem(p.system().nuclei(),0,3),
                new QuantumSystem(p.system().nuclei(),-4,1))) {
            var r=RhfScfCalculator.solve(system,p.functions());assertEquals(UNSUPPORTED_SYSTEM,r.status());
            assertTrue(r.convergedState().isEmpty());assertTrue(r.iterations().isEmpty());
        }
        var bad=List.of(KineticTestCases.single(.8,new Point3D(0,0,0)),p.functions().get(1));
        assertEquals(UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(p.system(),bad).status());
        assertEquals(UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(p.system(),List.of()).status());
        var h=Sto3gHydrogen.load();
        assertEquals(UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(p.system(),List.of(p.functions().getFirst(),h.atBohr(new Point3D(1.5,0,0)))).status());
    }

    @ParameterizedTest @ValueSource(doubles={0,1e-8})
    void coincidentOrRankDeficientSystemReturnsNumericalFailure(double separation) throws IOException {
        var h=Sto3gHydrogen.load();var centers=List.of(new Point3D(0,0,0),new Point3D(separation,0,0));
        var system=new QuantumSystem(centers.stream().map(NuclearTestCases::hydrogen).toList(),0,1);
        var r=RhfScfCalculator.solve(system,centers.stream().map(h::atBohr).toList());
        assertEquals(NUMERICAL_FAILURE,r.status());assertTrue(r.convergedState().isEmpty());
        assertTrue(r.iterations().isEmpty());assertFalse(r.receipt().reason().isBlank());
    }

    @ParameterizedTest @ValueSource(strings={"h2","h4"})
    void basisPermutationCovariance(String name) throws IOException {
        var p=JkTestCases.densities().get(name.equals("h2")?"h2_rhf":"h4_arbitrary");
        var a=RhfScfCalculator.solve(p.system(),p.functions());var b=RhfScfCalculator.solve(p.system(),p.functions().reversed());
        assertEquals(CONVERGED,b.status());var x=a.convergedState().orElseThrow();var y=b.convergedState().orElseThrow();int n=p.size();
        assertEquals(x.energy().totalHartree(),y.energy().totalHartree(),1e-10);
        for(int i=0;i<n;i++) {
            assertEquals(x.orbitals().energies().get(i),y.orbitals().energies().get(i),1e-9);
            for(int j=0;j<n;j++) {
                assertEquals(x.inputDensity().density().get(i,j),y.inputDensity().density().get(n-1-i,n-1-j),1e-9);
                assertEquals(x.orbitals().fock().get(i,j),y.orbitals().fock().get(n-1-i,n-1-j),1e-9);
                assertEquals(projector(x,i,j),projector(y,n-1-i,n-1-j),1e-9);
            }
        }
        assertNotEquals(a.receipt().receiptHash(),b.receipt().receiptHash());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void completeSystemTranslationAndRotation(boolean rotate) throws IOException {
        var p=JkTestCases.densities().get("h4_arbitrary");var h=Sto3gHydrogen.load();
        var centers=p.system().nuclei().stream().map(n->n.centerBohr()).map(v->rotate?
                new Point3D(.6*v.x()-.8*v.y(),.8*v.x()+.6*v.y(),v.z()):new Point3D(v.x()+2.3,v.y()-1.7,v.z()+.4)).toList();
        var system=new QuantumSystem(centers.stream().map(NuclearTestCases::hydrogen).toList(),0,1);
        var a=RhfScfCalculator.solve(p.system(),p.functions()).convergedState().orElseThrow();
        var b=RhfScfCalculator.solve(system,centers.stream().map(h::atBohr).toList()).convergedState().orElseThrow();
        assertEquals(a.energy().totalHartree(),b.energy().totalHartree(),1e-10);
        for(int i=0;i<p.size();i++) {
            assertEquals(a.orbitals().energies().get(i),b.orbitals().energies().get(i),1e-9);
            for(int j=0;j<p.size();j++) assertEquals(a.inputDensity().density().get(i,j),b.inputDensity().density().get(i,j),1e-9);
        }
    }

    @Test void trajectoryIsImmutableAndDeterministic() throws IOException {
        var a=ScfReceiptReplay.solve("h4",128);var b=ScfReceiptReplay.solve("h4",128);
        assertEquals(a.receipt(),b.receipt());assertEquals(a.receipt().toString(),b.receipt().toString());
        assertThrows(UnsupportedOperationException.class,()->a.iterations().clear());
        assertThrows(UnsupportedOperationException.class,()->a.receipt().iterations().clear());
        assertThrows(UnsupportedOperationException.class,()->a.receipt().iterations().getFirst().orbitalEnergies().clear());
    }

    @ParameterizedTest @ValueSource(strings={"h2","h4"})
    void separateJvmByteIdenticalConvergedAndCappedReceipts(String name,@TempDir Path temp) throws Exception {
        for(int cap:new int[]{1,128}) {
            byte[] expected=ScfReceiptReplay.solve(name,cap).receipt().toString().getBytes(StandardCharsets.UTF_8);
            for(String locale:List.of("en","tr")) {
                var out=temp.resolve(name+cap+locale+".receipt");
                var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                        "-Duser.language="+locale,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                        ScfReceiptReplay.class.getName(),name,Integer.toString(cap))
                        .redirectOutput(out.toFile()).redirectError(temp.resolve(name+cap+locale+".err").toFile()).start();
                assertTrue(process.waitFor(30,TimeUnit.SECONDS));assertEquals(0,process.exitValue());
                assertArrayEquals(expected,Files.readAllBytes(out));
            }
        }
    }

    @Test void previousDensityAndEnergyReceiptsRemainFrozen() throws IOException {
        var canonical=new StringBuilder();
        for(var p:RhfStateReceiptReplay.inputs().values()) canonical.append(RhfStateReceiptReplay.evaluate(p).replay());
        assertEquals("1f225b207f1e60882f730a3824acf3c70bc6acab3833c57fe77f1be023f5948a",ContentHash.sha256(canonical.toString()));
    }
}
