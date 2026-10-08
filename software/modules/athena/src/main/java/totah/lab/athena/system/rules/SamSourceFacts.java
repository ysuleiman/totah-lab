package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.research.ResearchDocuments;
import totah.lab.mnemosyne.*;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Exact G05 source assertions. Neither a binding nor a completeness claim is scientific authority. */
final class SamSourceFacts {
    static final String DEFINITION="ATHENA.G05.SAM_CHEBI_142094_SOURCE_IDENTITY/1";
    static final String FACT="ATHENA.G05.SAM_SOURCE_FACT";
    static final String REFERENCE="034fde087f28026075bc49a63557207bcc60252359a73c8739afff956937f974";
    static final List<String> CATEGORIES=List.of("elements","isotopes","formalCharges","hydrogens","aromaticity","bondOrders","stereochemistry","electronicState","connections");
    record Checked(SystemStateView.Component component, JsonNode binding, Map<String,String> mapping,
                   EvidenceInterpretation.Status status, List<String> reasons, List<EvidenceEnvelope> witnesses,
                   List<EvidenceEnvelope> consumed) { }
    private SamSourceFacts() { }
    static JsonNode reference() throws java.io.IOException {
        try(var in=SamSourceFacts.class.getResourceAsStream("sam-g05-v1/reference.json")) {
            if(in==null)throw new java.io.IOException("G05 reference missing");var bytes=in.readAllBytes();require(EvidenceExchange.sha256(bytes).equals("14edc439aa30d49e6db345c9c2c4d80112fffb941122b11a7215f028e79b2b31"),"G05 reference-table digest mismatch");return JSON.readTree(bytes);
        }
    }
    static Checked check(SystemStateView state,EvidenceEnvelope envelope,Map<String,EvidenceEnvelope> inputs,
                         List<totah.lab.gaia.structure.AtomReference> selection) throws Exception {
        var n=read(envelope);
        fields(n,"schema","definition","stateBinding","componentReference","referenceArtifact","sourceCoverage","referenceAtomMap","sourceHydrogenAtomIds","sourceScope","sourceKind","preparationReferences","coherenceWitnesses","factWitnesses","limitations");
        require(text(n,"schema").equals("athena-sam-source-binding/1")&&text(n,"definition").equals(DEFINITION),"G05 binding definition");
        require(binding(n.get("stateBinding")).equals(state.binding()),"G05 source state mismatch");
        var component=state.components().stream().filter(c->JSON.valueToTree(c.identity()).equals(n.get("componentReference"))).findFirst().orElseThrow(()->new IllegalArgumentException("G05 component absent"));
        var consumed=new LinkedHashMap<String,EvidenceEnvelope>();consumed.put(canonical(pin(envelope)),envelope);
        var ref=take(n.get("referenceArtifact"),inputs,consumed);require(ref.payloadSha256().equals(REFERENCE),"G05 exact reference bytes required");
        var cov=take(n.get("sourceCoverage"),inputs,consumed);require(cov.evidenceType().equals("athena:group-source-coverage"),"G05 source coverage type");
        var coverage=read(cov);fields(coverage,"schema","stateBinding","componentReference","completeGraph","atomState","sourceReferences","limitations");
        require(text(coverage,"schema").equals("athena-group-source-coverage/1")&&coverage.get("stateBinding").equals(n.get("stateBinding"))&&coverage.get("componentReference").equals(n.get("componentReference")),"G05 coverage binding mismatch");
        var reasons=new TreeSet<String>();boolean unknown=false,outside=false;
        for(var other:inputs.values())if(other.evidenceType().equals("athena:group-source-coverage")) {
            var o=read(other);if(o.path("componentReference").equals(n.get("componentReference"))&&!o.equals(coverage)){unknown=true;reasons.add("Conflicting source coverage");}
        }
        require(Set.of("SOURCE","PREPARED_SOURCE","DERIVED").contains(text(n,"sourceKind")),"G05 source kind");
        var preparations=array(n,"preparationReferences");require(n.path("sourceKind").asText().equals("SOURCE")||!preparations.isEmpty(),"G05 preparation lineage missing");
        uniquePins(preparations);for(var p:preparations)take(p,inputs,consumed);
        if(n.path("sourceKind").asText().equals("DERIVED")){outside=true;reasons.add("No derived-state profile admitted");}
        require(Set.of("COMPLETE_ORDINARY","KNOWN_NONORDINARY","UNKNOWN").contains(text(n,"sourceScope")),"G05 scope token");
        for(var v:array(n,"limitations"))require(v.isTextual()&&!v.asText().isBlank(),"G05 limitation string");
        var graph=component.chemistry();graph.validateTopology(true);
        var map=new TreeMap<String,String>();var values=new HashSet<String>();require(n.path("referenceAtomMap").isObject(),"G05 reference map object");
        for(var it=n.path("referenceAtomMap").fields();it.hasNext();) {
            var e=it.next();require(e.getKey().matches("[1-9]|1[0-9]|2[0-7]")&&e.getValue().isTextual()&&!e.getValue().asText().isBlank()&&values.add(e.getValue().asText()),"G05 malformed/noninjective reference map");map.put(e.getKey(),e.getValue().asText());
        }
        var graphIds=new TreeSet<String>();graph.atoms().forEach(a->graphIds.add(a.id()));
        var heavyIds=new TreeSet<String>();graph.atoms().stream().filter(a->!a.element().equals("H")).forEach(a->heavyIds.add(a.id()));
        var explicitHs=graph.atoms().stream().filter(a->a.element().equals("H")).map(MolecularGraph.Atom::id).sorted().toList();
        require(sortedStrings(n.get("sourceHydrogenAtomIds")).equals(explicitHs),"G05 explicit H inventory mismatch");
        if(!values.equals(heavyIds)){unknown=true;reasons.add("Incomplete full reference correspondence");}
        else if(map.size()!=27){outside=true;reasons.add("Complete source component has a different heavy-atom composition");}
        if(component.correspondenceAlternatives().size()!=1){unknown=true;reasons.add("Ambiguous coordinate correspondence");}
        else {
            var cm=component.correspondenceAlternatives().getFirst();
            if(!cm.keySet().equals(graphIds)||new HashSet<>(cm.values()).size()!=cm.size()){unknown=true;reasons.add("Incomplete source-coordinate correspondence");}
            var expectedBonds=new HashSet<totah.lab.gaia.structure.Bond>();
            for(var b:graph.bonds())if(cm.containsKey(b.firstAtomId())&&cm.containsKey(b.secondAtomId()))expectedBonds.add(new totah.lab.gaia.structure.Bond(cm.get(b.firstAtomId()),cm.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name())));
            var actualBonds=new HashSet<totah.lab.gaia.structure.Bond>();state.graph().structure().bonds().stream().filter(b->cm.containsValue(b.atom1())||cm.containsValue(b.atom2())).forEach(actualBonds::add);
            if(!actualBonds.equals(expectedBonds)||state.graph().structure().getConnectivityMetadata().provenance()!=totah.lab.gaia.structure.ConnectivityProvenance.EXPLICIT){unknown=true;reasons.add("Source graph and structural connectivity do not agree");}
            for(var a:graph.atoms()) {
                var ar=cm.get(a.id());var coordinateAtom=ar==null?null:state.atoms().get(ar);
                if(coordinateAtom==null||coordinateAtom.getElement()==null||!coordinateAtom.getElement().name().equalsIgnoreCase(a.element())||!state.charges().charges().containsKey(ar)||state.charges().charge(ar)!=a.formalCharge()){unknown=true;reasons.add("Source-to-coordinate element/charge correspondence conflicts");}
                else if(a.coordinates()!=null) {var xyz=a.coordinates();var position=coordinateAtom.getPosition();if(xyz.x()!=position.x()||xyz.y()!=position.y()||xyz.z()!=position.z()){unknown=true;reasons.add("Conflicting source coordinate frames");}}
            }
            for(var other:state.components())if(!other.identity().equals(component.identity()))for(var alt:other.correspondenceAlternatives())if(alt.values().stream().anyMatch(cm.values()::contains)){unknown=true;reasons.add("Overlapping source occurrences");}
        }
        if(!coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT")){unknown=true;reasons.add("Incomplete original graph evidence");}
        fields(n.get("factWitnesses"),CATEGORIES.toArray(String[]::new));
        var facts=new TreeMap<String,JsonNode>();var witnesses=new ArrayList<EvidenceEnvelope>();
        for(var category:java.util.stream.Stream.concat(CATEGORIES.stream(),java.util.stream.Stream.of("coherence")).toList()) {
            var pins=category.equals("coherence")?array(n,"coherenceWitnesses"):array(n.get("factWitnesses"),category);uniquePins(pins);
            if(pins.isEmpty()){unknown=true;reasons.add("Missing "+category+" witness");continue;}
            JsonNode fact=null;EvidenceInterpretation.Status previousStatus=null;
            for(var p:pins) {
                var e=take(p,inputs,consumed);require(e.evidenceType().equals("athena:event-source"),"G05 witness evidence type");
                var record=new EvidenceExchange().decodeRecord(e.readPayload());require(record instanceof EvidenceInterpretation,"G05 source interpretation required");
                var i=(EvidenceInterpretation)record;var v=i.measurements();
                require(v.keySet().equals(Set.of("proposition","stateBinding","componentReference","category","factValues","sourceProtocol","sourceLocations","preparationReferences")),"G05 witness fields");
                require((FACT+"/1").equals(v.get("proposition"))&&category.equals(v.get("category"))&&canonical(state.binding()).equals(v.get("stateBinding"))&&canonical(component.identity()).equals(v.get("componentReference")),"G05 witness state/category mismatch");
                require(e.method().equals(i.evaluator())&&!i.subjects().isEmpty()&&i.subjects().stream().allMatch(s->s.state().equals(state.identity())),"G05 witness method/subject mismatch");
                var dependencies=new HashSet<String>();for(var d:i.inputs()){var pin=JSON.valueToTree(d);take(pin,inputs,consumed);dependencies.add(canonical(pin));}
                var protocol=JSON.readTree(v.get("sourceProtocol"));require(dependencies.contains(canonical(protocol)),"G05 unbound source protocol");take(protocol,inputs,consumed);
                var prep=JSON.readTree(v.get("preparationReferences"));require(prep.equals(n.get("preparationReferences")),"G05 witness preparation mismatch");for(var pp:prep)require(dependencies.contains(canonical(pp)),"G05 unbound preparation");
                locations(JSON.readTree(v.get("sourceLocations")),dependencies,inputs,consumed);
                var fv=JSON.readTree(v.get("factValues"));validateFact(category,fv,graphIds,dependencies,inputs,consumed);
                if(fact!=null&&(!fact.equals(fv)||previousStatus!=i.status())){unknown=true;reasons.add("Conflicting "+category+" witnesses");}
                fact=fv;previousStatus=i.status();witnesses.add(e);
                if(i.status()!=SUPPORTED_PRESENT){unknown=true;reasons.add("Unresolved "+category+" source facts");}
            }
            facts.put(category,fact);
        }
        var selectedWitnesses=new HashSet<>(witnesses.stream().map(EvidenceEnvelope::reference).toList());
        for(var e:inputs.values())if(e.evidenceType().equals("athena:event-source")&&!selectedWitnesses.contains(e.reference())) {
            var record=new EvidenceExchange().decodeRecord(e.readPayload());
            if(record instanceof EvidenceInterpretation i&&(FACT+"/1").equals(i.measurements().get("proposition"))&&canonical(state.binding()).equals(i.measurements().get("stateBinding"))&&canonical(component.identity()).equals(i.measurements().get("componentReference"))) {
                unknown=true;reasons.add("Additional competing source witness was not resolved by this binding");
            }
        }
        if(facts.size()!=10)unknown=true;
        for(var category:List.of("elements","isotopes","formalCharges","aromaticity","hydrogens","stereochemistry")) {
            if(!facts.containsKey(category))continue;
            var keys=new TreeSet<String>();facts.get(category).path("atoms").fieldNames().forEachRemaining(keys::add);
            if(!keys.equals(Set.of("hydrogens","stereochemistry").contains(category)?heavyIds:graphIds)){unknown=true;reasons.add("Incomplete "+category+" atom facts");}
        }
        // Validate every independently asserted fact against the immutable source graph, before domain testing.
        if(!unknown) {
            for(var a:graph.atoms()) {
                var id=a.id();var f=coverage.path("atomState").path(id);
                if(!facts.get("elements").path("atoms").path(id).asText().equals(a.element())||!facts.get("isotopes").path("atoms").get(id).equals(JSON.valueToTree(a.isotope()))||facts.get("formalCharges").path("atoms").path(id).intValue()!=a.formalCharge()||facts.get("aromaticity").path("atoms").path(id).booleanValue()!=a.aromatic())unknown=true;
                if(!f.isObject()){unknown=true;continue;}
                fields(f,"chargeStatus","formalCharge","hydrogenMode","explicitHydrogenAtomIds","implicitHydrogenCount","aromaticityStatus","aromaticityModel","evidenceReferences");
                if(!f.path("chargeStatus").asText().equals("SUPPORTED_PRESENT")||!f.path("formalCharge").equals(JSON.valueToTree(a.formalCharge()))||!f.path("aromaticityStatus").asText().equals("SUPPORTED_PRESENT")||!f.path("aromaticityModel").asText().equals(facts.get("aromaticity").path("model").asText()))unknown=true;
                if(a.isotope()!=null)outside=true;
                String electronic=a.properties().get("athena.ocl.atomRadicalState/1");if(electronic==null||!Set.of("NONE","S","D","T").contains(electronic))unknown=true;else if(!electronic.equals("NONE"))outside=true;
                if(!a.element().equals("H")) {
                    var h=facts.get("hydrogens").path("atoms").path(id);var actual=hydrogenNeighbors(graph,id);
                    if(!sortedStrings(h.path("explicitAtomIds")).equals(actual)||!f.path("explicitHydrogenAtomIds").equals(h.path("explicitAtomIds"))||!f.path("implicitHydrogenCount").equals(h.path("authoritativeCount")))unknown=true;
                    if(!Set.of("EXPLICIT_GRAPH","AUTHORITATIVE_IMPLICIT").contains(f.path("hydrogenMode").asText())||(f.path("hydrogenMode").asText().equals("EXPLICIT_GRAPH")&&h.path("authoritativeCount").intValue()!=0))unknown=true;
                    if(a.explicitHydrogens()!=0&&a.explicitHydrogens()!=h.path("authoritativeCount").intValue())unknown=true;
                } else if(a.formalCharge()!=0||a.aromatic()||graph.bonds().stream().filter(b->incident(b,id)).count()!=1)outside=true;
            }
            if(!bonds(graph,false).equals(facts.get("bondOrders").get("bonds"))||!bonds(graph,true).equals(facts.get("aromaticity").get("bonds")))unknown=true;
            var scope=facts.get("connections");if(!scope.path("ordinaryBondDigest").asText().equals(hash(graph.bonds()))||!scope.path("coverage").asText().equals(n.path("sourceScope").asText()))unknown=true;
            if(scope.path("coverage").asText().equals("UNKNOWN"))unknown=true;
            if(scope.path("coverage").asText().equals("KNOWN_NONORDINARY"))outside=true;
            String electronic=facts.get("electronicState").path("state").asText();
            boolean nonNone=graph.atoms().stream().anyMatch(a->Set.of("S","D","T").contains(a.properties().getOrDefault("athena.ocl.atomRadicalState/1","UNKNOWN")));
            if(electronic.equals("UNKNOWN")||electronic.equals("NONE")&&nonNone||electronic.equals("KNOWN_NON_NONE")&&!nonNone)unknown=true;
            else if(electronic.equals("KNOWN_NON_NONE"))outside=true;
            var coherence=facts.get("coherence");if(!coherence.path("stateBinding").equals(JSON.valueToTree(state.binding())))unknown=true;
            var expected=new TreeSet<totah.lab.gaia.structure.AtomReference>();expected.addAll(component.correspondenceAlternatives().getFirst().values());expected.addAll(selection);
            if(!coherence.path("selection").equals(JSON.valueToTree(expected)))unknown=true;
        }
        if(unknown)reasons.add("Missing, inconsistent or conflicting source facts; no identity inference");
        // Fixed reference correspondence, not perception or normalization. H counts come only from witnesses.
        if(!unknown&&map.size()==27) {
            var reference=reference();var byReference=new HashMap<String,MolecularGraph.Atom>();for(var e:map.entrySet())byReference.put(e.getKey(),graph.atom(e.getValue()).orElseThrow());
            for(var expected:reference.path("atoms")) {
                var a=byReference.get(expected.path("id").asText());var h=facts.get("hydrogens").path("atoms").path(a.id());
                if(!a.element().equals(expected.path("element").asText())||a.formalCharge()!=expected.path("formalCharge").intValue()||a.aromatic()!=expected.path("aromatic").booleanValue()||hydrogenNeighbors(graph,a.id()).size()+h.path("authoritativeCount").intValue()!=expected.path("hydrogenCount").intValue())outside=true;
                String stereo=facts.get("stereochemistry").path("atoms").path(a.id()).asText();String needed=expected.path("stereo").asText();
                // Source-bound absolute R/S assertions are reviewed independently. Relative parity alone is never compared.
                if(needed.equals("NONE")){if(!Set.of("NONE","UNSPECIFIED").contains(stereo))unknown=true;}
                else if(!Set.of("R","S").contains(stereo)||a.stereochemistry().equals("NONE"))unknown=true;
                else if(!stereo.equals(needed))outside=true;
                if(Set.of("R","S").contains(a.stereochemistry())&&!a.stereochemistry().equals(stereo))unknown=true;
            }
            if(graph.bonds().stream().filter(b->heavyIds.contains(b.firstAtomId())&&heavyIds.contains(b.secondAtomId())).count()!=29)outside=true;
            for(var b:reference.path("bonds")) {
                String first=map.get(b.path("first").asText()),second=map.get(b.path("second").asText());
                var actual=graph.bonds().stream().filter(x->Set.of(x.firstAtomId(),x.secondAtomId()).equals(Set.of(first,second))).findFirst();
                if(actual.isEmpty()){outside=true;continue;}
                var a=actual.get();boolean aromatic=b.path("aromatic").booleanValue();
                if(a.aromatic()!=aromatic||!(a.order().name().equals(b.path("order").asText())||aromatic&&a.order()==MolecularGraph.BondOrder.AROMATIC))outside=true;
            }
        }
        if(outside)reasons.add("Known chemistry outside exact ChEBI142094 source domain");
        var status=unknown?UNKNOWN_INCONCLUSIVE:outside?UNSUPPORTED:SUPPORTED_PRESENT;
        return new Checked(component,n,Collections.unmodifiableMap(map),status,List.copyOf(reasons),List.copyOf(witnesses),List.copyOf(consumed.values()));
    }
    static boolean authorized(Checked checked,SystemStateView state,Map<String,EvidenceEnvelope> inputs,Instant at) throws Exception {
        // Invocation-local verification only: one immutable receipt check per selected source manifest.
        var reviewed=new ArrayList<Set<String>>();
        for(var envelope:inputs.values())if(envelope.evidenceType().equals("athena:rule-manifest")) {
            var m=ResearchDocuments.decode(envelope.readPayload(),RuleManifest.class);
            if(!m.ruleId().equals(FACT)||!m.schema().equals("athena-rule/3"))continue;
            var time=S1Qualification.current(m,state,null,inputs);
            if(time.isPresent()&&time.get().equals(at))reviewed.add(new HashSet<>(m.scientificSources().stream().map(RuleManifest.Source::sha256).toList()));
        }
        for(var e:checked.witnesses()) {
            var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(e.readPayload());var protocol=JSON.readTree(i.measurements().get("sourceProtocol"));
            if(!i.recordedAt().equals(at)||reviewed.stream().noneMatch(p->p.contains(e.payloadSha256())&&p.contains(protocol.path("sha256").asText())))return false;
        }
        return !checked.witnesses().isEmpty();
    }
    private static EvidenceEnvelope take(JsonNode pin,Map<String,EvidenceEnvelope> inputs,Map<String,EvidenceEnvelope> consumed)throws Exception {var e=pinned(pin,inputs);consumed.put(canonical(pin),e);return e;}
    private static void uniquePins(List<JsonNode> pins){require(new HashSet<>(pins).size()==pins.size(),"duplicate G05 pins");}
    static List<String> sortedStrings(JsonNode n){require(n.isArray(),"G05 string array");var a=new ArrayList<String>();for(var s:n){require(s.isTextual()&&!s.asText().isBlank(),"G05 nonblank string");a.add(s.asText());}require(a.equals(a.stream().distinct().sorted().toList()),"G05 sorted unique IDs required");return a;}
    private static boolean incident(MolecularGraph.Bond b,String id){return b.firstAtomId().equals(id)||b.secondAtomId().equals(id);}
    private static List<String> hydrogenNeighbors(MolecularGraph g,String id){var ids=new ArrayList<String>();for(var b:g.bonds())if(incident(b,id)){String other=b.firstAtomId().equals(id)?b.secondAtomId():b.firstAtomId();if(g.atom(other).orElseThrow().element().equals("H")){require(b.order()==MolecularGraph.BondOrder.SINGLE&&!b.aromatic(),"G05 invalid source H bond");ids.add(other);}}return ids.stream().sorted().toList();}
    static JsonNode bonds(MolecularGraph g,boolean aromatic){var list=new ArrayList<Map<String,Object>>();for(var b:g.bonds()){var ids=List.of(b.firstAtomId(),b.secondAtomId()).stream().sorted().toList();list.add(Map.of("first",ids.get(0),"second",ids.get(1),aromatic?"aromatic":"order",aromatic?b.aromatic():b.order().name()));}list.sort(Comparator.comparing(x->x.get("first")+"\u0000"+x.get("second")));return JSON.valueToTree(list);}
    private static void locations(JsonNode n,Set<String> dependencies,Map<String,EvidenceEnvelope> inputs,Map<String,EvidenceEnvelope> consumed)throws Exception {
        require(n.isArray()&&!n.isEmpty(),"G05 original source locations required");for(var l:n){fields(l,"artifact","selector");text(l,"selector");require(dependencies.contains(canonical(l.get("artifact"))),"G05 original source pin missing from witness inputs");var e=take(l.get("artifact"),inputs,consumed);require(e.evidenceType().equals("athena:source-artifact"),"G05 original source artifact type");}
    }
    private static void validateFact(String category,JsonNode f,Set<String> ids,Set<String> deps,Map<String,EvidenceEnvelope> inputs,Map<String,EvidenceEnvelope> consumed)throws Exception {
        switch(category) {
            case "elements","isotopes","formalCharges","hydrogens" -> fields(f,"atoms");
            case "aromaticity" -> {fields(f,"model","atoms","bonds");text(f,"model");}
            case "bondOrders" -> fields(f,"bonds");
            case "stereochemistry" -> {fields(f,"atoms","protocol");require(deps.contains(canonical(f.get("protocol"))),"G05 stereo protocol not independently pinned");take(f.get("protocol"),inputs,consumed);}
            case "electronicState" -> {fields(f,"state");require(Set.of("NONE","KNOWN_NON_NONE","UNKNOWN").contains(text(f,"state")),"G05 electronic state");}
            case "connections" -> {
                fields(f,"coverage","ordinaryBondDigest","nonordinary");digest(f,"ordinaryBondDigest");require(Set.of("COMPLETE_ORDINARY","KNOWN_NONORDINARY","UNKNOWN").contains(text(f,"coverage")),"G05 connection state");
                var nonordinary=array(f,"nonordinary");require(!f.path("coverage").asText().equals("KNOWN_NONORDINARY")||!nonordinary.isEmpty(),"Known connection needs exact witness");require(!f.path("coverage").asText().equals("COMPLETE_ORDINARY")||nonordinary.isEmpty(),"Ordinary coverage contradicts connections");
                for(var c:nonordinary){fields(c,"first","second","kind","source","selector");for(var k:List.of("first","second","kind","selector"))text(c,k);require(deps.contains(canonical(c.get("source"))),"Unpinned exact nonordinary source");take(c.get("source"),inputs,consumed);}
            }
            case "coherence" -> {fields(f,"stateBinding","selection","sourceLocations");binding(f.get("stateBinding"));require(f.path("selection").isArray(),"G05 coherent selection array");locations(f.get("sourceLocations"),deps,inputs,consumed);}
            default -> throw new IllegalArgumentException("Unknown G05 fact category");
        }
        if(f.has("atoms")) {
            require(f.get("atoms").isObject(),"G05 atom facts object");for(var it=f.get("atoms").fields();it.hasNext();) {
                var e=it.next();require(ids.contains(e.getKey()),"G05 unknown source atom");var v=e.getValue();
                switch(category) {
                    case "isotopes" -> require(v.isNull()||v.isIntegralNumber()&&v.canConvertToInt()&&v.intValue()>0,"G05 isotope value");
                    case "formalCharges" -> require(v.isIntegralNumber()&&v.canConvertToInt(),"G05 integer charge");
                    case "hydrogens" -> {fields(v,"explicitAtomIds","authoritativeCount");sortedStrings(v.get("explicitAtomIds"));require(v.path("authoritativeCount").isIntegralNumber()&&v.path("authoritativeCount").canConvertToInt()&&v.path("authoritativeCount").intValue()>=0,"G05 source H count");}
                    case "aromaticity" -> require(v.isBoolean(),"G05 aromaticity Boolean");
                    default -> require(v.isTextual()&&!v.asText().isBlank(),"G05 source token");
                }
            }
        }
        if(f.has("bonds")) {
            var seen=new TreeSet<String>();String previous="";
            for(var b:array(f,"bonds")){fields(b,"first","second",category.equals("aromaticity")?"aromatic":"order");String a=text(b,"first"),z=text(b,"second"),key=a+"\u0000"+z;require(ids.contains(a)&&ids.contains(z)&&a.compareTo(z)<0&&seen.add(key)&&previous.compareTo(key)<0,"G05 sorted unique source bonds");previous=key;if(category.equals("aromaticity"))require(b.path("aromatic").isBoolean(),"G05 bond aromatic flag");else require(Set.of("SINGLE","DOUBLE","TRIPLE","AROMATIC").contains(text(b,"order")),"G05 ordinary source order");}
        }
    }
}
