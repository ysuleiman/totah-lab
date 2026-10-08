package totah.lab.athena.system.rules;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.athena.system.rules.EventPayload.JSON;

class ResidueReferenceTablesTest {
    @Test void exactPinnedUpstreamNumericalCorpus()throws Exception {
        var tables=ResidueReferenceTables.load();int count=0;
        try(var in=getClass().getResourceAsStream("/residue-validation-v1/UPSTREAM_FIXTURES.jsonl.gz");var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(in),StandardCharsets.UTF_8))) {
            String line;while((line=reader.readLine())!=null){var n=JSON.readTree(line);String kind=n.path("kind").asText();if(kind.equals("dihedral"))continue;
                double actual;String category;
                if(kind.equals("rama")){var q=tables.rama(n.path("class").intValue(),n.path("phi").doubleValue(),n.path("psi").doubleValue());actual=q.value();category=q.category();}
                else if(kind.equals("rotamer")){var q=tables.rotamer(n.path("residue").asText(),n.path("chi").doubleValue());actual=q.value();category=q.category();}
                else {actual=n.path("q").doubleValue();category=ResidueReferenceTables.category(actual,kind.equals("rama_category")?ResidueReferenceTables.allowed(n.path("class").intValue()):0.003);}
                assertEquals(Double.doubleToRawLongBits(Double.valueOf(n.path("qHex").asText())),Double.doubleToRawLongBits(actual),line);
                assertEquals(n.path("category").asText(),category,line);count++;
            }
        }assertEquals(196716,count);
    }
    @Test void allUpstreamClassBranchesButNeverMissingOmegaFallback()throws Exception {
        try(var in=getClass().getResourceAsStream("/residue-validation-v1/CLASS_ORACLE.json")){
            var n=JSON.readTree(in);assertEquals(580,n.path("cases").size());
            for(var r:n.path("cases")){
                String id=r.path("central").asText();boolean next=r.path("next").asText().equals("PRO");Double omega=r.path("omega").isNull()?null:r.path("omega").doubleValue();
                if(omega==null)assertThrows(IllegalArgumentException.class,()->ResidueReferenceTables.ramaClass(id,next,null));
                else assertEquals(r.path("upstreamClass").intValue(),ResidueReferenceTables.ramaClass(id,next,omega));
            }
        }
        assertThrows(IllegalArgumentException.class,()->ResidueReferenceTables.ramaClass("UNKNOWN",false,180.0));
    }
    @Test void immutableSourcePackageRejectsMissingChangedAndTruncatedArtifacts()throws Exception {
        assertThrows(IOException.class,()->ResidueReferenceTables.load(p->{throw new IOException("missing");}));
        assertThrows(IOException.class,()->ResidueReferenceTables.load(p->{var b=ResidueReferenceTables.resource(p);if(p.contains("rota8000-ser"))b[b.length-2]^=1;return b;}));
        assertThrows(IOException.class,()->ResidueReferenceTables.load(p->{var b=ResidueReferenceTables.resource(p);return p.endsWith("SOURCES.json")?Arrays.copyOf(b,b.length-1):b;}));
    }
    @Test void malformedSparseGridIsNotValidSparseZero()throws Exception {
        String header="# Table name/description: \"test\"\n# Number of dimensions: 1\n# For each dimension\n#   x1: 0.0 360.0 360 true\n# Value is last\n";
        for(var row:List.of("0.5 0.1\n0.5 0.2\n","0.6 0.1\n","360.5 0.1\n","0.5 NaN\n","0.5\n"))assertThrows(IOException.class,()->ResidueReferenceTables.parseRotamer(header+row));
        var parsed=ResidueReferenceTables.parseRotamer(header+"0.5 0.1\n");assertEquals((float)0.1,parsed[0]);assertEquals(0,parsed[1]);
    }
    @Test void noArbitraryDomainsOrNonfiniteAngleAdmission()throws Exception {
        var t=ResidueReferenceTables.load();assertThrows(IllegalArgumentException.class,()->t.rama(6,0,0));assertThrows(IllegalArgumentException.class,()->t.rama(0,Double.NaN,0));assertThrows(IllegalArgumentException.class,()->t.rama(0,361,0));assertThrows(IllegalArgumentException.class,()->t.rotamer("LYS",0));assertThrows(IllegalArgumentException.class,()->t.rotamer("SER",Double.POSITIVE_INFINITY));
    }
}
