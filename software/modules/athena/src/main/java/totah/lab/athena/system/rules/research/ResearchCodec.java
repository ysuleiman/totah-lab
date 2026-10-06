package totah.lab.athena.system.rules.research;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import totah.lab.athena.system.rules.RuleManifest;
import totah.lab.mnemosyne.EvidenceExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The new research codec is deliberately separate from all historical codecs. */
final class ResearchCodec {
    private ResearchCodec() { }
    static final ObjectMapper JSON=JsonMapper.builder().addModule(new JavaTimeModule()).addModule(new Jdk8Module())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    static final Comparator<String> SCALAR_ORDER=(a,b)->Arrays.compare(a.codePoints().toArray(),b.codePoints().toArray());
    static void text(String s) { if(s==null||s.isBlank())throw new IllegalArgumentException("nonblank text required"); unicode(s); }
    static void schema(String s,String expected){if(!expected.equals(s))throw new IllegalArgumentException("unexpected schema/version: "+s);}
    static void hash(String s){if(s==null||!s.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("SHA-256 required");}
    static List<String> strings(List<String> list){var copy=List.copyOf(list);copy.forEach(ResearchCodec::text);return copy;}
    static List<String> stringSet(List<String> list){var copy=new TreeSet<String>(SCALAR_ORDER);for(var s:list){text(s);if(!copy.add(s))throw new IllegalArgumentException("duplicate set identity");}return List.copyOf(copy);}
    static void unicode(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException("unpaired surrogate");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("unpaired surrogate");}}
    static byte[] bytes(Object value){var out=new StringBuilder();write(JSON.valueToTree(value),out);return out.toString().getBytes(StandardCharsets.UTF_8);}
    static String digest(Object value){return EvidenceExchange.sha256(bytes(value));}
    private static void write(JsonNode node,StringBuilder out){
        if(node==null||node.isNull())throw new IllegalArgumentException("null research property");
        if(node.isObject()){
            var keys=new ArrayList<String>();node.fieldNames().forEachRemaining(keys::add);keys.sort(SCALAR_ORDER);
            out.append('{');boolean first=true;for(var key:keys){if(!first)out.append(',');first=false;quote(key,out);out.append(':');write(node.get(key),out);}out.append('}');
        }else if(node.isArray()){
            out.append('[');boolean first=true;for(var item:node){if(!first)out.append(',');first=false;write(item,out);}out.append(']');
        }else if(node.isTextual())quote(node.textValue(),out);
        else if(node.isBoolean())out.append(node.booleanValue());
        else throw new IllegalArgumentException("research scalars are strings or booleans; numeric scientific parameters are strings");
    }
    private static void quote(String s,StringBuilder out){
        unicode(s);out.append('"');for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){
            case '"'->out.append("\\\"");case '\\'->out.append("\\\\");
            case '\b'->out.append("\\b");case '\f'->out.append("\\f");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");
            default->{if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}
        }}out.append('"');
    }
    static <T>T decode(byte[] bytes,Class<T> type)throws IOException {
        if(bytes.length>8*1024*1024)throw new IOException("research metadata exceeds bound");
        // Full canonicalization also checks every nested scalar/key, including escaped surrogates.
        var node=JSON.readTree(bytes);try{var check=new StringBuilder();write(node,check);return JSON.treeToValue(node,type);}
        catch(IllegalArgumentException e){throw new IOException("invalid research artifact",e);}
    }
    static byte[] read(RuleManifest.Source source,ResearchArtifactReader reader,Set<String> checked)throws IOException {
        byte[] bytes=Objects.requireNonNull(reader.read(source),"missing artifact bytes").clone();
        if(!EvidenceExchange.sha256(bytes).equals(source.sha256()))throw new IOException("artifact hash mismatch: "+source.locator());
        checked.add(source.sha256());return bytes;
    }
    static RuleManifest.Source pin(Object value){String h=digest(value);return new RuleManifest.Source("sha256:"+h,h,"Immutable canonical research artifact");}
    static String definition(RuleManifest manifest){
        var root=JSON.valueToTree(manifest);var projected=JSON.createObjectNode();
        for(String key:List.of("schema","ruleId","version","profile","family","tier","implementationId","implementationVersion",
                "requiredCapabilities","requiredChemistry","requiredGeometry","measurementsProduced","classificationStates","parameters",
                "scientificSources","referenceArtifacts","limitations","negativeCoverage")){ if(!manifest.research().projectionVersion().equals("athena-rule-definition-projection/2") || !List.of("implementationId","implementationVersion","referenceArtifacts").contains(key))projected.set(key,root.required(key)); }
        projected.put("domainSha256",manifest.research().domain().sha256());return digest(projected);
    }
    static void verifySources(Object object,ResearchArtifactReader reader,Set<String> checked)throws IOException {
        verifyNodes(JSON.valueToTree(object),reader,checked);
    }
    private static void verifyNodes(JsonNode node,ResearchArtifactReader reader,Set<String> checked)throws IOException {
        if(node.isObject()&&node.has("locator")&&node.has("sha256")&&node.has("citation")){
            var source=JSON.treeToValue(node,RuleManifest.Source.class);read(source,reader,checked);
        }else if(node.isContainerNode())for(var child:node)verifyNodes(child,reader,checked);
    }
}
