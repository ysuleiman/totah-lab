package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import totah.lab.athena.interaction.InteractionThresholds;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.research.*;
import java.time.Instant;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Immutable definitions keyed by identity and content. No script engine or implicit profile fallback. */
public final class RuleRegistry {
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Map<String,RuleManifest> manifests;
    public RuleRegistry(){this(Map.of());}
    private RuleRegistry(Map<String,RuleManifest> manifests){this.manifests=Collections.unmodifiableMap(new TreeMap<>(manifests));}
    public Map<String,RuleManifest> manifests(){return manifests;}
    public static String digest(RuleManifest manifest){return manifest.schema().equals("athena-rule/3")?totah.lab.mnemosyne.EvidenceExchange.sha256(ResearchDocuments.encode(manifest)):SystemStateView.digest(manifest);}
    public static RuleManifest decode(byte[] bytes)throws IOException {
        if(bytes.length>1024*1024)throw new IOException("manifest exceeds bounded metadata size");
        try {
            var root=JSON.readTree(bytes);
            if(root==null||!root.isObject())throw new IllegalArgumentException("manifest object required");
            for(var value:root)if(value.isNull())throw new IllegalArgumentException("null manifest field");
            if(root.path("schema").asText().equals("athena-rule/1")) {
                if(root.has("negativeCoverage"))throw new IllegalArgumentException("legacy coverage field forbidden");
                ((com.fasterxml.jackson.databind.node.ObjectNode)root).putNull("negativeCoverage");
            }
            if(!root.path("schema").asText().equals("athena-rule/3")) {
                if(root.has("research"))throw new IllegalArgumentException("historical research field forbidden");
                ((com.fasterxml.jackson.databind.node.ObjectNode)root).putNull("research");
            }
            var m=JSON.treeToValue(root,RuleManifest.class);validate(m);return m;
        }
        catch(IllegalArgumentException e){throw new IOException("invalid rule manifest",e);}
    }
    public RuleRegistry register(RuleManifest m) {
        validate(m);var prior=manifests.get(m.key());
        if(prior!=null&&!digest(prior).equals(digest(m)))throw new IllegalArgumentException("rule identity/version conflict");
        var next=new TreeMap<>(manifests);next.put(m.key(),m);return new RuleRegistry(next);
    }
    public RuleManifest require(String key,String digest){var m=manifests.get(key);if(m==null||!digest(m).equals(digest))throw new IllegalArgumentException("rule pin mismatch");return m;}
    public VerifiedScientificRule requireCurrent(String key,String digest,RuleQualificationReceipt receipt,
            RulePolicyContext context,ResearchArtifactReader artifacts,Instant at)throws IOException {
        var manifest=require(key,digest);
        if(!manifest.schema().equals("athena-rule/3"))throw new IOException("historical manifest cannot issue current qualification");
        if(receipt.mode()!=QualificationMode.CURRENT||receipt.qualification()!=totah.lab.athena.system.SystemGraphCertificate.Status.QUALIFIED)
            throw new IOException("receipt is not current qualification");
        RuleQualification.verify(manifest,receipt,context,artifacts,at);
        return new VerifiedScientificRule(manifest,receipt);
    }
    public static RuleRegistry load(Path directory)throws IOException {
        var r=new RuleRegistry();try(var paths=Files.list(directory)){
            for(var p:paths.filter(p->p.getFileName().toString().endsWith(".rule.json")).sorted().toList())r=r.register(decode(Files.readAllBytes(p)));
        }return r;
    }
    public static RuleRegistry bundled()throws IOException {
        String prefix="/totah/lab/athena/system/rules/";var registry=new RuleRegistry();
        try(var index=RuleRegistry.class.getResourceAsStream(prefix+"index.txt")) {
            if(index==null)throw new IOException("missing manifest index");
            for(String name:new String(index.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList())
                try(var input=RuleRegistry.class.getResourceAsStream(prefix+name)) {
                    if(input==null)throw new IOException("missing manifest: "+name);
                    registry=registry.register(decode(input.readAllBytes()));
                }
        }return registry;
    }
    public static RuleRegistry scientific()throws IOException {
        var registry=bundled();String prefix="/totah/lab/athena/system/rules/scientific-b00-v2/";
        try(var index=RuleRegistry.class.getResourceAsStream(prefix+"index.txt")) {
            if(index==null)throw new IOException("missing scientific manifest index");
            for(String name:new String(index.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList())
                try(var input=RuleRegistry.class.getResourceAsStream(prefix+name)) {
                    if(input==null)throw new IOException("missing scientific manifest: "+name);
                    registry=registry.register(decode(input.readAllBytes()));
                }
        }return registry;
    }
    private static void validate(RuleManifest m) {
        if(m.implementationId().equals("athena.ss-connectivity")){SourceSulfurConnectivityRules.validate(m);return;}
        if(m.implementationId().equals("athena.all-members-nonpolar")){AllMembersNonpolarRules.validate(m);return;}
        if(m.implementationId().equals("athena.charge-groups")){ChargeGroupRules.validate(m);return;}
        if(m.implementationId().equals("athena.aromatic-systems")){AromaticSystemRules.validate(m);return;}
        if(m.implementationId().equals("athena.geometry")){ContinuousGeometryRules.validate(m);return;}
        if(m.schema().equals("athena-rule/3")) {
            if(!Set.of("athena.group","athena.scientific").contains(m.implementationId()))
                throw new IllegalArgumentException("/3 implementation not registered");
            if(m.implementationId().equals("athena.group"))FunctionalGroupRules.validate(m);else AthenaScientificRules.validate(m);
            return; // Eligibility and execution qualification are separate checked boundaries.
        }
        if(m.implementationId().equals("athena.group")){FunctionalGroupRules.validate(m);return;}
        if(m.implementationId().equals("athena.scientific")) {
            AthenaScientificRules.validate(m);return;
        }
        if(m.negativeCoverage()!=null)throw new IllegalArgumentException("/2 requires its qualified coverage implementation");
        if(!Set.of("native.interaction","native.geometry","native.clash","native.sulfur","external.plip").contains(m.implementationId())||!m.implementationVersion().equals("1"))throw new IllegalArgumentException("implementation not registered");
        if(m.implementationId().equals("external.plip")&&m.qualification()==totah.lab.athena.system.SystemGraphCertificate.Status.QUALIFIED)throw new IllegalArgumentException("PLIP execution parity not qualified");
        if(m.implementationId().equals("external.plip")) {
            String family=switch(m.ruleId()) {
                case "INT.HYDRO.001"->"HYDROPHOBIC";case "INT.HBOND.001"->"HBOND";
                case "INT.PIPI.001"->"PI_STACKING";case "INT.CATPI.001"->"PI_CATION";
                case "INT.SALT.001"->"SALT_BRIDGE";case "INT.HALOGEN.001"->"HALOGEN_BOND";
                case "INT.WATER.001"->"WATER_BRIDGE";case "INT.METAL.001"->"METAL_COMPLEX";
                default->throw new IllegalArgumentException("unknown external rule");
            };
            if(!m.profile().equals("PLIP_3_0_1_"+family))throw new IllegalArgumentException("external profile/family mismatch");
        } else {
            if(!m.profile().startsWith("ATHENA_"))throw new IllegalArgumentException("native implementation cannot masquerade as PLIP");
            if(!m.requiredCapabilities().contains(totah.lab.athena.system.SystemGraphCertificate.Capability.DISTANCE_QUERIES)&&!m.implementationId().equals("native.clash"))throw new IllegalArgumentException("distance prerequisite required");
        }
        RuleManifest.Family expected=switch(m.implementationId()) {
            case "native.interaction","external.plip"->RuleManifest.Family.INTERACTION;
            case "native.clash"->RuleManifest.Family.VALIDATOR;
            case "native.geometry"->RuleManifest.Family.ENVIRONMENT;
            default->m.ruleId().equals("SULF.ENV.001")?RuleManifest.Family.ENVIRONMENT:RuleManifest.Family.MOTIF;
        };
        if(m.family()!=expected)throw new IllegalArgumentException("implementation/family mismatch");
        if(m.implementationId().equals("native.interaction")) {
            RuleAnalyzers.nativeFamily(m.ruleId());thresholds(m);
            if(m.ruleId().equals("INT.HBOND.001")&&!m.requiredCapabilities().contains(totah.lab.athena.system.SystemGraphCertificate.Capability.HBOND_ANALYSIS))throw new IllegalArgumentException("H-bond preparation prerequisite required");
        }
        if(m.implementationId().equals("native.geometry")&&!Set.of("GEO.PROX.001","GEO.SHELL.001","GEO.PATH.001").contains(m.ruleId()))throw new IllegalArgumentException("unknown geometry rule");
        if(m.implementationId().equals("native.clash")) {
            if(!m.ruleId().equals("VAL.CLASH.001")||!m.parameters().keySet().equals(Set.of("radiusScale"))||!m.parameters().get("radiusScale").unit().equals("dimensionless")||!m.requiredCapabilities().contains(totah.lab.athena.system.SystemGraphCertificate.Capability.CLASH_ANALYSIS))throw new IllegalArgumentException("invalid native clash protocol");
            new totah.lab.athena.clash.StericClashAnalysis.Options(Double.parseDouble(m.parameters().get("radiusScale").value()));
        }
        if(m.implementationId().equals("native.sulfur")) {
            if(!Set.of("SULF.SS.001","SULF.VICINAL.001","SULF.PI.001","SULF.ENV.001").contains(m.ruleId()))throw new IllegalArgumentException("unknown sulfur rule");
            if(Set.of("SULF.VICINAL.001","SULF.PI.001").contains(m.ruleId())&&m.qualification()!=totah.lab.athena.system.SystemGraphCertificate.Status.UNSUPPORTED)throw new IllegalArgumentException("sulfur classifier unqualified");
        }
    }
    public static InteractionThresholds thresholds(RuleManifest m) {
        var keys=List.of("minDist","hydrophobicDistMax","saltBridgeDistMax","piStackDistMax","piStackParallelAngleDev","piStackTShapeAngleDev","piStackOffsetMax","piCationDistMax","piCationOffsetMax","piCationTertamineAngleMax","halogenDistMax","halogenAcceptorAngle","halogenDonorAngle","halogenAngleDev","hydrogenAcceptorCutoff","donorAcceptorCutoff","minDonorAngleDegrees","donorBondCutoff");
        if(!m.parameters().keySet().stream().filter(k->m.schema().equals("athena-rule/1")||!k.startsWith("pattern.")).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(keys)))throw new IllegalArgumentException("exact native threshold fields required");
        double[] v=new double[keys.size()];for(int i=0;i<v.length;i++){
            var p=m.parameters().get(keys.get(i));String expected=(keys.get(i).contains("Angle")?"degree":"angstrom");
            if(!p.unit().equals(expected))throw new IllegalArgumentException("threshold unit mismatch");
            if(p.value().equals("UNGATED")&&!keys.get(i).equals("hydrogenAcceptorCutoff"))throw new IllegalArgumentException("ungated field not supported");
            v[i]=p.value().equals("UNGATED")?Double.POSITIVE_INFINITY:Double.parseDouble(p.value());
        }
        return new InteractionThresholds(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11],v[12],v[13],v[14],v[15],v[16],v[17],m.key()+"@"+digest(m));
    }
}
