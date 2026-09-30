package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import static totah.lab.aether.OneShotReceiptReplay.solve;

class AetherRhfStateTest {
    @Test void freshExternalReferencesEveryElementAndEnergy() throws IOException {
        var evidence = new TreeMap<String,RhfStateReceiptReplay.Evidence>();
        RhfStateReceiptReplay.inputs().forEach((name,p) -> evidence.put(name,RhfStateReceiptReplay.evaluate(p)));
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/rhf-state.csv")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        assertEquals("819f553b31fa10a259b7d27ad504dcc2ecad59fa443cf969e4580cacd36962a8",ContentHash.sha256(bytes));
        var errors = new TreeMap<String,Double>(); int count = 0;
        for (String line : new String(bytes,StandardCharsets.UTF_8).lines().skip(1).toList()) {
            var c = line.split(","); var r = evidence.get(c[0]);
            double actual = switch(c[1]) {
                case "density" -> r.built().density().get(Integer.parseInt(c[2]),Integer.parseInt(c[3]));
                case "occupied" -> r.built().receipt().occupation().occupiedOrbitals();
                case "trace_ps" -> r.built().receipt().tracePS();
                case "nuclear" -> r.external().nuclearHartree();
                case "electronic" -> r.external().electronicHartree();
                case "total" -> r.external().totalHartree();
                case "built_electronic" -> r.constructed().electronicHartree();
                case "built_total" -> r.constructed().totalHartree();
                default -> throw new AssertionError(c[1]);
            };
            double expected = Double.parseDouble(c[4]);
            assertEquals(expected,actual,1e-10,line);
            errors.merge(c[1],Math.abs(actual-expected),Math::max); count++;
        }
        assertEquals(146,count);
        System.out.println("M7 reference entries="+count+" max errors="+errors);
        var h2 = evidence.get("h2_fresh_rhf");
        System.out.println("M7 H2 occupied="+h2.built().receipt().occupation().occupiedOrbitals()+" trace="+h2.built().receipt().tracePS()
                +" Enuc="+h2.external().nuclearHartree()+" Eelec="+h2.external().electronicHartree()+" Etotal="+h2.external().totalHartree());
    }

    @ParameterizedTest @ValueSource(strings={"h2_fresh_rhf","h4_fresh_rhf","h2_arbitrary","h4_off_diagonal"})
    void densityIdentitiesAndOccupationOrdering(String name) throws IOException {
        var p = RhfStateReceiptReplay.inputs().get(name); var o = solve(p); var s = OverlapMatrix.compute(p.functions());
        var r = OccupiedDensityCalculator.build(p.system(),s,o.coefficients(),o.energies()); var d = r.density();
        assertEquals(p.size()/2,r.receipt().occupation().occupiedOrbitals());
        double trace = 0;
        for (int i=0;i<p.size();i++) for (int j=0;j<p.size();j++) {
            double occupied = 0, psp = 0;
            for (int k=0;k<p.size()/2;k++) occupied += 2*o.coefficients().get(i,k)*o.coefficients().get(j,k);
            for (int a=0;a<p.size();a++) for (int b=0;b<p.size();b++) psp += d.get(i,a)*s.get(a,b)*d.get(b,j);
            assertEquals(occupied,d.get(i,j),1e-14); assertEquals(d.get(i,j),d.get(j,i));
            assertEquals(2*d.get(i,j),psp,1e-10); trace+=d.get(i,j)*s.get(j,i);
        }
        assertEquals(p.size(),trace,1e-10);
        assertEquals(o.receipt(),r.receipt().orbitalSource());
        assertEquals(d.densityHash(),r.receipt().densityHash());
        assertEquals(ScientificStatus.SCREENING_ONLY,r.receipt().status());
    }

