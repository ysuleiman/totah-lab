package totah.lab.aether.matrix;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.CanonicalByteCapture;
import static org.junit.jupiter.api.Assertions.*;

class AetherIncrementalHashTest {
    private static String historical(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void utf8AndChunkBoundariesPreserveHistoricalBytes() throws Exception {
        for(String text:new String[]{"","ASCII\n", "λ雪\uD83D\uDE00", "x".repeat(4095)+"\uD83D\uDE00"+"z".repeat(9000),"bad\uD800end\uDC00","terminal\uD800"}) {
            try(var capture=new CanonicalByteCapture()) {
            String streamed=ContentHash.sha256(text);
            assertTrue(capture.contains(text.getBytes(StandardCharsets.UTF_8)));
            assertEquals(historical(text),streamed);
            assertEquals(historical(text),ContentHash.sha256(text));
            var hash=ContentHash.accumulator();
            for(int i=0;i<text.length();i++)hash.append(text.substring(i,i+1));
            assertEquals(historical(text),hash.finish());
            assertThrows(IllegalStateException.class,hash::finish);
            assertThrows(IllegalStateException.class,()->hash.append("x"));
            }
        }
    }
    @Test void historicalGridBlockTreeIncludingPartialAndEmptyBlocks() throws Exception {
        for(int size:new int[]{0,1,1023,1024,1025,100_003}) {
            try(var capture=new CanonicalByteCapture()) {
            var current=new NumericalEvidenceHash("test-domain");
            var block=new StringBuilder();var outer=new StringBuilder("test-domain;blocks-of-1024-canonical-numbers\n");
            for(int i=0;i<size;i++) {
                double value=i%7==0?-0.0:StrictMath.scalb((i%31)-15.25,i%40-20);
                current.add(value);block.append(Double.toHexString(value==0?0:value)).append('\n');
                if((i+1)%1024==0){outer.append(historical(block.toString())).append('\n');block.setLength(0);}
            }
            if(!block.isEmpty())outer.append(historical(block.toString())).append('\n');
            outer.append("count=").append(size);
            String streamed=current.finish();
            assertTrue(capture.contains(outer.toString().getBytes(StandardCharsets.UTF_8)));
            assertEquals(historical(outer.toString()),streamed);
            }
        }
    }
    @Test void matrixAndSpectrumCanonicalOrderUnchanged() throws Exception {
        double[][] values={{0,-.25},{Double.MIN_VALUE,13.5}};
        var expected=new StringBuilder("aether-diis-matrix-v1\n2\n2\n");
        for(var row:values)for(double v:row)expected.append(Double.toHexString(v)).append('\n');
        assertEquals(historical(expected.toString()),PulayDiis.hash(new Array2DRowRealMatrix(values)));
        assertEquals(historical("spectrum\n2\n0x0.0p0\n-0x1.0p-2\n"),SpectralValues.capture(values[0],"spectrum").resultHash());
    }
}
