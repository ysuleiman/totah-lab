package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Complete existing aromatic-carbocycle membership assessed against reviewed atom identities. */
final class AllMembersNonpolarRules {
    private AllMembersNonpolarRules() { }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> GROUPS = List.of("ATHENA.GROUP.AROMATIC.RING5", "ATHENA.GROUP.AROMATIC.RING6",
            "ATHENA.PERCEPTION.NONPOLAR.AROMATIC_C_H0", "ATHENA.PERCEPTION.NONPOLAR.AROMATIC_C_H1");
    private static final List<String> NEGATIVE = List.of("VERIFIED_SOURCE_REPORTS", "COMPLETE_CYCLE_AND_ATOM_ENUMERATION",
            "COMPLETE_SOURCE_CORRESPONDENCE", "WITHIN_REQUEST_BUDGET");
    private static final String SCOPE = "EXISTING_AROMATIC_CARBOCYCLE_OCCURRENCE";

    static void validate(RuleManifest m) {
        if (!m.schema().equals("athena-rule/2") || !m.ruleId().equals("ATHENA.PERCEPTION.AROMATIC_CARBOCYCLE.ALL_MEMBERS_NONPOLAR")
                || !m.implementationVersion().equals("1") || !m.profile().equals("ATHENA_ALL_MEMBERS_NONPOLAR_V1")
                || m.family() != RuleManifest.Family.MOTIF || !m.requiredCapabilities().isEmpty()
                || !m.parameters().keySet().equals(Set.of("definitionReference", "sourceManifests"))
                || !m.negativeCoverage().requirements().equals(NEGATIVE))
            throw new IllegalArgumentException("all-members nonpolar manifest contract");
        try { definition(m); sources(m); }
        catch (Exception e) { throw new IllegalArgumentException("invalid all-members nonpolar pins", e); }
    }

    private static ScientificReference definition(RuleManifest m) throws Exception {
        var ref = JSON.readValue(m.parameters().get("definitionReference").value(), ScientificReference.class);
        ref.require(ScientificReference.Kind.SOURCE);
        if (!ref.version().matches("[0-9a-f]{64}")) throw new IllegalArgumentException("definition digest required");
        return ref;
    }

    private static Map<String, RuleManifest> sources(RuleManifest m) throws Exception {
        var result = new TreeMap<String, RuleManifest>();
        var array = JSON.readTree(m.parameters().get("sourceManifests").value());
        if (!array.isArray() || array.size() != GROUPS.size()) throw new IllegalArgumentException("four pinned source definitions required");
        for (var value : array) {
            var source = RuleRegistry.decode(SystemStateView.bytes(value));
            if (!source.implementationId().equals("athena.group") || !source.implementationVersion().equals("2")
                    || source.retired() || !GROUPS.contains(source.ruleId())
                    || result.put(source.ruleId(), source) != null)
                throw new IllegalArgumentException("distinct reviewed group sources required");
        }
        return result;
    }

