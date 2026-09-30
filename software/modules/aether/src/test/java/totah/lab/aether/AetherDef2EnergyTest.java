package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.provenance.ContentHash;
import static org.junit.jupiter.api.Assertions.*;

class AetherDef2EnergyTest {
    static java.util.stream.Stream<String> referenceSystems() {
        return Arrays.stream(System.getProperty("aether.def2.system","h2 h2o nh3 ch4 co n2 h2s ph3 hcl ch3cl chlorobenzene dms trimethylsulfonium").split(" "));
    }
    @ParameterizedTest @MethodSource("referenceSystems") void energyReference(String name) throws Exception {
        try(var heap=new HeapObservation(name+" mode="+System.getProperty("aether.def2.method","BOTH"))) {
        var system=DiisReceiptReplay.systems().get(name);var b=Def2SvpBasis.load().forSystem(system);
        byte[] bytes;try(var in=getClass().getResourceAsStream("reference/def2-energy-"+name+".csv")){bytes=Objects.requireNonNull(in).readAllBytes();}
        try(var in=getClass().getResourceAsStream("reference/def2-energy-"+name+".sha256")){assertEquals(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.US_ASCII).trim(),ContentHash.sha256(bytes));}
        String mode=System.getProperty("aether.def2.method","BOTH");assertTrue(Set.of("BOTH","RHF","LDA").contains(mode));
        var out=Path.of("target/def2-validation");Files.createDirectories(out);
        long started=System.nanoTime();System.out.println("M13 START "+name+" basis="+b.size()+" method="+mode);
        if(!mode.equals("LDA")) {
            var rhf=RhfScfCalculator.solve(system,b,ScfPolicy.DIIS);
            System.out.println("M13 RHF "+name+" "+rhf.status()+" "+rhf.receipt().reason()+" "+rhf.performanceCounters()+" "+rhf.basisPerformance());
            Files.writeString(out.resolve(name+"-rhf.receipt"),rhf.receipt().toString());
            assertEquals(RhfScfResult.Status.CONVERGED,rhf.status());var state=rhf.convergedState().orElseThrow();
            var rhfArrays=RetainedScientificArrays.bytes(rhf);
            assertEquals(8L*b.size()*b.size()*(5L*rhf.iterations().size()+1),rhfArrays.get("SCF_MATRIX_BYTES").longValue());
            System.out.println("MEMORY_RETAINED_EVIDENCE "+name+" RHF "+rhfArrays);
            compare(name,"RHF",bytes,c->{
                int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]);
                return switch(c[1]) {
                    case "converged"->1;case "total"->state.energy().totalHartree();case "electronic"->state.energy().electronicHartree();
                    case "density"->state.density().get(i,j);case "Fock"->state.physicalOrbitals().fock().get(i,j);
                    case "orbital"->state.physicalOrbitals().energies().get(i);default->throw new AssertionError(c[1]);
                };
            });
        }
        if(!mode.equals("RHF")) {
            var ks=KohnShamCalculator.solve(system,b,new GridDefinition(120,590),LdaFunctional.EXCHANGE_PZ81);
            System.out.println("M13 LDA "+name+" "+ks.status()+" "+ks.receipt().reason()+" "+ks.performance()+" "+ks.basisPerformance());
            Files.writeString(out.resolve(name+"-lda.receipt"),ks.receipt().toString());
            assertEquals(RhfScfResult.Status.CONVERGED,ks.status());var state=ks.convergedState().orElseThrow();
            var ksArrays=RetainedScientificArrays.bytes(ks);
            assertEquals(32L*b.size()*b.size(),ksArrays.get("SCF_MATRIX_BYTES").longValue());
            System.out.println("MEMORY_RETAINED_EVIDENCE "+name+" LDA "+ksArrays);
            compare(name,"LDA",bytes,c->{
                int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]);
                return switch(c[1]) {
                    case "converged"->1;case "total"->state.energy().totalHartree();case "electronic"->state.energy().electronicHartree();
                    case "density"->state.density().get(i,j);case "Fock"->state.fock().get(i,j);case "orbital"->state.orbitals().energy(i);
                    case "electrons"->state.xc().integratedElectrons();case "Exc"->state.xc().energyHartree();default->throw new AssertionError(c[1]);
                };
            });
        }
        System.out.println("M13 RUNTIME "+name+" "+(System.nanoTime()-started)/1e9);
        }
    }
    private static void compare(String name,String method,byte[] bytes,ToDoubleFunction<String[]> actual) throws Exception {
        var errors=new TreeMap<String,Double>();int entries=0;
        for(var line:new String(bytes,StandardCharsets.US_ASCII).lines().skip(1).toList()) {
            var c=line.split(",");if(!c[0].equals(method))continue;
            double expected=Double.parseDouble(c[4]),value=actual.applyAsDouble(c);
            errors.merge(method+"_"+c[1],Math.abs(value-expected),Math::max);assertEquals(expected,value,1e-8,line);entries++;
        }
        assertTrue(entries>0);String evidence="M13 ENERGY "+name+" method="+method+" entries="+entries+" errors="+errors;
        System.out.println(evidence);Files.writeString(Path.of("target/def2-validation",name+"-"+method+"-validation.txt"),evidence+"\n");
    }
}
