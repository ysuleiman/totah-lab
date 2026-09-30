package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.*;
import totah.lab.aether.provenance.ContentHash;
import static org.junit.jupiter.api.Assertions.*;

class AetherDef2CounterpoiseTest {
    static java.util.stream.Stream<String> referenceSystems() {
        return Arrays.stream(System.getProperty("aether.def2.dimer","water_dimer water_ammonia methanethiol_water ammonium_benzene chlorobenzene_water").split(" "));
    }
    @ParameterizedTest @MethodSource("referenceSystems") void referenceComponents(String name) throws Exception {
        try(var heap=new HeapObservation(name+" mode=CP")) {
        var pair=InteractionReceiptReplay.pairs().get(name);long start=System.nanoTime();
        System.out.println("M13 CP START "+name);
        var result=InteractionEnergyCalculator.calculate(pair,BasisFamily.DEF2_SVP);
        var dir=Path.of("target/def2-validation");Files.createDirectories(dir);
        Files.writeString(dir.resolve(name+"-cp.receipt"),InteractionReceiptReplay.receipts(result));
        System.out.println("MEMORY_RETAINED_EVIDENCE "+name+" CP "+RetainedScientificArrays.bytes(result));
        for(var c:result.components())System.out.println("M13 CP "+name+" "+c.role()+" "+c.calculation().status()+" "+c.calculation().performanceCounters());
        byte[] bytes;try(var in=getClass().getResourceAsStream("reference/def2-cp-"+name+".csv")){bytes=Objects.requireNonNull(in).readAllBytes();}
        try(var in=getClass().getResourceAsStream("reference/def2-cp-"+name+".sha256")){assertEquals(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.US_ASCII).trim(),ContentHash.sha256(bytes));}
        double max=0;
        for(var line:new String(bytes,StandardCharsets.US_ASCII).lines().skip(1).toList()) {
            var c=line.split(",");double expected=Double.parseDouble(c[2]),actual;
            if(c[0].equals("INTERACTION")) actual=c[1].equals("uncorrected")?result.receipt().uncorrectedHartree().orElseThrow():result.receipt().counterpoiseHartree().orElseThrow();
            else {
                var state=result.components().stream().filter(x->x.role().name().equals(c[0])).findFirst().orElseThrow().calculation().convergedState().orElseThrow();
                actual=switch(c[1]) {case "converged"->1;case "total"->state.energy().totalHartree();case "electronic"->state.energy().electronicHartree();case "nuclear"->state.energy().nuclearHartree();default->throw new AssertionError(c[1]);};
            }
            assertEquals(expected,actual,1e-8,line);max=Math.max(max,Math.abs(actual-expected));
        }
        System.out.println("M13 CP RESULT "+name+" unc="+result.receipt().uncorrectedHartree()+" cp="+result.receipt().counterpoiseHartree()+" maxError="+max+" seconds="+(System.nanoTime()-start)/1e9);
        }
    }
}