    static SystemGraphAnalyzer analyzer(RuleManifest m, RuleRequest r) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method() { return new ScientificReference(ScientificReference.Kind.METHOD,
                    "athena.all-members-nonpolar", m.key()+"/evaluate", RuleRegistry.digest(m)); }
            public Set<SystemGraphCertificate.Capability> requires() { return Set.of(); }
            public Set<SystemGraphCertificate.Capability> qualifies() { return Set.of(); }
            public Set<String> evidenceTypes() { return Set.of("athena:group-identities", "athena:system-state"); }
            public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> inputs,Map<String,String> configuration) throws Exception {
                if (!state.binding().equals(r.state()) || !m.key().equals(r.manifestKey()) || !RuleRegistry.digest(m).equals(r.manifestSha256())
                        || !r.atoms().isEmpty() || !r.first().isEmpty() || !r.second().isEmpty()) throw new IllegalArgumentException("whole-component source selection required");
                var manifests=sources(m);var reports=new TreeMap<String,JsonNode>();var envelopes=new TreeMap<String,EvidenceEnvelope>();
                var refs=new HashSet<ScientificReference>();SystemStateView.Component component=null;boolean complete=true;
                for(var envelope:inputs) {
                    if(!envelope.evidenceType().equals("athena:group-identities"))continue;
                    if(!refs.add(envelope.reference()))throw new IllegalArgumentException("duplicate/conflicting source reference");
                    var bytes=envelope.readPayload();if(!EvidenceExchange.sha256(bytes).equals(envelope.payloadSha256()))throw new IllegalArgumentException("source hash mismatch");
                    var report=JSON.readTree(bytes);var source=manifests.get(report.path("definition").path("groupId").asText());
                    if(source==null||reports.put(source.ruleId(),report)!=null)throw new IllegalArgumentException("unreviewed/duplicate source definition");
                    var request=new RuleRequest(state.binding(),source.key(),RuleRegistry.digest(source),List.of(),List.of(),List.of(),r.radiusAngstrom(),r.maximumHops(),r.maximumNodes(),r.maximumCandidates());
                    FunctionalGroupRules.analyzer(source,request,null,true).analyze(state,List.of(envelope),Map.of());
                    var current=state.components().stream().filter(c->node(c.identity()).equals(report.path("componentReference"))).findFirst().orElseThrow();
                    if(component!=null&&!component.identity().equals(current.identity()))throw new IllegalArgumentException("source component mismatch");
                    component=current;envelopes.put(source.ruleId(),envelope);
                    for(var proof:report.path("negativeCoverage"))complete &= proof.isBoolean()&&proof.booleanValue();
                }
                complete &= reports.size()==GROUPS.size() && component!=null && component.correspondenceAlternatives().size()==1;
                if(component!=null)complete &= component.chemistry().atoms().size()<=r.maximumNodes();
                var supported=new TreeSet<String>();
                for(var entry:reports.entrySet())if(entry.getKey().startsWith("ATHENA.PERCEPTION.NONPOLAR.")) {
                    for(var occurrence:entry.getValue().path("occurrences")) {
                        var members=occurrence.path("memberAtomIds");
                        if(members.size()!=1)throw new IllegalArgumentException("atomic source identity must have exactly one member");
                        supported.add(members.get(0).asText());
                    }
                }
                var findings=new TreeMap<String,Finding>();
                for(var entry:reports.entrySet())if(entry.getKey().startsWith("ATHENA.GROUP.AROMATIC.")) {
                    for(var occurrence:entry.getValue().path("occurrences")) {
                        var ids=new TreeSet<String>();occurrence.path("memberAtomIds").forEach(id->ids.add(id.asText()));
                        var missing=new TreeSet<>(ids);missing.removeAll(supported);
                        boolean carbocycle=ids.stream().allMatch(id->state.components().stream()
                                .filter(c->node(c.identity()).equals(entry.getValue().path("componentReference")))
                                .findFirst().orElseThrow().chemistry().atom(id).orElseThrow().element().equals("C"));
                        var assessment=!carbocycle?UNSUPPORTED:missing.isEmpty()?SUPPORTED_PRESENT:complete?ABSENT_FALSE:UNKNOWN_INCONCLUSIVE;
                        var id=occurrence.path("occurrenceId").asText();
                        var members=ids.stream().map(atom->new EvidenceSubject(componentIdentity(entry.getValue()),"chemicalAtom",atom,List.of())).toList();
                        var subject=new EvidenceSubject(state.identity(),"groupOccurrence",id,members);
                        var values=new TreeMap<String,String>();values.put("predicate","ALL_MEMBERS_SATISFY_REVIEWED_NONPOLAR_ATOM_PREDICATE");
                        values.put("assessment",assessment.name());values.put("sourceOccurrenceId",id);
                        values.put("sourceReportSha256",envelopes.get(entry.getKey()).payloadSha256());
                        values.put("sourceDefinitionDigest",entry.getValue().path("definitionDigest").asText());
                        values.put("stateSha256",state.binding().stateSha256());values.put("memberCount",Integer.toString(ids.size()));
                        values.put("supportedMemberCount",Integer.toString(ids.size()-missing.size()));values.put("completeNegativeCoverage",Boolean.toString(complete));
                        findings.put(id,new Finding(id,List.of(subject),status(m,assessment),values,
                                List.of(!carbocycle?"outside aromatic-carbocycle domain":missing.isEmpty()?"every exact source member has a supported aromatic nonpolar atom identity":
                                        (complete?"demonstrated nonmatching members: ":"members lacking supported identity; coverage incomplete: ")+missing),m.limitations()));
                    }
                }
                if(findings.isEmpty())return List.of(new Finding("no-attributed-cycle",List.of(state.subject()),status(m,UNKNOWN_INCONCLUSIVE),
                        Map.of("assessment",UNKNOWN_INCONCLUSIVE.name()),List.of("no verified cycle object available; no group-property absence inferred"),m.limitations()));
                return List.copyOf(findings.values());
            }
        };
    }
    private static EvidenceInterpretation.Status status(RuleManifest m,EvidenceInterpretation.Status assessment) {
        return m.retired()||m.qualification()!=SystemGraphCertificate.Status.QUALIFIED?NOT_EVALUATED:assessment;
    }
    private static ScientificReference componentIdentity(JsonNode report) {
        try{return JSON.treeToValue(report.path("componentReference"),ScientificReference.class);}
        catch(java.io.IOException e){throw new IllegalArgumentException(e);}
    }
    private static JsonNode node(Object value) {
        try{return JSON.readTree(SystemStateView.bytes(value));}
        catch(java.io.IOException e){throw new IllegalArgumentException(e);}
    }
}