    @Test void oddOpenShellAndImpossibleOccupationsFailClosed() throws IOException {
        var p = JkTestCases.densities().get("h2_rhf"); var nuclei=p.system().nuclei();
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.occupation(new QuantumSystem(nuclei,1,2),2));
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.occupation(new QuantumSystem(nuclei,0,3),2));
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.occupation(new QuantumSystem(nuclei,-4,1),2));
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.occupation(p.system(),0));
        assertEquals(0,OccupiedDensityCalculator.occupation(new QuantumSystem(nuclei,2,1),2).occupiedOrbitals());
    }

    @Test void emptyAndFullOccupationAreMathematicallyValid() throws IOException {
        var template = JkTestCases.densities().get("h2_rhf");
        for (int charge : new int[]{2,-2}) {
            var sys = new QuantumSystem(template.system().nuclei(),charge,1);
            var p = DensityMatrix.fromRowMajor(sys,template.functions(),List.of(0.,0.,0.,0.));
            var r=RhfStateReceiptReplay.evaluate(p);
            assertEquals(2-charge,r.built().receipt().tracePS(),1e-10);
            if (charge==2) {
                for(double v:JkTestCases.rowMajor(r.built().density())) assertEquals(0.,v);
                assertEquals(0.,r.constructed().electronicHartree());
            }
        }
    }

    @Test void incompatibleDensityOrbitalSystemBasisAndGeometryFailClosed() throws IOException {
        var p=JkTestCases.densities().get("h2_rhf"); var o=solve(p); var s=OverlapMatrix.compute(p.functions());
        var charged=new QuantumSystem(p.system().nuclei(),2,1);
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(charged,s,o.coefficients(),o.energies()));
        var other=solve(JkTestCases.densities().get("h2_arbitrary"));
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(p.system(),s,o.coefficients(),other.energies()));
        var reversed=OverlapMatrix.compute(List.of(p.functions().get(1),p.functions().get(0)));
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(p.system(),reversed,o.coefficients(),o.energies()));
        var h=Sto3gHydrogen.load();
        var moved=OverlapMatrix.compute(List.of(h.atBohr(new Point3D(0,0,0)),h.atBohr(new Point3D(1.5,0,0))));
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(p.system(),moved,o.coefficients(),o.energies()));
        var changed=OverlapMatrix.compute(List.of(KineticTestCases.single(.8,new Point3D(0,0,0)),p.functions().get(1)));
        assertThrows(IllegalArgumentException.class,()->OccupiedDensityCalculator.build(p.system(),changed,o.coefficients(),o.energies()));
    }

    @Test void energyRequiresExactDensityAndCoreLineage() throws IOException {
        var p=JkTestCases.densities().get("h2_rhf"); var o=solve(p);
        var core=new CoreHamiltonianCalculator(p.system(),p.functions()).calculate();
        var built=OccupiedDensityCalculator.build(p.system(),OverlapMatrix.compute(p.functions()),o.coefficients(),o.energies());
        assertNotEquals(p.densityHash(),built.density().densityHash());
        assertThrows(IllegalArgumentException.class,()->RhfEnergyCalculator.evaluate(built,core,o.fock()));
        var changed=JkTestCases.rowMajor(p); changed.set(0,Math.nextUp(changed.getFirst()));
        var tiny=DensityMatrix.fromRowMajor(p.system(),p.functions(),changed);
        assertThrows(IllegalArgumentException.class,()->RhfEnergyCalculator.evaluate(tiny,core,o.fock()));
        var charged=new QuantumSystem(p.system().nuclei(),2,1);
        var wrongCore=new CoreHamiltonianCalculator(charged,p.functions()).calculate();
        assertThrows(IllegalArgumentException.class,()->RhfEnergyCalculator.evaluate(p,wrongCore,o.fock()));
        var wrongP=DensityMatrix.fromRowMajor(charged,p.functions(),JkTestCases.rowMajor(p));
        assertThrows(IllegalArgumentException.class,()->RhfEnergyCalculator.evaluate(wrongP,core,o.fock()));
        var reversed=DensityMatrix.fromRowMajor(p.system(),List.of(p.functions().get(1),p.functions().get(0)),JkTestCases.rowMajor(p));
        assertThrows(IllegalArgumentException.class,()->RhfEnergyCalculator.evaluate(reversed,core,o.fock()));
        var correct=RhfStateReceiptReplay.evaluate(p);
        assertTrue(correct.external().receipt().densityConstruction().isEmpty());
        assertEquals(correct.built().receipt(),correct.constructed().receipt().densityConstruction().orElseThrow());
    }

    @ParameterizedTest @ValueSource(strings={"h2_arbitrary","h2_zero","h4_off_diagonal"})
    void nonSelfConsistentStatesAreEvaluatedWithoutConvergenceClaim(String name) throws IOException {
        var p=JkTestCases.densities().get(name); var r=RhfStateReceiptReplay.evaluate(p);
        double difference=0;
        for(int i=0;i<p.size();i++) for(int j=0;j<p.size();j++) difference=Math.max(difference,Math.abs(p.get(i,j)-r.built().density().get(i,j)));
        assertTrue(difference>1e-3);
        assertEquals("SUPPLIED_STATE_NO_SCF_PERFORMED",r.external().receipt().evaluation());
        assertEquals(ScientificStatus.SCREENING_ONLY,r.external().receipt().status());
        assertEquals(ScientificStatus.SCREENING_ONLY,r.constructed().receipt().status());
    }

    @Test void nuclearRepulsionAnalyticAndCoincidentRejection() throws IOException {
        var nuclei=JkTestCases.densities().get("h2_rhf").system().nuclei();
        assertEquals(1/1.4,NuclearRepulsion.calculate(new QuantumSystem(nuclei,0,1)).hartree());
        var one=new QuantumSystem(List.of(nuclei.getFirst()),1,1);
        assertEquals(0.,NuclearRepulsion.calculate(one).hartree());
        var coincident=new QuantumSystem(List.of(nuclei.getFirst(),nuclei.getFirst()),0,1);
        assertThrows(IllegalArgumentException.class,()->NuclearRepulsion.calculate(coincident));
        var near=new QuantumSystem(List.of(nuclei.getFirst(),NuclearTestCases.hydrogen(new Point3D(1e-12,0,0))),0,1);
        assertEquals(1e12,NuclearRepulsion.calculate(near).hartree());
    }

    @ParameterizedTest @ValueSource(strings={"h2_arbitrary","h4_arbitrary"})
    void basisPermutationCovariance(String name) throws IOException {
        var p=JkTestCases.densities().get(name); int n=p.size(); var values=new ArrayList<Double>();
        for(int i=0;i<n;i++) for(int j=0;j<n;j++) values.add(p.get(n-1-i,n-1-j));
        var perm=DensityMatrix.fromRowMajor(p.system(),p.functions().reversed(),values);
        var a=RhfStateReceiptReplay.evaluate(p); var b=RhfStateReceiptReplay.evaluate(perm);
        for(int i=0;i<n;i++) for(int j=0;j<n;j++) assertEquals(a.built().density().get(i,j),b.built().density().get(n-1-i,n-1-j),1e-10);
        assertEquals(a.external().totalHartree(),b.external().totalHartree(),1e-10);
        assertEquals(a.constructed().totalHartree(),b.constructed().totalHartree(),1e-10);
        assertNotEquals(a.constructed().receipt().receiptHash(),b.constructed().receipt().receiptHash());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void completeSystemRigidMotion(boolean rotate) throws IOException {
        var p=JkTestCases.densities().get("h4_arbitrary"); var h=Sto3gHydrogen.load();
        var centers=p.system().nuclei().stream().map(n->n.centerBohr()).map(v->rotate?
                new Point3D(.6*v.x()-.8*v.y(),.8*v.x()+.6*v.y(),v.z()):new Point3D(v.x()+2.3,v.y()-1.7,v.z()+.4)).toList();
        var sys=new QuantumSystem(centers.stream().map(NuclearTestCases::hydrogen).toList(),0,1);
        var moved=DensityMatrix.fromRowMajor(sys,centers.stream().map(h::atBohr).toList(),JkTestCases.rowMajor(p));
        var a=RhfStateReceiptReplay.evaluate(p); var b=RhfStateReceiptReplay.evaluate(moved);
        assertEquals(a.external().nuclearHartree(),b.external().nuclearHartree(),1e-12);
        assertEquals(a.external().totalHartree(),b.external().totalHartree(),1e-10);
        assertEquals(a.constructed().totalHartree(),b.constructed().totalHartree(),1e-10);
        for(int i=0;i<p.size();i++) for(int j=0;j<p.size();j++) assertEquals(a.built().density().get(i,j),b.built().density().get(i,j),1e-10);
    }

    @Test void deterministicConstructionForAllReferences() throws IOException {
        for(var p:RhfStateReceiptReplay.inputs().values()) assertEquals(RhfStateReceiptReplay.evaluate(p).replay(),RhfStateReceiptReplay.evaluate(p).replay());
    }

    @ParameterizedTest @ValueSource(strings={"h2_fresh_rhf","h4_arbitrary"})
    void separateJvmByteIdenticalReceipts(String name,@TempDir Path temp) throws Exception {
        byte[] expected=RhfStateReceiptReplay.evaluate(RhfStateReceiptReplay.inputs().get(name)).replay().getBytes(StandardCharsets.UTF_8);
        for(String locale:List.of("en","tr")) {
            Path out=temp.resolve(locale+".receipt");
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Duser.language="+locale,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    RhfStateReceiptReplay.class.getName(),name).redirectOutput(out.toFile()).redirectError(temp.resolve(locale+".err").toFile()).start();
            assertTrue(process.waitFor(15,TimeUnit.SECONDS)); assertEquals(0,process.exitValue());
            assertArrayEquals(expected,Files.readAllBytes(out));
        }
    }

    @Test void allExistingOneShotReceiptsRemainFrozen() throws IOException {
        var canonical=new StringBuilder();
        for(var p:JkTestCases.densities().values()) canonical.append(solve(p).receipt()).append('\n');
        assertEquals("08adb678f556e4bd2823c6a0dac6c842e8cf2c10ab9a1e6e86c9f627a343cd68",ContentHash.sha256(canonical.toString()));
    }
}
