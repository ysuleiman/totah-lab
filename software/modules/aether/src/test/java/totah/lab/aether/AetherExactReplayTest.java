package totah.lab.aether;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import totah.lab.aether.provenance.ContentHash;
import static org.junit.jupiter.api.Assertions.*;

class AetherExactReplayTest {
    @Test void separateJava21JvmsReproduceRHFBlockedLdaAndCounterpoise()throws Exception {
        for(var example:List.of(List.of("RHF","h2o"),List.of("LDA","h2o"),List.of("CP","water_dimer"))) {
            byte[] expected=null;
            for(int replay=0;replay<2;replay++) {
                var out=Files.createTempFile("aether-exact-14-",".receipt");
                try {
                    var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx512m","-cp",System.getProperty("java.class.path"),
                            ExactReceiptReplay.class.getName(),example.get(0),example.get(1)).redirectOutput(out.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                    if(!child.waitFor(10,TimeUnit.MINUTES)){child.destroyForcibly();fail("M14 replay timeout");}
                    assertEquals(0,child.exitValue());var actual=Files.readAllBytes(out);
                    if(expected==null)expected=actual;else assertArrayEquals(expected,actual);
                }finally{Files.deleteIfExists(out);}
            }
            System.out.println("M14_SEPARATE_JVM_REPLAY "+example+" SHA256="+ContentHash.sha256(expected));
        }
    }
}
