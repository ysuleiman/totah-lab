package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.research.ResearchDocuments;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Exact selected residue source assertions. A label, coverage declaration or caller status is not authority. */
final class ResidueContextSource {
    static final String FACT="ATHENA.V09_V10.RESIDUE_CONTEXT_SOURCE_FACT";
    static final String ADMISSION="ATHENA.V09_V10.REVIEWED_SELECTED_RESIDUE_CONTEXT/1";
    static final Set<String> IDENTITIES=Set.of("ALA","ARG","ASN","ASP","CYS","GLN","GLU","GLY","HIS","ILE","LEU","LYS","MET","PHE","PRO","SER","THR","TRP","TYR","VAL");
    static final List<String> CATEGORIES=List.of("identityState","atomCorrespondence","connections","stereochemistry","preparation","coherence");
    static final String[] ROLES={"N","CA","C","O","CB","OG","OG1","CG1","CG2","CD1","ND1","NE2","CD"};
    static final Set<String> SCOPES=Set.of("COMPLETE_ORDINARY","KNOWN_NONORDINARY","UNKNOWN");
    record Checked(JsonNode binding,Map<String,EvidenceInterpretation.Status> statuses,Map<String,JsonNode> facts,
                   List<EvidenceEnvelope> witnesses,List<EvidenceEnvelope> consumed,List<String> reasons,
                   Map<String,AtomReference> coordinates) {
        EvidenceInterpretation.Status status(){return combine(statuses.values());}
        String identity(String position){
            var selected=binding.path(position);if(selected.isNull()||!facts.containsKey("identityState"))return null;
            for(var r:facts.get("identityState").path("residues"))if(r.path("residue").equals(selected.path("residue")))return r.path("canonicalIdentity").isTextual()?r.path("canonicalIdentity").asText():null;
            return null;
        }
        AtomReference coordinate(String position,String role){var n=binding.path(position).path("roles").path(role);return n.isObject()?coordinates.get(canonical(n)):null;}
    }
    private ResidueContextSource() { }
    static EvidenceInterpretation.Status combine(Collection<EvidenceInterpretation.Status> values){
        if(values.contains(UNKNOWN_INCONCLUSIVE)||values.contains(FAILED))return UNKNOWN_INCONCLUSIVE;
        if(values.contains(UNSUPPORTED))return UNSUPPORTED;
        if(values.contains(NOT_EVALUATED))return NOT_EVALUATED;
        return SUPPORTED_PRESENT;
    }
    private static final class Check {
        final SystemStateView state;final Map<String,EvidenceEnvelope> inputs;final JsonNode binding;
        final Map<String,EvidenceInterpretation.Status> statuses=new TreeMap<>();
        final Map<String,JsonNode> selections=new TreeMap<>(),facts=new TreeMap<>(),atomFacts=new TreeMap<>(),coverage=new TreeMap<>();
        final Map<String,AtomReference> coordinates=new TreeMap<>();final Map<String,JsonNode> sourceAtoms=new TreeMap<>();
        final Map<String,EvidenceEnvelope> consumed=new TreeMap<>();final List<EvidenceEnvelope> witnesses=new ArrayList<>();
        final TreeSet<String> reasons=new TreeSet<>();
        Check(SystemStateView state,Map<String,EvidenceEnvelope> inputs,JsonNode binding){this.state=state;this.inputs=inputs;this.binding=binding;for(var c:CATEGORIES)statuses.put(c,SUPPORTED_PRESENT);}
        void unknown(String c,String why){statuses.put(c,UNKNOWN_INCONCLUSIVE);reasons.add(why);}
        void outside(String c,String why){if(statuses.get(c)!=UNKNOWN_INCONCLUSIVE)statuses.put(c,UNSUPPORTED);reasons.add(why);}
        EvidenceEnvelope take(JsonNode p)throws IOException {var e=pinned(p,inputs);consumed.put(canonical(p),e);return e;}
        void pins(JsonNode list,boolean ordered)throws IOException {
            require(list.isArray(),"pin array");var keys=new ArrayList<String>();for(var p:list){take(p);keys.add(canonical(p));}
            require(keys.size()==new HashSet<>(keys).size(),"duplicate/cyclic preparation pins");
            if(!ordered)require(keys.equals(keys.stream().sorted().toList()),"sorted pin array required");
        }
        void locations(JsonNode list)throws IOException {
            require(list.isArray(),"source locations array");if(list.isEmpty())unknown("identityState","Original source locations missing");
            var seen=new HashSet<String>();for(var l:list){fields(l,"artifact","selector");text(l,"selector");require(seen.add(canonical(l)),"duplicate source location");require(take(l.get("artifact")).evidenceType().equals("athena:source-artifact"),"original source artifact required");}
        }
        MolecularGraph.Atom atom(JsonNode a)throws IOException {
            fields(a,"component","atomId");var c=component(a);return c.chemistry().atom(text(a,"atomId")).orElseThrow(()->new IllegalArgumentException("source atom absent"));
        }
        SystemStateView.Component component(JsonNode a)throws IOException {
            var ref=reference(a.get("component"));return state.components().stream().filter(c->c.identity().equals(ref)).findFirst().orElseThrow(()->new IllegalArgumentException("source component absent"));
        }
        Set<String> neighbors(JsonNode a,boolean hydrogen)throws IOException {
            var g=component(a).chemistry();var out=new TreeSet<String>();String id=text(a,"atomId");
            for(var b:g.bonds())if(b.firstAtomId().equals(id)||b.secondAtomId().equals(id)){
                String other=b.firstAtomId().equals(id)?b.secondAtomId():b.firstAtomId();
                if(!hydrogen||g.atom(other).orElseThrow().element().equals("H"))out.add(canonical(source(a.get("component"),other)));
            }return out;
        }
        void selection(JsonNode n)throws IOException {
            fields(n,"residue","sourceLocations","candidateIdentity","stateDescription","atoms","roles");residue(n.get("residue"));locations(n.get("sourceLocations"));
            nullableToken(n.get("candidateIdentity"),IDENTITIES);nullableText(n.get("stateDescription"));fields(n.get("roles"),ROLES);
            String key=canonical(n.get("residue"));require(selections.putIfAbsent(key,n)==null,"duplicate selected residue");
            var members=new TreeSet<String>();String previous="";var mapped=new HashSet<AtomReference>();
            for(var item:array(n,"atoms")) {
                fields(item,"source","coordinate");var a=item.get("source");atom(a);String ak=canonical(a);
                require(previous.compareTo(ak)<0&&members.add(ak)&&sourceAtoms.putIfAbsent(ak,a)==null,"sorted unique nonoverlapping source membership");previous=ak;
                AtomReference coordinate=item.path("coordinate").isNull()?null:coordinate(item.get("coordinate"));
                if(coordinate!=null){require(mapped.add(coordinate),"coordinate identity collision");require(state.atoms().containsKey(coordinate),"coordinate absent from bound state");require(sameResidue(coordinate,n.get("residue")),"coordinate residue mismatch");coordinates.put(ak,coordinate);}
                var alternatives=component(a).correspondenceAlternatives();
                if(alternatives.isEmpty())unknown("atomCorrespondence","No source correspondence authority");
                for(var map:alternatives)if(!Objects.equals(map.get(text(a,"atomId")),coordinate))unknown("atomCorrespondence","Source correspondence alternatives disagree");
            }
            require(!members.isEmpty(),"empty residue membership");var roles=new HashSet<String>();
            for(var role:ROLES){var a=n.path("roles").get(role);if(!a.isNull()){atom(a);require(members.contains(canonical(a))&&roles.add(canonical(a)),"role must be distinct selected source member");}}
            // Detect a coordinate-addressed atom silently omitted from the declared residue membership.
            for(var c:state.components())for(var map:c.correspondenceAlternatives())for(var entry:map.entrySet())if(sameResidue(entry.getValue(),n.get("residue"))&&!members.contains(canonical(source(JSON.valueToTree(c.identity()),entry.getKey()))))unknown("atomCorrespondence","Source residue membership omitted a mapped atom");
        }
        void coverage()throws IOException {
            pins(binding.get("sourceCoverage"),false);
            for(var p:binding.get("sourceCoverage")) {
                var e=take(p);require(e.evidenceType().equals("athena:group-source-coverage"),"source coverage type");var n=read(e);
                fields(n,"schema","stateBinding","componentReference","completeGraph","atomState","sourceReferences","limitations");
                require(text(n,"schema").equals("athena-group-source-coverage/1")&&n.get("stateBinding").equals(binding.get("stateBinding")),"source coverage state");
                String key=canonical(n.get("componentReference"));var prior=coverage.putIfAbsent(key,n);if(prior!=null&&!prior.equals(n))unknown("identityState","Conflicting coverage");
                if(!n.path("completeGraph").asText().equals("SUPPORTED_PRESENT"))unknown("connections","Incomplete original connection coverage");
            }
            for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage")) {
                var n=read(e);var selected=coverage.get(canonical(n.path("componentReference")));
                if(selected!=null&&!selected.equals(n))unknown("identityState","Competing source coverage omitted");
            }
        }
        void bond(JsonNode b)throws IOException {
            fields(b,"first","second","order","aromatic","sourceLocations");atom(b.get("first"));atom(b.get("second"));token(b,"order",Set.of("SINGLE","DOUBLE","TRIPLE","AROMATIC"));bool(b.get("aromatic"));locations(b.get("sourceLocations"));
            require(canonical(b.get("first")).compareTo(canonical(b.get("second")))<0,"canonical bond endpoints");
        }
        String bondKey(JsonNode b){return canonical(b.get("first"))+"\u0000"+canonical(b.get("second"));}
        Map<String,JsonNode> bonds(JsonNode list)throws IOException {
            require(list.isArray(),"bond array");var out=new TreeMap<String,JsonNode>();String previous="";
            for(var b:list){bond(b);String k=bondKey(b);require(previous.compareTo(k)<0&&out.putIfAbsent(k,b)==null,"sorted unique bonds");previous=k;}return out;
        }
        Map<String,JsonNode> expectedBonds(Set<String> members,boolean incident) {
            var out=new TreeMap<String,JsonNode>();
            for(var c:state.components())for(var b:c.chemistry().bonds()) {
                var a=source(JSON.valueToTree(c.identity()),b.firstAtomId());var z=source(JSON.valueToTree(c.identity()),b.secondAtomId());String ak=canonical(a),zk=canonical(z);
                if(incident?members.contains(ak)||members.contains(zk):members.contains(ak)&&members.contains(zk)) {
                    var n=JSON.createObjectNode();n.set("first",ak.compareTo(zk)<0?a:z);n.set("second",ak.compareTo(zk)<0?z:a);n.put("order",b.order().name());n.put("aromatic",b.aromatic());out.put(bondKey(n),n);
                }
            }return out;
        }
        void compareBonds(JsonNode list,Set<String> members,boolean incident,String category)throws IOException {
            var actual=bonds(list);var expected=expectedBonds(members,incident);
            if(!actual.keySet().equals(expected.keySet()))unknown(category,"Incomplete/conflicting source bond coverage");
            for(var e:actual.entrySet()){var b=expected.get(e.getKey());if(b==null||!b.get("order").equals(e.getValue().get("order"))||!b.get("aromatic").equals(e.getValue().get("aromatic")))unknown(category,"Source bond order/aromaticity conflict");}
        }
        Map<String,JsonNode> residueFacts(JsonNode f,String... keys)throws IOException {
            var out=new TreeMap<String,JsonNode>();for(var r:array(f,"residues")){fields(r,keys);residue(r.get("residue"));require(out.putIfAbsent(canonical(r.get("residue")),r)==null,"duplicate residue fact");}
            if(!out.keySet().equals(selections.keySet()))unknown(activeCategory,"Incomplete or extraneous residue facts");return out;
        }
        String activeCategory;
        void fact(String category,JsonNode f)throws IOException {
            activeCategory=category;
            switch(category) {
                case "identityState" -> identity(f);
                case "atomCorrespondence" -> correspondence(f);
                case "connections" -> connections(f);
                case "stereochemistry" -> stereo(f);
                case "preparation" -> preparation(f);
                case "coherence" -> coherence(f);
                default -> throw new IllegalArgumentException("Unknown residue fact category");
            }
        }
        void identity(JsonNode f)throws IOException {
            fields(f,"residues");var rs=residueFacts(f,"residue","canonicalIdentity","domainStatus","sourceStateDescription","atoms","bonds");
            for(var entry:rs.entrySet()) {
                var r=entry.getValue();nullableToken(r.get("canonicalIdentity"),IDENTITIES);token(r,"domainStatus",Set.of("SUPPORTED_PRESENT","UNKNOWN_INCONCLUSIVE","UNSUPPORTED"));text(r,"sourceStateDescription");
                var selected=selections.get(entry.getKey());if(selected==null)continue;
                if(r.path("domainStatus").asText().equals("UNKNOWN_INCONCLUSIVE")||r.get("canonicalIdentity").isNull())unknown("identityState","Canonical source identity unresolved");
                if(r.path("domainStatus").asText().equals("UNSUPPORTED"))outside("identityState","Independently reviewed outside-domain chemistry");
                if(!r.get("canonicalIdentity").equals(selected.get("candidateIdentity"))||!r.get("sourceStateDescription").equals(selected.get("stateDescription")))unknown("identityState","Candidate name/state does not agree with independent source review");
                var members=members(selected);var found=new TreeSet<String>();String previous="";
                for(var a:array(r,"atoms")) {
                    fields(a,"source","element","isotopeMass","isotopeStatus","formalCharge","explicitHydrogens","nonExplicitHydrogenCount","aromatic","aromaticityModel","electronicState");
                    var sa=a.get("source");var atom=atom(sa);String key=canonical(sa);require(found.add(key)&&previous.compareTo(key)<0,"sorted unique atom facts");previous=key;atomFacts.put(key,a);
                    text(a,"element");nullableInt(a.get("isotopeMass"),1);nullableInt(a.get("formalCharge"),Integer.MIN_VALUE);nullableInt(a.get("nonExplicitHydrogenCount"),0);nullableBool(a.get("aromatic"));nullableText(a.get("aromaticityModel"));
                    token(a,"isotopeStatus",Set.of("KNOWN_UNSPECIFIED","EXPLICIT","UNKNOWN"));token(a,"electronicState",Set.of("NONE","KNOWN_NON_NONE","UNKNOWN"));
                    String isotope=text(a,"isotopeStatus");require(isotope.equals("UNKNOWN")||isotope.equals("EXPLICIT")!=a.get("isotopeMass").isNull(),"isotope value/state mismatch");
                    var hs=new TreeSet<String>();String hp="";for(var h:a.get("explicitHydrogens")){require(a.get("explicitHydrogens").isArray(),"H array");var ha=atom(h);String hk=canonical(h);require(ha.element().equals("H")&&hp.compareTo(hk)<0&&hs.add(hk),"sorted source H identities");hp=hk;}
                    if(!a.get("explicitHydrogens").isArray())throw new IllegalArgumentException("H array required");
                    if(isotope.equals("UNKNOWN")||a.get("formalCharge").isNull()||a.get("nonExplicitHydrogenCount").isNull()||a.get("aromatic").isNull()||a.get("aromaticityModel").isNull()||a.path("electronicState").asText().equals("UNKNOWN"))unknown("identityState","Required source atom facts unresolved");
                    if(!atom.element().equals(a.path("element").asText())||!JSON.valueToTree(atom.isotope()).equals(a.get("isotopeMass"))||!JSON.valueToTree(atom.formalCharge()).equals(a.get("formalCharge"))||!JSON.valueToTree(atom.aromatic()).equals(a.get("aromatic"))||!neighbors(sa,true).equals(hs))unknown("identityState","Atom facts conflict with immutable chemistry");
                    String radical=atom.properties().get("athena.ocl.atomRadicalState/1");boolean nonNone=Set.of("S","D","T").contains(radical==null?"":radical);
                    if(radical==null||!Set.of("NONE","S","D","T").contains(radical)||a.path("electronicState").asText().equals("NONE")&&nonNone||a.path("electronicState").asText().equals("KNOWN_NON_NONE")&&!nonNone)unknown("identityState","Electronic source-state conflict or missing evidence");
                    if(a.path("electronicState").asText().equals("KNOWN_NON_NONE"))outside("identityState","Known radical outside ordinary residue domain");
                    var cov=coverage.get(canonical(sa.get("component")));var cv=cov==null?null:cov.path("atomState").get(sa.path("atomId").asText());
                    if(cv==null)unknown("identityState","Missing independent source atom coverage");
                    else {
                        if(!cv.path("chargeStatus").asText().equals("SUPPORTED_PRESENT")||!cv.path("formalCharge").equals(a.get("formalCharge"))||!cv.path("aromaticityStatus").asText().equals("SUPPORTED_PRESENT")||!cv.path("aromaticityModel").equals(a.get("aromaticityModel"))||!Set.of("EXPLICIT_GRAPH","AUTHORITATIVE_IMPLICIT").contains(cv.path("hydrogenMode").asText())||!cv.path("implicitHydrogenCount").equals(a.get("nonExplicitHydrogenCount")))unknown("identityState","Source coverage does not independently establish atom facts");
                        var ids=new TreeSet<String>();for(var h:a.get("explicitHydrogens"))ids.add(h.path("atomId").asText());if(!cv.path("explicitHydrogenAtomIds").equals(JSON.valueToTree(ids)))unknown("identityState","Source H coverage conflict");
                        if(cv.path("hydrogenMode").asText().equals("EXPLICIT_GRAPH")&&a.path("nonExplicitHydrogenCount").intValue()!=0)unknown("identityState","Explicit-only coverage contradicts unplaced H count");
                    }
                    if(atom.explicitHydrogens()!=0&&atom.explicitHydrogens()!=a.path("nonExplicitHydrogenCount").intValue())unknown("identityState","Source graph H count disagrees");
                    if(atom.element().equals("H")&&(!hs.isEmpty()||a.path("nonExplicitHydrogenCount").intValue()!=0||neighbors(sa,false).size()!=1))outside("identityState","Nonordinary explicit hydrogen");
                    var cr=coordinates.get(key);if(cr!=null&&(!state.charges().charges().containsKey(cr)||state.charges().charges().get(cr)!=atom.formalCharge()))unknown("identityState","Coordinate/source formal charge conflict");
                }
                if(!found.equals(members))unknown("identityState","Exact residue atom membership incomplete");compareBonds(r.get("bonds"),members,false,"identityState");
            }
        }
        void correspondence(JsonNode f)throws IOException {
            fields(f,"residues");for(var e:residueFacts(f,"residue","atoms","roles").entrySet()){
                fields(e.getValue().get("roles"),ROLES);var selected=selections.get(e.getKey());
                if(selected!=null&&(!selected.get("atoms").equals(e.getValue().get("atoms"))||!selected.get("roles").equals(e.getValue().get("roles"))))unknown("atomCorrespondence","Independent correspondence/role witness mismatch");
                for(var a:array(e.getValue(),"atoms")){fields(a,"source","coordinate");atom(a.get("source"));if(!a.get("coordinate").isNull())coordinate(a.get("coordinate"));}
            }
        }
        void connections(JsonNode f)throws IOException {
            fields(f,"residues","previousPresence","nextPresence","previousResidue","nextResidue","nextProline","cyclicPeptide");
            for(var e:residueFacts(f,"residue","coverage","incidentOrdinaryBonds","nonordinary").entrySet()) {
                var r=e.getValue();token(r,"coverage",SCOPES);var selected=selections.get(e.getKey());if(selected!=null)compareBonds(r.get("incidentOrdinaryBonds"),members(selected),true,"connections");
                var nonordinary=array(r,"nonordinary");
                if(r.path("coverage").asText().equals("UNKNOWN"))unknown("connections","Unknown complete connection scope");
                if(r.path("coverage").asText().equals("KNOWN_NONORDINARY")){if(nonordinary.isEmpty())unknown("connections","Known nonordinary assertion has no original witness");else outside("connections","Known nonordinary source attachment excluded");}
                if(r.path("coverage").asText().equals("COMPLETE_ORDINARY")&&!nonordinary.isEmpty())unknown("connections","Contradictory ordinary/nonordinary scope");
                var seen=new HashSet<String>();for(var n:nonordinary){fields(n,"first","second","kind","sourceLocations");atom(n.get("first"));fields(n.get("second"),"artifact","selector");take(n.path("second").get("artifact"));text(n.get("second"),"selector");text(n,"kind");locations(n.get("sourceLocations"));require(seen.add(canonical(n)),"duplicate nonordinary connection");if(selected!=null&&!members(selected).contains(canonical(n.get("first"))))unknown("connections","Nonordinary connection not incident to selected residue");}
            }
            for(var side:List.of("previous","next")) {
                token(f,side+"Presence",Set.of("PRESENT","ABSENT","UNKNOWN"));String presence=f.path(side+"Presence").asText();var actual=f.get(side+"Residue");var selected=binding.get(side);
                if(!actual.isNull())residue(actual);
                if(presence.equals("UNKNOWN"))unknown("connections","Neighbor presence unresolved");
                else if(presence.equals("ABSENT")){if(!actual.isNull()||!selected.isNull())unknown("connections","Contradictory terminal boundary");else outside("connections","Known terminal outside selected internal residue domain");}
                else if(actual.isNull()||selected.isNull()||!selected.path("residue").equals(actual))unknown("connections","Neighbor identity/link mismatch");
            }
            token(f,"nextProline",Set.of("TRUE","FALSE","NOT_APPLICABLE","UNKNOWN"));token(f,"cyclicPeptide",Set.of("TRUE","FALSE","UNKNOWN"));
            if(f.path("cyclicPeptide").asText().equals("UNKNOWN"))unknown("connections","Cyclic context unresolved");else if(f.path("cyclicPeptide").asText().equals("TRUE"))outside("connections","Cyclic peptide excluded");
            String expected=f.path("nextPresence").asText().equals("ABSENT")?"NOT_APPLICABLE":identity("next")==null?"UNKNOWN":identity("next").equals("PRO")?"TRUE":"FALSE";
            if(!expected.equals(f.path("nextProline").asText())||expected.equals("UNKNOWN"))unknown("connections","Next-PRO fact unresolved or conflicts with independently established next identity");
            String inferred=SCOPES.stream().filter(x->x.equals(binding.path("sourceScope").asText())).findFirst().orElseThrow();
            if(inferred.equals("UNKNOWN"))unknown("connections","Binding scope unknown");
            if(inferred.equals("KNOWN_NONORDINARY")&&f.path("residues").findValuesAsText("coverage").stream().noneMatch("KNOWN_NONORDINARY"::equals))unknown("connections","Binding known connection lacks witness");
            if(inferred.equals("COMPLETE_ORDINARY")&&f.path("residues").findValuesAsText("coverage").stream().anyMatch(x->!x.equals("COMPLETE_ORDINARY")))unknown("connections","Binding scope contradicts source witnesses");
        }
        String identity(String position){var s=binding.path(position);var f=facts.get("identityState");if(s.isNull()||f==null)return null;for(var r:f.path("residues"))if(r.path("residue").equals(s.path("residue")))return r.path("canonicalIdentity").isTextual()?r.path("canonicalIdentity").asText():null;return null;}
        void stereo(JsonNode f)throws IOException {
            fields(f,"residues","interpretationProtocol");take(f.get("interpretationProtocol"));
            for(var e:residueFacts(f,"residue","alpha","beta","sourceStereo","valineMethylAttribution").entrySet()) {
                var r=e.getValue();var selected=selections.get(e.getKey());if(selected==null)continue;
                token(r,"alpha",Set.of("L","D","ACHIRAL_GLY","UNKNOWN"));token(r,"beta",Set.of("NATIVE_ILE","NATIVE_THR","OTHER","NOT_APPLICABLE","UNKNOWN"));
                String id=null;for(var q:facts.getOrDefault("identityState",JSON.createObjectNode()).path("residues"))if(q.path("residue").equals(r.path("residue")))id=q.path("canonicalIdentity").asText(null);
                if(id==null||r.path("alpha").asText().equals("UNKNOWN"))unknown("stereochemistry","Alpha identity/stereo unresolved");
                else if(!r.path("alpha").asText().equals(id.equals("GLY")?"ACHIRAL_GLY":"L"))outside("stereochemistry","Non-native alpha stereochemistry");
                String beta="ILE".equals(id)?"NATIVE_ILE":"THR".equals(id)?"NATIVE_THR":"NOT_APPLICABLE";
                if(r.path("beta").asText().equals("UNKNOWN"))unknown("stereochemistry","Beta state unresolved");else if(!r.path("beta").asText().equals(beta))outside("stereochemistry","Non-native beta stereochemistry");
                var centers=new HashSet<String>();for(var st:array(r,"sourceStereo")) {
                    fields(st,"center","orderedLigands","nonExplicitHydrogenLigand","sourceToken","sourceLocations");var center=st.get("center");var a=atom(center);require(centers.add(canonical(center)),"duplicate stereo center");bool(st.get("nonExplicitHydrogenLigand"));text(st,"sourceToken");locations(st.get("sourceLocations"));
                    var ligands=new HashSet<String>();for(var l:array(st,"orderedLigands")){atom(l);require(ligands.add(canonical(l)),"duplicate stereo ligand");}
                    if(!neighbors(center,false).equals(ligands)||!a.stereochemistry().equals(st.path("sourceToken").asText())||Set.of("NONE","UNSPECIFIED","").contains(a.stereochemistry()))unknown("stereochemistry","Exact source stereo token/ligand order evidence incomplete");
                    var af=atomFacts.get(canonical(center));if(af==null||af.path("nonExplicitHydrogenCount").isNull()||st.path("nonExplicitHydrogenLigand").booleanValue()!=(af.path("nonExplicitHydrogenCount").intValue()==1)||ligands.size()+(st.path("nonExplicitHydrogenLigand").booleanValue()?1:0)!=4)unknown("stereochemistry","Virtual stereo H ligand not independently established");
                }
                if(id!=null&&!id.equals("GLY")&&!centers.contains(canonical(selected.path("roles").path("CA"))))unknown("stereochemistry","Missing alpha stereo witness");
                if(Set.of("ILE","THR").contains(id==null?"":id)&&!centers.contains(canonical(selected.path("roles").path("CB"))))unknown("stereochemistry","Missing native beta stereo witness");
                var v=r.get("valineMethylAttribution");if(!v.isNull()){fields(v,"cg1","cg2","protocol");atom(v.get("cg1"));atom(v.get("cg2"));take(v.get("protocol"));if(!v.get("cg1").equals(selected.path("roles").path("CG1"))||!v.get("cg2").equals(selected.path("roles").path("CG2")))unknown("stereochemistry","Stereospecific methyl map conflict");}
                if("VAL".equals(id)&&v.isNull())unknown("stereochemistry","VAL stereospecific methyl attribution missing");
            }
        }
        void preparation(JsonNode f)throws IOException {
            fields(f,"sourceKind","preparationReferences","originalSources","resultState");token(f,"sourceKind",Set.of("SOURCE","PREPARED_SOURCE","DERIVED"));pins(f.get("preparationReferences"),true);pins(f.get("originalSources"),false);binding(f.get("resultState"));
            if(f.path("originalSources").isEmpty()||!f.get("sourceKind").equals(binding.get("sourceKind"))||!f.get("preparationReferences").equals(binding.get("preparationReferences"))||!f.get("resultState").equals(binding.get("stateBinding")))unknown("preparation","Preparation lineage/source state mismatch");
            if(f.path("sourceKind").asText().equals("DERIVED"))outside("preparation","No derived transformation profile admitted");
            if(!f.path("sourceKind").asText().equals("SOURCE")&&f.path("preparationReferences").isEmpty())unknown("preparation","Preparation lineage missing");
        }
        void coherence(JsonNode f)throws IOException {
            fields(f,"stateBinding","modelIdentity","frameReference","selectedAtoms","sourceLocations","alternateEnsembleReferences","correlation");binding(f.get("stateBinding"));model(f.get("modelIdentity"));take(f.get("frameReference"));locations(f.get("sourceLocations"));pins(f.get("alternateEnsembleReferences"),false);token(f,"correlation",Set.of("EXPLICIT_SINGLE_CONFORMER","EXPLICIT_ENSEMBLE_MEMBER","UNRESOLVED"));
            var atoms=new ArrayList<AtomReference>();for(var a:array(f,"selectedAtoms"))atoms.add(coordinate(a));require(atoms.equals(atoms.stream().distinct().sorted().toList()),"sorted unique coherent atoms");
            if(!f.get("stateBinding").equals(binding.get("stateBinding"))||!f.get("modelIdentity").equals(binding.get("modelIdentity"))||!f.get("frameReference").equals(binding.get("frameReference"))||!atoms.containsAll(coordinates.values())||!state.frameQualified()||f.path("correlation").asText().equals("UNRESOLVED"))unknown("coherence","State/frame/conformer coherence unresolved");
            if(f.path("correlation").asText().equals("EXPLICIT_ENSEMBLE_MEMBER")&&f.path("alternateEnsembleReferences").isEmpty())unknown("coherence","Alternate ensemble relationship missing");
            if(!f.get("alternateEnsembleReferences").equals(binding.path("conformerSelection").get("correlationWitnesses")))unknown("coherence","Binding/source alternate correlation mismatch");
        }
        void model(JsonNode n)throws IOException{fields(n,"artifact","selector","rawValue");take(n.get("artifact"));text(n,"selector");text(n,"rawValue");}
        void rolesAndLinks()throws IOException {
            for(var e:selections.entrySet()) {
                var r=e.getValue();String id=null;for(var f:facts.getOrDefault("identityState",JSON.createObjectNode()).path("residues"))if(f.path("residue").equals(r.path("residue")))id=f.path("canonicalIdentity").asText(null);
                if(id==null)continue;var allowed=new HashSet<>(List.of("N","CA","C","O"));if(!id.equals("GLY"))allowed.add("CB");
                allowed.addAll(switch(id){case "SER"->List.of("OG");case "THR"->List.of("OG1","CG2");case "VAL"->List.of("CG1","CG2");case "ILE"->List.of("CG1","CG2","CD1");case "HIS"->List.of("ND1","NE2");case "PRO"->List.of("CD");default->List.of();});
                for(var role:ROLES){var a=r.path("roles").path(role);if(allowed.contains(role)&&a.isNull())unknown("atomCorrespondence","Required canonical role missing");else if(!allowed.contains(role)&&!a.isNull())outside("identityState","Role outside exact residue identity");else if(!a.isNull()){
                    String element=role.equals("N")||role.startsWith("N")?"N":role.startsWith("O")?"O":"C";if(!atom(a).element().equals(element))outside("identityState","Role element incompatible with source identity");
                }}
                var roles=r.path("roles");edge(roles.path("N"),roles.path("CA"),"SINGLE");edge(roles.path("CA"),roles.path("C"),"SINGLE");edge(roles.path("C"),roles.path("O"),"DOUBLE");if(!id.equals("GLY"))edge(roles.path("CA"),roles.path("CB"),"SINGLE");if(id.equals("PRO"))edge(roles.path("N"),roles.path("CD"),"SINGLE");
            }
            var expected=new ArrayList<String>();for(var side:List.of("previous","next"))if(!binding.path(side).isNull()) {
                var a=side.equals("previous")?binding.path(side).path("roles").path("C"):binding.path("central").path("roles").path("C");
                var b=side.equals("previous")?binding.path("central").path("roles").path("N"):binding.path(side).path("roles").path("N");
                if(a.isObject()&&b.isObject()){expected.add(canonical(a)+"->"+canonical(b));edge(a,b,"SINGLE");}
            }
            var actual=new HashSet<String>();for(var link:array(binding,"peptideLinks")){fields(link,"from","to","sourceLocations");atom(link.get("from"));atom(link.get("to"));locations(link.get("sourceLocations"));require(actual.add(canonical(link.get("from"))+"->"+canonical(link.get("to"))),"duplicate peptide link");}
            if(!actual.equals(new HashSet<>(expected)))unknown("connections","Selected peptide link witnesses missing or inconsistent");
            var centralMembers=members(binding.get("central"));
            var allowedBoundary=new HashSet<String>();
            for(var side:List.of("previous","next"))if(!binding.path(side).isNull()){
                var a=side.equals("previous")?binding.path(side).path("roles").path("C"):binding.path("central").path("roles").path("C");
                var b=side.equals("previous")?binding.path("central").path("roles").path("N"):binding.path(side).path("roles").path("N");
                if(a.isObject()&&b.isObject())allowedBoundary.add(List.of(canonical(a),canonical(b)).stream().sorted().reduce((x,y)->x+"\u0000"+y).orElseThrow());
            }
            for(var bond:expectedBonds(centralMembers,true).entrySet()){
                boolean first=centralMembers.contains(canonical(bond.getValue().get("first"))),second=centralMembers.contains(canonical(bond.getValue().get("second")));
                if(first!=second&&!allowedBoundary.contains(bond.getKey()))outside("connections","Known crosslink outside ordinary selected peptide boundary");
            }
            // Known extra backbone heavy attachments cannot be admitted by a residue label.
            var central=binding.path("central").path("roles");var n=central.path("N");
            if(n.isObject()&&!binding.path("previous").isNull()) {
                var permitted=new HashSet<String>();for(var a:List.of(central.path("CA"),binding.path("previous").path("roles").path("C")))if(a.isObject())permitted.add(canonical(a));
                if("PRO".equals(identity("central"))&&central.path("CD").isObject())permitted.add(canonical(central.path("CD")));
                var heavy=neighbors(n,false);heavy.removeAll(neighbors(n,true));if(!permitted.equals(heavy))outside("identityState","Nonordinary backbone nitrogen attachment");
            }
        }
        void edge(JsonNode a,JsonNode b,String order)throws IOException {
            if(!a.isObject()||!b.isObject()){unknown("connections","Required role/connection unresolved");return;}
            if(!a.get("component").equals(b.get("component"))){unknown("connections","Peptide linkage not represented by immutable source bond");return;}
            var g=component(a).chemistry();var match=g.bonds().stream().filter(x->Set.of(x.firstAtomId(),x.secondAtomId()).equals(Set.of(a.path("atomId").asText(),b.path("atomId").asText()))).toList();
            if(match.size()!=1||!match.getFirst().order().name().equals(order)||match.getFirst().aromatic())outside("identityState","Known source graph outside ordinary peptide/role connectivity");
        }
    }
    static Checked check(SystemStateView state,EvidenceEnvelope envelope,Map<String,EvidenceEnvelope> inputs)throws Exception {
        require(envelope.evidenceType().equals("athena:residue-context-binding"),"Residue binding evidence type");var n=read(envelope);
        fields(n,"schema","admissionDefinition","stateBinding","central","previous","next","peptideLinks","sourceKind","preparationReferences","modelIdentity","frameReference","conformerSelection","sourceCoverage","sourceScope","limitations");
        require(text(n,"schema").equals("athena-residue-context-binding/1")&&text(n,"admissionDefinition").equals(ADMISSION)&&binding(n.get("stateBinding")).equals(state.binding()),"Exact residue context definition/state required");
        var c=new Check(state,inputs,n);c.consumed.put(canonical(pin(envelope)),envelope);
        token(n,"sourceKind",Set.of("SOURCE","PREPARED_SOURCE","DERIVED"));token(n,"sourceScope",SCOPES);strings(n.get("limitations"));c.pins(n.get("preparationReferences"),true);c.model(n.get("modelIdentity"));c.take(n.get("frameReference"));
        fields(n.get("conformerSelection"),"sourceLocations","correlationWitnesses");c.locations(n.path("conformerSelection").get("sourceLocations"));c.pins(n.path("conformerSelection").get("correlationWitnesses"),false);
        c.selection(n.get("central"));for(var side:List.of("previous","next"))if(!n.get(side).isNull())c.selection(n.get(side));c.coverage();
        var factStatus=new HashMap<String,EvidenceInterpretation.Status>();
        for(var e:inputs.values())if(e.evidenceType().equals("athena:event-source")) {
            var record=new EvidenceExchange().decodeRecord(e.readPayload());if(!(record instanceof EvidenceInterpretation i)||!(FACT+"/1").equals(i.measurements().get("proposition")))continue;
            var v=i.measurements();require(v.keySet().equals(Set.of("proposition","stateBinding","contextBinding","category","factValues","sourceProtocol","sourceLocations","preparationReferences")),"Exact residue witness fields required");
            var contextPin=JSON.readTree(v.get("contextBinding"));var context=c.take(contextPin);
            if(!contextPin.equals(JSON.valueToTree(pin(envelope)))){
                var other=read(context);if(other.path("stateBinding").equals(n.get("stateBinding"))&&other.path("central").path("residue").equals(n.path("central").path("residue")))for(var category:CATEGORIES)c.unknown(category,"Competing selected-state context witness");continue;
            }
            require(canonical(state.binding()).equals(v.get("stateBinding"))&&e.method().equals(i.evaluator())&&!i.subjects().isEmpty()&&i.subjects().stream().allMatch(s->s.state().equals(state.identity())),"Witness exact state/method/subject mismatch");
            String category=v.get("category");require(CATEGORIES.contains(category),"Unknown source category");
            var deps=new HashSet<String>();for(var input:i.inputs()){var p=JSON.valueToTree(input);c.take(p);require(deps.add(canonical(p)),"duplicate witness dependency");}
            require(deps.contains(canonical(contextPin)),"Witness must bind exact context input");
            var protocol=JSON.readTree(v.get("sourceProtocol"));c.take(protocol);require(deps.contains(canonical(protocol)),"Source protocol must be witness input");
            var prep=JSON.readTree(v.get("preparationReferences"));require(prep.equals(n.get("preparationReferences")),"Witness preparation mismatch");
            var locations=JSON.readTree(v.get("sourceLocations"));c.locations(locations);require(!locations.isEmpty(),"Independent original source witness locations required");
            var f=JSON.readTree(v.get("factValues"));var previous=c.facts.putIfAbsent(category,f);
            if(previous!=null&&(!previous.equals(f)||factStatus.get(category)!=i.status()))c.unknown(category,"Conflicting independent "+category+" facts");
            factStatus.put(category,i.status());if(i.status()!=SUPPORTED_PRESENT)c.unknown(category,"Unresolved independent "+category+" witness");
            // Every recursively consumed original/protocol/preparation pin belongs to the witness input list.
            requirePins(f,deps,inputs);requirePins(locations,deps,inputs);requirePins(prep,deps,inputs);
            c.witnesses.add(e);c.consumed.put(canonical(pin(e)),e);
        }
        for(var category:CATEGORIES){var f=c.facts.get(category);if(f==null)c.unknown(category,"Missing independent "+category+" facts");else c.fact(category,f);}
        c.rolesAndLinks();return new Checked(n,Map.copyOf(c.statuses),Map.copyOf(c.facts),List.copyOf(c.witnesses),List.copyOf(c.consumed.values()),List.copyOf(c.reasons),Map.copyOf(c.coordinates));
    }
    private static void requirePins(JsonNode n,Set<String> deps,Map<String,EvidenceEnvelope> inputs)throws IOException {
        if(n.isObject()&&n.has("reference")&&n.has("sha256")){pinned(n,inputs);require(deps.contains(canonical(n)),"Original/protocol/preparation pin omitted from witness inputs");return;}
        if(n.isContainerNode())for(var v:n)requirePins(v,deps,inputs);
    }
    static boolean authorized(Checked checked,SystemStateView state,Map<String,EvidenceEnvelope> inputs,Instant at)throws Exception {
        var reviewed=new ArrayList<Set<String>>();
        for(var e:inputs.values())if(e.evidenceType().equals("athena:rule-manifest")) {
            var m=ResearchDocuments.decode(e.readPayload(),RuleManifest.class);if(!m.ruleId().equals(FACT)||!m.schema().equals("athena-rule/3"))continue;
            var time=S1Qualification.current(m,state,null,inputs);if(time.isPresent()&&time.get().equals(at))reviewed.add(new HashSet<>(m.scientificSources().stream().map(RuleManifest.Source::sha256).toList()));
        }
        var categories=new HashSet<String>();
        for(var e:checked.witnesses()) {
            var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(e.readPayload());categories.add(i.measurements().get("category"));
            var needed=new HashSet<String>();needed.add(e.payloadSha256());for(var p:i.inputs())needed.add(p.sha256());for(var dependency:checked.consumed())if(!dependency.evidenceType().equals("athena:event-source"))needed.add(dependency.payloadSha256());
            if(!i.recordedAt().equals(at)||reviewed.stream().noneMatch(p->p.containsAll(needed)))return false;
        }return categories.containsAll(CATEGORIES);
    }
    static JsonNode source(JsonNode component,String id){var n=JSON.createObjectNode();n.set("component",component);n.put("atomId",id);return n;}
    private static Set<String> members(JsonNode selection){var out=new TreeSet<String>();for(var a:selection.path("atoms"))out.add(canonical(a.path("source")));return out;}
    static AtomReference coordinate(JsonNode n)throws IOException {fields(n,"chainId","residueNumber","insertionCode","atomName");text(n,"chainId");text(n,"atomName");require(n.path("residueNumber").isIntegralNumber()&&n.path("residueNumber").canConvertToInt()&&n.path("insertionCode").isTextual()&&n.path("insertionCode").asText().length()==1,"Exact atom locator types");return JSON.treeToValue(n,AtomReference.class);}
    static ResidueId residue(JsonNode n)throws IOException{fields(n,"chainId","residueNumber","insertionCode");text(n,"chainId");require(n.path("residueNumber").isIntegralNumber()&&n.path("residueNumber").canConvertToInt()&&(n.path("insertionCode").isNull()||n.path("insertionCode").isTextual()&&n.path("insertionCode").asText().length()==1),"Exact residue locator types");return JSON.treeToValue(n,ResidueId.class);}
    static boolean sameResidue(AtomReference a,JsonNode r)throws IOException{var id=residue(r);return id.chainId().equals(a.chainId())&&id.residueNumber()==a.residueNumber()&&(id.insertionCode()==null?' ':id.insertionCode())==a.insertionCode();}
    private static void token(JsonNode n,String key,Set<String> allowed){require(allowed.contains(text(n,key)),"Unknown "+key);}
    private static void nullableToken(JsonNode n,Set<String> allowed){require(n!=null&&(n.isNull()||n.isTextual()&&allowed.contains(n.asText())),"Invalid nullable token");}
    private static void nullableText(JsonNode n){require(n!=null&&(n.isNull()||n.isTextual()&&!n.asText().isBlank()),"Invalid nullable text");}
    private static void nullableInt(JsonNode n,int min){require(n!=null&&(n.isNull()||n.isIntegralNumber()&&n.canConvertToInt()&&n.intValue()>=min),"Invalid integer source fact");}
    private static void bool(JsonNode n){require(n!=null&&n.isBoolean(),"Boolean required");}
    private static void nullableBool(JsonNode n){require(n!=null&&(n.isNull()||n.isBoolean()),"Nullable Boolean required");}
    private static void strings(JsonNode n){require(n.isArray(),"String array");for(var s:n)require(s.isTextual()&&!s.asText().isBlank(),"Nonblank limitation");}
}
