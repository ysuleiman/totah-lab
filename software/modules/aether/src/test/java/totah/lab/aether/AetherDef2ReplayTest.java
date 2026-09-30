package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import totah.lab.aether.provenance.ContentHash;
import static org.junit.jupiter.api.Assertions.*;

class AetherDef2ReplayTest {
    @Test void freshJava21Receipts() throws Exception {
        for(var example:List.of(List.of("RHF","h2o"),List.of("LDA","h2o"),List.of("CP","water_dimer"))) {
            byte[] expected=Def2ReceiptReplay.receipt(example.get(0),example.get(1)).getBytes(StandardCharsets.UTF_8);
            var out=Files.createTempFile("def2-replay-",".receipt");
            try {
                var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx512m","-cp",System.getProperty("java.class.path"),Def2ReceiptReplay.class.getName(),example.get(0),example.get(1)).redirectOutput(out.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                if(!process.waitFor(10,TimeUnit.MINUTES)){process.destroyForcibly();fail("Replay timeout");}
                assertEquals(0,process.exitValue());assertArrayEquals(expected,Files.readAllBytes(out));
                System.out.println("M13 REPLAY "+example+" "+ContentHash.sha256(expected));
            }finally{Files.deleteIfExists(out);}
        }
    }
    @Test void streamedCanonicalHashIsByteIdentical() {
        var lines=List.of("aether-ERI-hartree-packed-v1","10","1540",ContentHash.number(0),ContentHash.number(-0.0),ContentHash.number(-.37),ContentHash.number(Double.MIN_VALUE));
        assertEquals(ContentHash.sha256(String.join("\n",lines)+"\n"),ContentHash.sha256Lines(lines));
    }
    @Test void incompleteMixedAndDisplacedBasisFailClosed() throws Exception {
        var system=DiisReceiptReplay.systems().get("h2o");var b=Def2SvpBasis.load().forSystem(system);
        assertEquals(RhfScfResult.Status.UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(system,b.subList(0,b.size()-1),1,ScfPolicy.DIIS).status());
        var mixed=new ArrayList<>(b);mixed.set(0,Sto3gBasis.load().forSystem(system).getFirst());
        assertEquals(RhfScfResult.Status.UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(system,mixed,1,ScfPolicy.DIIS).status());
        var wrong=DiisReceiptReplay.systems().get("nh3");
        assertEquals(RhfScfResult.Status.UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(wrong,b,1,ScfPolicy.DIIS).status());
    }
}
