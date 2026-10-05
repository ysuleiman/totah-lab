package totah.lab.athena.system.rules;

import totah.lab.athena.system.SystemGraphCertificate;
import totah.lab.mnemosyne.EvidenceInterpretation;
import totah.lab.athena.system.rules.research.ResearchBinding;
import java.util.*;

/** Definition/provenance only. Execution belongs to registered Java implementations. */
public record RuleManifest(String schema, String ruleId, String version, String profile, Family family,
                           String tier, String implementationId, String implementationVersion,
                           SystemGraphCertificate.Status qualification, boolean retired,
                           List<SystemGraphCertificate.Capability> requiredCapabilities,
                           List<String> requiredChemistry, List<String> requiredGeometry,
                           Map<String,String> measurementsProduced, List<EvidenceInterpretation.Status> classificationStates,
                           Map<String,Parameter> parameters, List<Source> scientificSources,
                           List<Source> referenceArtifacts, List<String> limitations,
                           @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                           NegativeCoverage negativeCoverage,
                           @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                           ResearchBinding research) {
    /** Versioned necessary conditions for a negative, not an assertion that the input satisfies them. */
    public record NegativeCoverage(String version, String supportedDomain,
                                   List<String> requirements, String scope, String incompleteStatus) {
        public NegativeCoverage {
            text(version);text(supportedDomain);text(scope);
            requirements=List.copyOf(requirements);requirements.forEach(RuleManifest::text);
            if(requirements.isEmpty()||new HashSet<>(requirements).size()!=requirements.size()
                    ||!incompleteStatus.equals("UNKNOWN_INCONCLUSIVE"))throw new IllegalArgumentException("invalid negative coverage policy");
        }
    }
    /** Preserve source compatibility and canonical /1 bytes; /1 never implicitly acquires a /2 policy. */
    public RuleManifest(String schema,String ruleId,String version,String profile,Family family,String tier,
                        String implementationId,String implementationVersion,SystemGraphCertificate.Status qualification,
                        boolean retired,List<SystemGraphCertificate.Capability> requiredCapabilities,List<String> requiredChemistry,
                        List<String> requiredGeometry,Map<String,String> measurementsProduced,List<EvidenceInterpretation.Status> classificationStates,
                        Map<String,Parameter> parameters,List<Source> scientificSources,List<Source> referenceArtifacts,List<String> limitations) {
        this(schema,ruleId,version,profile,family,tier,implementationId,implementationVersion,qualification,retired,
                requiredCapabilities,requiredChemistry,requiredGeometry,measurementsProduced,classificationStates,parameters,
                scientificSources,referenceArtifacts,limitations,null);
    }
    /** Preserve the /2 constructor and its serialized form. */
    public RuleManifest(String schema,String ruleId,String version,String profile,Family family,String tier,
                        String implementationId,String implementationVersion,SystemGraphCertificate.Status qualification,boolean retired,
                        List<SystemGraphCertificate.Capability> requiredCapabilities,List<String> requiredChemistry,List<String> requiredGeometry,
                        Map<String,String> measurementsProduced,List<EvidenceInterpretation.Status> classificationStates,
                        Map<String,Parameter> parameters,List<Source> scientificSources,List<Source> referenceArtifacts,List<String> limitations,
                        NegativeCoverage negativeCoverage) {
        this(schema,ruleId,version,profile,family,tier,implementationId,implementationVersion,qualification,retired,
                requiredCapabilities,requiredChemistry,requiredGeometry,measurementsProduced,classificationStates,parameters,
                scientificSources,referenceArtifacts,limitations,negativeCoverage,null);
    }
    public enum Family { VALIDATOR, INTERACTION, MOTIF, ENVIRONMENT }
    public record Parameter(String value, String unit, String definition) {
        public Parameter {
            text(value);text(definition);
            if(!Set.of("angstrom","degree","dimensionless","policy").contains(unit))throw new IllegalArgumentException("unknown unit");
            if(!unit.equals("policy")&&!value.equals("UNGATED")&&!Double.isFinite(Double.parseDouble(value)))throw new IllegalArgumentException("nonfinite parameter");
        }
    }
    public record Source(String locator, String sha256, String citation) {
        public Source {text(locator);text(citation);if(!sha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("source digest required");}
    }
    public RuleManifest {
        if(!Set.of("athena-rule/1","athena-rule/2","athena-rule/3").contains(schema))throw new IllegalArgumentException("unknown rule schema");
        if(!schema.equals("athena-rule/1")&&negativeCoverage==null)throw new IllegalArgumentException("versioned negative coverage required");
        if(schema.equals("athena-rule/1")&&negativeCoverage!=null)throw new IllegalArgumentException("legacy schema cannot acquire a new coverage policy");
        if(schema.equals("athena-rule/3")) {
            Objects.requireNonNull(research,"research binding required");
            if(qualification!=SystemGraphCertificate.Status.NOT_EVALUATED)throw new IllegalArgumentException("/3 cannot self-certify");
        }else if(research!=null)throw new IllegalArgumentException("historical manifest cannot acquire research binding");
        for(var s:List.of(ruleId,version,profile,tier,implementationId,implementationVersion))text(s);
        Objects.requireNonNull(family);Objects.requireNonNull(qualification);
        requiredCapabilities=List.copyOf(requiredCapabilities);requiredChemistry=List.copyOf(requiredChemistry);requiredGeometry=List.copyOf(requiredGeometry);
        measurementsProduced=Collections.unmodifiableMap(new TreeMap<>(measurementsProduced));classificationStates=List.copyOf(classificationStates);
        parameters=Collections.unmodifiableMap(new TreeMap<>(parameters));scientificSources=List.copyOf(scientificSources);referenceArtifacts=List.copyOf(referenceArtifacts);limitations=List.copyOf(limitations);
        if(classificationStates.size()!=EvidenceInterpretation.Status.values().length)throw new IllegalArgumentException("exactly six unique assessment states required");
        requiredChemistry.forEach(RuleManifest::text);requiredGeometry.forEach(RuleManifest::text);limitations.forEach(RuleManifest::text);
        measurementsProduced.forEach((k,v)->{text(k);text(v);});
        if(scientificSources.isEmpty()||limitations.isEmpty()||!new HashSet<>(classificationStates).equals(EnumSet.allOf(EvidenceInterpretation.Status.class)))throw new IllegalArgumentException("sources, limitations and all six states required");
    }
    public String key(){return ruleId+"/"+profile+"/"+version;}
    private static void text(String text){if(text==null||text.isBlank())throw new IllegalArgumentException("nonblank manifest field required");}
}
