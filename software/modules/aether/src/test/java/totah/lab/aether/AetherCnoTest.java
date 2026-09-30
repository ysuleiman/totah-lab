package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.basis.*;
import totah.lab.aether.integral.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherCnoTest {
    record Evidence(OverlapMatrix s,KineticMatrix t,NuclearAttractionMatrix v,CoreHamiltonianMatrix core,
                    ElectronRepulsionTensor eri,RhfScfResult scf) {}
    @Test void allPracticalIntegralsAndConvergedRHFMatchFreshLibcint() throws IOException {
        var basis=Sto3gBasis.load();var evidence=new TreeMap<String,Evidence>();
        for(var entry:CnoReceiptReplay.systems().entrySet()) {
            var system=entry.getValue();var functions=basis.forSystem(system);long started=System.nanoTime();
            var s=OverlapMatrix.compute(functions);var t=KineticMatrix.compute(functions);var v=NuclearAttractionMatrix.compute(functions,system.nuclei());
            var calculator=new CoreHamiltonianCalculator(system,functions);var core=calculator.calculate(calculator.bind(t),calculator.bind(v));
            var eri=new ElectronRepulsionCalculator(system,functions).calculate();long integralTime=System.nanoTime()-started;
            var scf=RhfScfCalculator.solve(system,functions);
            assertEquals(RhfScfResult.Status.CONVERGED,scf.status(),entry.getKey()+": "+scf.receipt().reason());
            evidence.put(entry.getKey(),new Evidence(s,t,v,core,eri,scf));
            assertTrue(scf.receipt().protocol().contains("s/p-Cartesian"));assertEquals(ScientificStatus.SCREENING_ONLY,scf.scientificStatus());
            System.out.println("M9 "+entry.getKey()+" basis="+functions.size()+" uniqueERI="+eri.uniqueQuartetCount()
                    +" primitiveQuartets="+eri.performanceCounters().primitiveQuartetsEvaluated()+" integralNanos="+integralTime
                    +" SCF="+scf.status()+" E="+scf.convergedState().orElseThrow().energy().totalHartree()+" "+scf.performanceCounters());
        }
        byte[] bytes;
        try(var input=getClass().getResourceAsStream("reference/cno.csv")){assertNotNull(input);bytes=input.readAllBytes();}
        assertEquals("e40fea1a056d2ed016465a3b2477bcb42526460ba9c511c0380629c05ed227e1",ContentHash.sha256(bytes));
        var errors=new TreeMap<String,Double>();int entries=0,eris=0;
        for(String line:new String(bytes,StandardCharsets.UTF_8).lines().skip(1).toList()) {
            var c=line.split(",");var e=evidence.get(c[0]);var state=e.scf.convergedState().orElseThrow();int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]),k=Integer.parseInt(c[4]),l=Integer.parseInt(c[5]);
            double actual=switch(c[1]) {
                case "S" -> e.s.get(i,j);case "T" -> e.t.get(i,j);case "V" -> e.v.get(i,j);case "Hcore" -> e.core.get(i,j);
                case "ERI" -> e.eri.get(i,j,k,l);case "density" -> state.inputDensity().density().get(i,j);
                case "Fock" -> state.orbitals().fock().get(i,j);case "orbital_energies" -> state.orbitals().energies().get(i);
                case "electronic" -> state.energy().electronicHartree();case "nuclear" -> state.energy().nuclearHartree();case "total" -> state.energy().totalHartree();
                case "converged" -> e.scf.status()==RhfScfResult.Status.CONVERGED?1:0;default -> throw new AssertionError(c[1]);
            };
            double error=Math.abs(actual-Double.parseDouble(c[6]));double tolerance=List.of("S","T","V","Hcore","ERI").contains(c[1])?2e-11:1e-8;
            assertEquals(Double.parseDouble(c[6]),actual,tolerance,line);errors.merge(c[1],error,Math::max);entries++;
            if(c[1].equals("ERI")) {
                eris++;
                for(double permutation:new double[]{e.eri.get(j,i,k,l),e.eri.get(i,j,l,k),e.eri.get(j,i,l,k),e.eri.get(k,l,i,j),e.eri.get(l,k,i,j),e.eri.get(k,l,j,i),e.eri.get(l,k,j,i)})assertEquals(actual,permutation);
            }
        }
        assertEquals(10752,eris);System.out.println("M9 entries="+entries+" ERIs="+eris+" errors="+errors);
    }

    @Test void boysOrdersAgainstIndependentHighPrecisionValues() throws IOException {
        byte[] bytes;try(var in=getClass().getResourceAsStream("reference/boys-orders.csv")){assertNotNull(in);bytes=in.readAllBytes();}
        assertEquals("d01c8890205807351f6ec4e5a592b965c780ed105b7359d5760c24ddf3b152f1",ContentHash.sha256(bytes));
        int count=0;
        for(String line:new String(bytes,StandardCharsets.UTF_8).lines().skip(1).toList()) {
            var c=line.split(",");double expected=Double.parseDouble(c[2]);
            assertEquals(expected,BoysFunction.value(Integer.parseInt(c[0]),Double.parseDouble(c[1])),Math.abs(expected)*3e-14,line);count++;
        }
        assertEquals(44,count);
        assertThrows(IllegalArgumentException.class,()->BoysFunction.value(9,1));assertThrows(IllegalArgumentException.class,()->BoysFunction.value(1,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->BoysFunction.value(1,-1));
    }

    @ParameterizedTest @ValueSource(doubles={.2,1,20})
    void analyticCartesianPrimitiveNormalizationKineticAndAttraction(double alpha) {
        var g=new PrimitiveGaussian(new Point3D(0,0,0),alpha);
        for(var p:List.of(CartesianAngularMomentum.PX,CartesianAngularMomentum.PY,CartesianAngularMomentum.PZ)) {
            assertEquals(1,CartesianIntegrals.overlap(g,p,g,p),2e-14);
            assertEquals(2.5*alpha,CartesianIntegrals.kinetic(g,p,g,p),2e-12);
            assertEquals(-4*StrictMath.sqrt(2*alpha)/(3*StrictMath.sqrt(StrictMath.PI)),CartesianIntegrals.attraction(g,p,g,p,List.of(new NuclearCenter(g.centerBohr(),1))),2e-13);
            assertEquals(0,CartesianIntegrals.overlap(g,p,g,CartesianAngularMomentum.S));
            assertTrue(CartesianIntegrals.repulsion(g,p,g,p,g,p,g,p)>0);
        }
        assertEquals(0,CartesianIntegrals.overlap(g,CartesianAngularMomentum.PX,g,CartesianAngularMomentum.PY));
    }

    @Test void primitiveEightfoldSymmetryAndSignedPQuartets() {
        var a=new PrimitiveGaussian(new Point3D(.2,-.5,.7),.4);var b=new PrimitiveGaussian(new Point3D(-.3,.1,.5),1.3);
        var c=new PrimitiveGaussian(new Point3D(.8,.3,-.2),.8);var d=new PrimitiveGaussian(new Point3D(-.4,.9,.1),.6);
        var x=CartesianAngularMomentum.PX;var y=CartesianAngularMomentum.PY;var z=CartesianAngularMomentum.PZ;var s=CartesianAngularMomentum.S;
        double v=CartesianIntegrals.repulsion(a,x,b,y,c,z,d,s);
        for(double value:new double[]{CartesianIntegrals.repulsion(b,y,a,x,c,z,d,s),CartesianIntegrals.repulsion(a,x,b,y,d,s,c,z),
                CartesianIntegrals.repulsion(b,y,a,x,d,s,c,z),CartesianIntegrals.repulsion(c,z,d,s,a,x,b,y),
                CartesianIntegrals.repulsion(d,s,c,z,a,x,b,y),CartesianIntegrals.repulsion(c,z,d,s,b,y,a,x),CartesianIntegrals.repulsion(d,s,c,z,b,y,a,x)})assertEquals(v,value,1e-14);
    }

    @Test void basisOrderingAngularProvenanceAndElementRestrictions() throws IOException {
        var b=Sto3gBasis.load();var origin=new Point3D(0,0,0);
        for(int z:new int[]{6,7,8}) {
            var f=b.atBohr(z,origin);assertEquals(5,f.size());
            assertEquals(List.of(CartesianAngularMomentum.S,CartesianAngularMomentum.S,CartesianAngularMomentum.PX,CartesianAngularMomentum.PY,CartesianAngularMomentum.PZ),f.stream().map(ContractedGaussian::angularMomentum).toList());
            var s=OverlapMatrix.compute(f);for(int i=0;i<5;i++)assertEquals(1,s.get(i,i),2e-14);
            assertNotEquals(OverlapMatrix.compute(List.of(f.get(2))).receipt().basisGeometryHash(),OverlapMatrix.compute(List.of(f.get(3))).receipt().basisGeometryHash());
            assertNotEquals(OverlapMatrix.compute(List.of(f.get(1))).receipt().basisGeometryHash(),OverlapMatrix.compute(List.of(f.get(2))).receipt().basisGeometryHash());
        }
        for(int z:new int[]{11,14,35}) {
            assertThrows(IllegalArgumentException.class,()->b.atBohr(z,origin));
            assertThrows(IllegalArgumentException.class,()->new QuantumSystem(List.of(new NuclearCenter(origin,z)),0,z%2==0?1:2));
        }
        var h=b.atBohr(1,origin).getFirst();assertEquals(OverlapMatrix.compute(List.of(Sto3gHydrogen.load().atBohr(origin))).receipt(),OverlapMatrix.compute(List.of(h)).receipt());
    }

    private static final double[][] ROTATION={{.36,-.48,.8},{.8,.6,0},{-.48,.64,.6}};
    private static Point3D rotate(Point3D v) {
        return new Point3D(.36*v.x()-.48*v.y()+.8*v.z(),.8*v.x()+.6*v.y(),-.48*v.x()+.64*v.y()+.6*v.z());
    }
    private static double[][] aoRotation(List<ContractedGaussian> basis) {
        int n=basis.size();double[][] d=new double[n][n];
        for(int i=0;i<n;i++) {
            if(basis.get(i).angularMomentum()==CartesianAngularMomentum.S)d[i][i]=1;
            else { for(int a=0;a<3;a++)for(int b=0;b<3;b++)d[i+a][i+b]=ROTATION[a][b];i+=2; }
        }
        return d;
    }
    @ParameterizedTest @ValueSource(strings={"h2o","co","ethene"})
    void pAxisRotationCovarianceAndRHFInvariance(String name) throws IOException {
        var system=CnoReceiptReplay.systems().get(name);var library=Sto3gBasis.load();var basis=library.forSystem(system);
        var rotated=new QuantumSystem(system.nuclei().stream().map(n->new NuclearCenter(rotate(n.centerBohr()),n.charge())).toList(),0,1);var rb=library.forSystem(rotated);
        double[][] d=aoRotation(basis);int n=basis.size();
        var s=OverlapMatrix.compute(basis);var rs=OverlapMatrix.compute(rb);var t=KineticMatrix.compute(basis);var rt=KineticMatrix.compute(rb);
        var v=NuclearAttractionMatrix.compute(basis,system.nuclei());var rv=NuclearAttractionMatrix.compute(rb,rotated.nuclei());
        var a=RhfScfCalculator.solve(system,basis);var b=RhfScfCalculator.solve(rotated,rb);
        assertEquals(RhfScfResult.Status.CONVERGED,a.status());assertEquals(RhfScfResult.Status.CONVERGED,b.status());
        var x=a.convergedState().orElseThrow();var y=b.convergedState().orElseThrow();
        assertEquals(x.energy().totalHartree(),y.energy().totalHartree(),1e-10);
        for(int i=0;i<n;i++) {
            assertEquals(x.orbitals().energies().get(i),y.orbitals().energies().get(i),1e-9);
            for(int j=0;j<n;j++) {
                double st=0,tt=0,vt=0,pt=0,ft=0;
                for(int k=0;k<n;k++)for(int l=0;l<n;l++) {
                    double weight=d[i][k]*d[j][l];st+=weight*s.get(k,l);tt+=weight*t.get(k,l);vt+=weight*v.get(k,l);
                    pt+=weight*x.inputDensity().density().get(k,l);ft+=weight*x.orbitals().fock().get(k,l);
                }
                assertEquals(st,rs.get(i,j),2e-12);assertEquals(tt,rt.get(i,j),2e-11);assertEquals(vt,rv.get(i,j),2e-11);
                assertEquals(pt,y.inputDensity().density().get(i,j),2e-9);assertEquals(ft,y.orbitals().fock().get(i,j),2e-9);
            }
        }
    }

    @Test void pERIQuartetsTransformAsRankFourTensor() throws IOException {
        var system=CnoReceiptReplay.systems().get("h2o");var lib=Sto3gBasis.load();var basis=lib.forSystem(system);var d=aoRotation(basis);
        var rotated=new QuantumSystem(system.nuclei().stream().map(n->new NuclearCenter(rotate(n.centerBohr()),n.charge())).toList(),0,1);
        var e=new ElectronRepulsionCalculator(system,basis).calculate();var re=new ElectronRepulsionCalculator(rotated,lib.forSystem(rotated)).calculate();
        for(int i=2;i<5;i++)for(int j=2;j<5;j++)for(int k=2;k<5;k++)for(int l=2;l<5;l++) {
            double value=0;for(int a=2;a<5;a++)for(int b=2;b<5;b++)for(int c=2;c<5;c++)for(int f=2;f<5;f++) value+=d[i][a]*d[j][b]*d[k][c]*d[l][f]*e.get(a,b,c,f);
            assertEquals(value,re.get(i,j,k,l),2e-12);
        }
    }

    @Test void basisPermutationAndTranslation() throws IOException {
        var system=CnoReceiptReplay.systems().get("nh3");var lib=Sto3gBasis.load();var basis=lib.forSystem(system);
        var a=RhfScfCalculator.solve(system,basis).convergedState().orElseThrow();
        var b=RhfScfCalculator.solve(system,basis.reversed()).convergedState().orElseThrow();
        var translated=new QuantumSystem(system.nuclei().stream().map(n->new NuclearCenter(new Point3D(n.centerBohr().x()+2.1,n.centerBohr().y()-.7,n.centerBohr().z()+.2),n.charge())).toList(),0,1);
        var c=RhfScfCalculator.solve(translated,lib.forSystem(translated)).convergedState().orElseThrow();
        assertEquals(a.energy().totalHartree(),b.energy().totalHartree(),1e-10);assertEquals(a.energy().totalHartree(),c.energy().totalHartree(),1e-10);
        int n=basis.size();for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
            assertEquals(a.inputDensity().density().get(i,j),b.inputDensity().density().get(n-1-i,n-1-j),1e-9);
            assertEquals(a.inputDensity().density().get(i,j),c.inputDensity().density().get(i,j),1e-9);
        }
    }

    @Test void hydrogenSCFReceiptsRemainByteFrozen() throws IOException {
        assertEquals("dc52d8dc67a8afbac84b6c9fc9561ec65cb15e21c1d9997e04854886d1337ca8",ContentHash.sha256(ScfReceiptReplay.solve("h2",128).receipt().toString()));
        assertEquals("eec0840f6156f58fd376571d4465cd8c8fb812c33f8c36b15a57aae19fc4c0e7",ContentHash.sha256(ScfReceiptReplay.solve("h4",128).receipt().toString()));
        assertEquals("e061da054a97192b1616112cce112d44a2af891599b9f16de6b8d9d25a9952f2",ContentHash.sha256(ScfReceiptReplay.solve("h2",1).receipt().toString()));
        assertEquals("20e531811ee5028ff35a2f16fa2ad1084c043b012c4b68a443d4fc66a50f890c",ContentHash.sha256(ScfReceiptReplay.solve("h4",1).receipt().toString()));
    }

    @ParameterizedTest @ValueSource(strings={"ch4","nh3","h2o","co","n2","ethene"})
    void separateJvmByteIdenticalAngularReceipts(String name,@TempDir Path temp) throws Exception {
        var system=CnoReceiptReplay.systems().get(name);var basis=Sto3gBasis.load().forSystem(system);
        var expected=RhfScfCalculator.solve(system,basis).receipt().toString();assertEquals(expected,RhfScfCalculator.solve(system,basis).receipt().toString());
        for(String locale:List.of("en","tr")) {
            var out=temp.resolve(locale+".receipt");
            var errors=Path.of("target","child-jvm-diagnostics",name+"-"+locale+".err");
            Files.createDirectories(errors.getParent());
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx256m","-Duser.language="+locale,
                    "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),CnoReceiptReplay.class.getName(),name)
                    .redirectOutput(out.toFile()).redirectError(errors.toFile()).start();
            if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();fail("Child JVM timed out; stderr: "+errors);}
            assertEquals(0,process.exitValue(),()->"Child JVM stderr: "+errors+"; "+readChildErrors(errors));
            assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8),Files.readAllBytes(out));
        }
    }
    private static String readChildErrors(Path errors) {
        try{return Files.readString(errors);}catch(IOException e){return e.toString();}
    }
}
