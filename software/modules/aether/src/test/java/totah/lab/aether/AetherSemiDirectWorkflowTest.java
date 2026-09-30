package totah.lab.aether;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.aether.basis.*;
import totah.lab.aether.matrix.*;
import static org.junit.jupiter.api.Assertions.*;

class AetherSemiDirectWorkflowTest {
    @TempDir Path root;
    @Test void waterRHFMatchesFrozenReferenceAndReusesCache()throws Exception {
        var output=root.resolve("out");var cache=root.resolve("cache");
        SemiDirectProbe.main(new String[]{"RHF","h2o",output.toString(),cache.toString()});
        byte[] first=Files.readAllBytes(output.resolve("h2o-RHF.receipt"));
        SemiDirectProbe.main(new String[]{"RHF","h2o",output.toString(),cache.toString()});
        assertArrayEquals(first,Files.readAllBytes(output.resolve("h2o-RHF.receipt")));
        assertTrue(Files.readString(output.resolve("h2o-RHF.txt")).contains("eriGenerationCount=0"));
    }
    @Test void waterCounterpoiseReusesOnlyFullBasisERIAndMatchesFrozenReference()throws Exception {
        var output=root.resolve("out");SemiDirectProbe.main(new String[]{"CP","water_dimer",output.toString(),root.resolve("cache").toString()});
        String text=Files.readString(output.resolve("water_dimer-CP.txt"));
        var identities=new HashSet<String>();int reused=0;
        for(String line:text.split("\n"))if(line.startsWith("COMPLEX ")||line.startsWith("A_WITH_GHOST_B ")||line.startsWith("B_WITH_GHOST_A ")) {
            identities.add(line.split("cacheIdentity=")[1].split(" ")[0]);if(line.contains("generated=false"))reused++;
        }
        assertEquals(1,identities.size());assertEquals(2,reused);
    }
    @Test void freshJvmsReproduceColdAndWarmRHFAndCounterpoiseReceipts()throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();String cp=System.getProperty("java.class.path");
        for(String mode:List.of("RHF","CP")) {
            String name=mode.equals("RHF")?"h2o":"water_dimer";Path cache=root.resolve(mode+"-cache");byte[] previous=null;
            for(int run=0;run<2;run++) {
                Path output=root.resolve(mode+run);Path log=root.resolve(mode+run+".log");
                var process=new ProcessBuilder(java,"-Xmx512m","-cp",cp,SemiDirectProbe.class.getName(),mode,name,output.toString(),cache.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                assertEquals(0,process.waitFor(),Files.readString(log));byte[] receipt=Files.readAllBytes(output.resolve(name+"-"+mode+".receipt"));
                if(previous!=null)assertArrayEquals(previous,receipt);previous=receipt;
            }
        }
    }
}
