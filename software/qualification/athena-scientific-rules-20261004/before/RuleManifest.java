package totah.lab.athena.system.rules;

import totah.lab.athena.system.SystemGraphCertificate;
import totah.lab.mnemosyne.EvidenceInterpretation;
import java.util.*;

/** Definition/provenance only. Execution belongs to registered Java implementations. */
public record RuleManifest(String schema, String ruleId, String version, String profile, Family family,
                           String tier, String implementationId, String implementationVersion,
                           SystemGraphCertificate.Status qualification, boolean retired,
                           List<SystemGraphCertificate.Capability> requiredCapabilities,
                           List<String> requiredChemistry, List<String> requiredGeometry,
                           Map<String,String> measurementsProduced, List<EvidenceInterpretation.Status> classificationStates,
                           Map<String,Parameter> parameters, List<Source> scientificSources,
                           List<Source> referenceArtifacts, List<String> limitations) {
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
        if(!"athena-rule/1".equals(schema))throw new IllegalArgumentException("unknown rule schema");
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
