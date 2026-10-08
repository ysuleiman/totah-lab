package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import totah.lab.mnemosyne.EvidenceExchange;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact immutable source catalog. Loading conveys neither qualification nor scientific authority. */
final class AdvisoryAlertCatalog {
    static final String FORMAT="OCL_IDCODE_QUERY/2026.7.2";
    static final String JAR="2e1642f29f09c1def25dc405a77973c28bae02355a6807320e42f1dc4cd157c9";
    static final String ORDERED="d0e92e06907a6bcb9e8a0cb59674a6410b42816b65e05e725b269fb2e736f7c2";
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    record Entry(int index,String label,String query,String querySha256) { }
    private final List<Entry> entries;
    private AdvisoryAlertCatalog(List<Entry> entries){this.entries=List.copyOf(entries);}
    List<Entry> entries(){return entries;}
    static AdvisoryAlertCatalog decode(byte[] bytes)throws java.io.IOException {
        var d=JSON.readTree(bytes);
        fields(d,Set.of("schema","catalogId","catalogVersion","sourceJarSha256","orderedCatalogSha256","entries"));
        equal(d,"schema","athena-advisory-alert-catalog/1");equal(d,"catalogId","OCL.PAINS");equal(d,"catalogVersion","2026.7.2");equal(d,"sourceJarSha256",JAR);equal(d,"orderedCatalogSha256",ORDERED);
        if(!d.path("entries").isArray()||d.path("entries").size()!=890)throw new IllegalArgumentException("exact890 entries required");
        var entries=new ArrayList<Entry>();var ordered=new StringBuilder();
        for(var e:d.get("entries")){
            fields(e,Set.of("index","label","queryFormat","query","querySha256"));
            if(!e.path("index").isIntegralNumber()||!e.path("index").canConvertToInt()||e.path("index").intValue()!=entries.size())throw new IllegalArgumentException("catalog index/order");
            equal(e,"queryFormat",FORMAT);String query=text(e,"query"),label=text(e,"label"),hash=text(e,"querySha256");
            if(!sha(query).equals(hash))throw new IllegalArgumentException("original query bytes/hash");
            ordered.append(query.length()).append(':').append(query).append(label.length()).append(':').append(label);
            entries.add(new Entry(entries.size(),label,query,hash));
        }
        if(!sha(ordered.toString()).equals(ORDERED))throw new IllegalArgumentException("original ordered catalog identity");
        return new AdvisoryAlertCatalog(entries);
    }
    private static String sha(String s){return EvidenceExchange.sha256(s.getBytes(StandardCharsets.UTF_8));}
    private static String text(JsonNode n,String k){if(!n.path(k).isTextual()||n.path(k).asText().isEmpty())throw new IllegalArgumentException("catalog field "+k);return n.path(k).asText();}
    private static void equal(JsonNode n,String k,String v){if(!text(n,k).equals(v))throw new IllegalArgumentException("catalog identity "+k);}
    private static void fields(JsonNode n,Set<String> keys){if(n==null||!n.isObject())throw new IllegalArgumentException("catalog object required");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(keys))throw new IllegalArgumentException("catalog exact fields");}
}
