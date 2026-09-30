package totah.lab.aether;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.ElectronRepulsionCalculator;
import totah.lab.aether.provenance.ContentHash;
import static org.junit.jupiter.api.Assertions.*;

/** The legacy full-text path exists only in this deliberately small compatibility test. */
class AetherMemoryHashCompatibilityTest {
    @ParameterizedTest @ValueSource(strings={"h2","h2o","nh3","ch4","co","n2","h2s","ph3","hcl","ch3cl",
            "dms","trimethylsulfonium","chlorobenzene","water_dimer","water_ammonia","methanethiol_water","ammonium_benzene","chlorobenzene_water"})
    void historicalCanonicalEriBytes(String name) throws Exception {
        var pair=InteractionReceiptReplay.pairs().get(name);
        var system=pair!=null?pair.complex():DiisReceiptReplay.systems().get(name);
        for(var family:BasisFamily.values()) {
            var basis=family.forSystem(system);
            // Fixed before evaluation: materialized oracle plus capture fits the 512 MiB test heap.
            if(basis.size()>55)continue;
            try(var capture=new CanonicalByteCapture()) {
            var eri=new ElectronRepulsionCalculator(system,basis).calculate();
            var legacy=new StringBuilder("aether-ERI-hartree-packed-v1\n").append(eri.size()).append('\n').append(eri.uniqueQuartetCount()).append('\n');
            int count=0;
            for(int i=0;i<eri.size();i++)for(int j=0;j<=i;j++) {
                int ij=i*(i+1)/2+j;
                for(int k=0;k<=i;k++)for(int l=0;l<=k;l++) {
                    if(k*(k+1)/2+l>ij)break;
                    double value=eri.get(i,j,k,l);
                    legacy.append(Double.toHexString(value==0?0:value)).append('\n');count++;
                }
            }
            assertEquals(eri.uniqueQuartetCount(),count);
            byte[] historical=legacy.toString().getBytes(StandardCharsets.UTF_8);
            assertTrue(capture.contains(historical),"Production SHA-256 input bytes differ from historical ERI serialization");
            String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(historical));
            assertEquals(expected,eri.receipt().resultHash());
            assertEquals(expected,ContentHash.sha256(legacy.toString()));
            System.out.println("MEMORY_HASH_COMPATIBILITY system="+name+" basis="+family+" slots="+count+" canonical_bytes="+historical.length+" byte_equivalent=true hash="+expected);
            }
        }
    }
}
