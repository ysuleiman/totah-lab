package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherDiisTest {
    private static final Map<String,RhfScfRun> RUNS=new TreeMap<>();
    private static RhfScfRun run(String name) throws Exception {
        if(!RUNS.containsKey(name)) {
            System.out.println("DIIS_START="+name);
            var result=DiisReceiptReplay.solve(name);RUNS.put(name,result);
            System.out.println("DIIS_DONE="+name+" "+result.status()+" iterations="+result.iterations().size()+" reason="+result.receipt().reason());
        }
        return RUNS.get(name);
    }
    @Test void allBenchmarksAgainstFreshIndependentReferences() throws Exception {
        var errors=new TreeMap<String,Double>();var systemErrors=new TreeMap<String,Double>();var failures=new java.util.TreeSet<String>();int entries=0;
        try(var input=getClass().getResourceAsStream("reference/diis.csv")) {
            assertNotNull(input);var bytes=input.readAllBytes();
            assertEquals("ee2a5c70a83bd194cfc0dbb81f975e54ed6e16b58c5e8458dc0e65167d7e0da1",ContentHash.sha256(bytes));
            for(String line:new String(bytes,StandardCharsets.UTF_8).lines().skip(1).toList()) {
                var c=line.split(",");var r=run(c[0]);
                assertEquals(RhfScfResult.Status.CONVERGED,r.status(),c[0]+" "+r.receipt().reason());
                var state=r.convergedState().orElseThrow();int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]);
                double actual=switch(c[1]) {
                    case "total"->state.energy().totalHartree();case "electronic"->state.energy().electronicHartree();
                    case "nuclear"->state.energy().nuclearHartree();case "density"->state.density().get(i,j);
                    case "Fock"->state.physicalOrbitals().fock().get(i,j);case "orbital_energies"->state.physicalOrbitals().energies().get(i);
                    default->throw new AssertionError(c[1]);
                };
                double error=Math.abs(actual-Double.parseDouble(c[4]));errors.merge(c[1],error,Math::max);
                systemErrors.merge(c[0]+":"+c[1],error,Math::max);
                if(error>1e-8)failures.add(c[0]+":"+c[1]);entries++;
            }
        }
        System.out.println("DIIS_EXTERNAL_ENTRIES="+entries+" ERRORS="+errors+" SYSTEM_ERRORS="+systemErrors);
        var dir=Path.of("target/diis-validation");Files.createDirectories(dir);
        for(var item:RUNS.entrySet()) {
            var r=item.getValue();var state=r.convergedState().orElseThrow();
            assertTrue(state.energyCriterionPassed());assertTrue(state.densityCriterionPassed());
            assertTrue(state.deltaEnergy().orElseThrow()<=1e-12);assertTrue(state.densityResidual()<=1e-10);
            assertEquals(128,r.receipt().maximumIterations());
            System.out.println("DIIS_SYSTEM="+item.getKey()+" status="+r.status()+" iterations="+r.iterations().size()+" E="+state.energy().totalHartree()+" delta="+state.deltaEnergy()+" residual="+state.densityResidual()+" counters="+r.performanceCounters());
            Files.writeString(dir.resolve(item.getKey()+".receipt"),r.receipt().toString());
        }
        assertEquals(18,RUNS.size());
        assertTrue(failures.isEmpty(),"Independent reference mismatches: "+failures);
    }
    @Test void previouslyConvergedPlainSystemsKeepPhysicalSolution() throws Exception {
        double energyError=0,densityError=0;int compared=0;var failures=new java.util.TreeSet<String>();
        for(var item:DiisReceiptReplay.systems().entrySet()) {
            if(item.getKey().equals("chlorobenzene")||item.getKey().equals("ethyl_methyl_sulfide"))continue;
            var plain=RhfScfCalculator.solve(item.getValue(),Sto3gBasis.load().forSystem(item.getValue()));
            assertEquals(RhfScfResult.Status.CONVERGED,plain.status(),item.getKey());
            var p=plain.convergedState().orElseThrow();var d=run(item.getKey()).convergedState().orElseThrow();
            double e=Math.abs(p.energy().totalHartree()-d.energy().totalHartree());energyError=Math.max(energyError,e);
            if(e>1e-8)failures.add(item.getKey()+":energy");
            for(int i=0;i<d.density().size();i++)for(int j=0;j<d.density().size();j++) {
                double error=Math.abs(p.inputDensity().density().get(i,j)-d.density().get(i,j));
                densityError=Math.max(densityError,error);if(error>1e-8)failures.add(item.getKey()+":density");
            }
            compared++;
        }
        assertEquals(16,compared);
        System.out.println("DIIS_PLAIN_COMPARED="+compared+" energy_error="+energyError+" density_error="+densityError+" mismatches="+failures);
        assertTrue(failures.isEmpty(),"Plain/DIIS solution mismatches: "+failures);
    }
    @Test void explicitPlainPolicyPreservesHistoricalReceipt() throws Exception {
        var p=JkTestCases.densities().get("h2_rhf");var legacy=RhfScfCalculator.solve(p.system(),p.functions());
        var plain=RhfScfCalculator.solve(p.system(),p.functions(),ScfPolicy.PLAIN);
        assertEquals(legacy.receipt(),plain.plainResult().orElseThrow().receipt());
        assertEquals(legacy.receipt().receiptHash(),plain.receipt().receiptHash());
    }
    @Test void preservedPlainFailureReceiptsReplayUnchanged() throws Exception {
        var hashes=Map.of("chlorobenzene","6ffb776762852d165ca5e5e7a3d3e738104da76aa42b5799601a3db62775cd42",
                "ethyl_methyl_sulfide","35c83d3b419aeef07cb5e481969a0d418e1948091c23150a1b5514aa26af7dc9");
        for(var name:hashes.keySet()) {
            var result=SpclReceiptReplay.solve(name);
            assertEquals(RhfScfResult.Status.MAX_ITERATIONS,result.status());
            assertEquals(128,result.iterations().size());assertEquals(hashes.get(name),result.receipt().receiptHash());
            assertEquals(Files.readString(Path.of("validation/milestone-10.1/"+name+"-plain.receipt")),result.receipt().toString());
        }
    }
    @Test void capAndUnsupportedSystemsFailClosed() throws Exception {
        var p=JkTestCases.densities().get("h4_arbitrary");
        var capped=RhfScfCalculator.solve(p.system(),p.functions(),1,ScfPolicy.DIIS);
        assertEquals(RhfScfResult.Status.MAX_ITERATIONS,capped.status());assertTrue(capped.convergedState().isEmpty());
        var odd=new QuantumSystem(p.system().nuclei(),1,2);
        assertEquals(RhfScfResult.Status.UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(odd,p.functions(),ScfPolicy.DIIS).status());
        var basis=new ArrayList<>(p.functions());Collections.swap(basis,0,1);
        var wrong=new QuantumSystem(p.system().nuclei().subList(0,2),0,1);
        assertEquals(RhfScfResult.Status.UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(wrong,basis,ScfPolicy.DIIS).status());
    }
    @Test void basisPermutationAndRigidMotion() throws Exception {
        var system=DiisReceiptReplay.systems().get("h2o");var basis=Sto3gBasis.load().forSystem(system);
        var ref=run("h2o").convergedState().orElseThrow();var reversed=new ArrayList<>(basis);Collections.reverse(reversed);
        var perm=RhfScfCalculator.solve(system,reversed,ScfPolicy.DIIS).convergedState().orElseThrow();
        assertEquals(ref.energy().totalHartree(),perm.energy().totalHartree(),1e-8);
        for(int i=0;i<basis.size();i++)for(int j=0;j<basis.size();j++)assertEquals(ref.density().get(i,j),perm.density().get(basis.size()-1-i,basis.size()-1-j),1e-8);
        var nuclei=system.nuclei().stream().map(n->{var p=n.centerBohr();return new NuclearCenter(new Point3D(-p.y()+2,p.x()-3,p.z()+.7),n.charge());}).toList();
        var moved=new QuantumSystem(nuclei,0,1);
        var motion=RhfScfCalculator.solve(moved,Sto3gBasis.load().forSystem(moved),ScfPolicy.DIIS).convergedState().orElseThrow();
        assertEquals(ref.energy().totalHartree(),motion.energy().totalHartree(),1e-8);
    }
    @Test void replayAcrossFreshJava21Jvms() throws Exception {
        for(String name:new String[]{"h2","h4","h2o","chlorobenzene","ethyl_methyl_sulfide"}) {
            var expected=run(name).receipt().toString().getBytes(StandardCharsets.UTF_8);
            var file=Files.createTempFile("aether-diis-replay-",".txt");
            try {
                var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx512m","-cp",System.getProperty("java.class.path"),DiisReceiptReplay.class.getName(),name)
                        .redirectOutput(file.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                assertEquals(0,process.waitFor());assertArrayEquals(expected,Files.readAllBytes(file),name);
            }finally{Files.deleteIfExists(file);}
        }
    }
}
