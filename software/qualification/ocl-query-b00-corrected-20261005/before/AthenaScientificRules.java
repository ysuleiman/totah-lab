package totah.lab.athena.system.rules;

import totah.lab.athena.design.backend.*;
import totah.lab.athena.interaction.*;
import totah.lab.athena.interaction.perception.*;
import totah.lab.athena.system.*;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.*;
import java.util.*;

/** Bounded chemical-domain qualification around existing perception and measurement engines. */
public final class AthenaScientificRules {
    private AthenaScientificRules() { }
    public static final String COVERAGE_VERSION="athena-negative-coverage/1";
    public static final List<String> REQUIREMENTS=List.of("EXPLICIT_PARTNER_SCOPE","EXPLICIT_CONNECTIVITY",
            "UNAMBIGUOUS_CHEMICAL_MAPPING","SUPPORTED_CHEMICAL_DOMAIN","FORMAL_STATE_COVERAGE",
            "PERCEPTION_ENUMERATION_COMPLETE","SEARCH_ENUMERATION_COMPLETE","FINITE_GEOMETRY");
    public static String family(String id) {return switch(id) {
        case "ATHENA.HYDROPHOBIC.CONTACT"->"HYDROPHOBIC";
        case "ATHENA.HBOND.DIRECTIONAL"->"HBOND";
        case "ATHENA.PI_STACKING.GEOMETRY"->"PI_STACKING";
        case "ATHENA.PI_CATION.GEOMETRY"->"PI_CATION";
        case "ATHENA.SALT_BRIDGE.PROXIMITY"->"SALT_BRIDGE";
        default->throw new IllegalArgumentException("unqualified Athena scientific family: "+id);
    };}
    static void validate(RuleManifest m) {
        family(m.ruleId());
        if(!m.schema().equals("athena-rule/2")||!m.profile().equals("ATHENA_SCIENTIFIC_V1")
                ||!m.implementationVersion().equals("1")||m.family()!=RuleManifest.Family.INTERACTION)
            throw new IllegalArgumentException("scientific manifest binding");
        var coverage=m.negativeCoverage();
        if(!coverage.version().equals(COVERAGE_VERSION)||!coverage.requirements().equals(REQUIREMENTS)
                ||!coverage.scope().equals("EXPLICIT_PARTNERS_ONLY"))throw new IllegalArgumentException("unimplemented negative coverage policy");
        if(!m.requiredCapabilities().equals(List.of(SystemGraphCertificate.Capability.DISTANCE_QUERIES)))
            throw new IllegalArgumentException("candidate-local chemistry qualification is required");
        RuleRegistry.thresholds(m);
        if(!m.parameters().keySet().stream().filter(k->k.startsWith("pattern.")).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("pattern.alcohol","pattern.carbonyl","pattern.ammonium","pattern.carboxylate")))
            throw new IllegalArgumentException("exact registered perception-pattern roles required");
        for(String role:List.of("alcohol","carbonyl","ammonium","carboxylate")) {
            var p=m.parameters().get("pattern."+role);
            if(p==null||!p.unit().equals("policy"))throw new IllegalArgumentException("missing attributed chemical pattern");
        }
    }
    public record Measurements(InteractionMeasurements.Result raw, List<Boolean> candidateSupported,
                               Map<String,Boolean> negativeCoverage, List<String> limitations,
                               Map<String,String> perceptionProvenance, String coverageVersion) {
        public Measurements {
            candidateSupported=List.copyOf(candidateSupported);
            negativeCoverage=Collections.unmodifiableMap(new TreeMap<>(negativeCoverage));
            limitations=List.copyOf(limitations);perceptionProvenance=Collections.unmodifiableMap(new TreeMap<>(perceptionProvenance));
            if(raw.candidates().size()!=candidateSupported.size())throw new IllegalArgumentException("candidate eligibility cardinality");
        }
    }
    private record Chemistry(Map<AtomReference,MolecularGraph.Atom> atoms, Map<String,Set<AtomReference>> roles,
                             List<Set<AtomReference>> carboxylates, Map<String,String> provenance) { }

    /** No new chemistry representation: references into validated source components and OCL pattern matches only. */
    private static Chemistry chemistry(SystemStateView s,RuleManifest m,SubstructureMatcher matcher)throws MolecularBackendException {
        var atoms=new TreeMap<AtomReference,MolecularGraph.Atom>();var roles=new TreeMap<String,Set<AtomReference>>();
        var carboxylates=new ArrayList<Set<AtomReference>>();var provenance=new TreeMap<String,String>();
        for(String role:List.of("alcohol","carbonyl","ammonium","carboxylate"))roles.put(role,new TreeSet<>());
        if(!(matcher instanceof MolecularSanitizer sanitizer))return new Chemistry(atoms,roles,carboxylates,Map.of("missing","qualified matcher/sanitizer not supplied"));
        var ambiguous=new HashSet<AtomReference>();
        for(var component:s.components()) {
            if(component.correspondenceAlternatives().size()!=1)continue;
            var map=component.correspondenceAlternatives().getFirst();var graph=component.chemistry();
            graph.validateTopology(true);
            MolecularSanitizer.Result checked;
            try {checked=sanitizer.sanitize(graph,new MolecularSanitizer.SanitizationPolicy(Set.of(),true));}
            catch(MolecularBackendException unavailable) {
                provenance.put(component.identity()+"/sanitizer-unavailable",unavailable.toString());
                continue; // Component evidence survives; neither local positives nor negatives may rely on it.
            }
            provenance.put(component.identity()+"/sanitizer",new String(SystemStateView.bytes(checked.evidence()),java.nio.charset.StandardCharsets.UTF_8));
            if(!checked.valid()||!checked.graph().equals(graph)||map.size()!=graph.atoms().size()
                    ||new HashSet<>(map.values()).size()!=map.size())continue;
            boolean valid=true;
            for(var a:graph.atoms()) {
                var ref=map.get(a.id());var source=s.atoms().get(ref);
                if(source==null||source.getElement()==null||!source.getElement().name().equalsIgnoreCase(a.element())
                        ||!s.charges().charges().containsKey(ref)||s.charges().charge(ref)!=a.formalCharge())valid=false;
                if(a.coordinates()!=null&&source!=null) {
                    var p=a.coordinates();var q=source.getPosition();
                    if(p.x()!=q.x()||p.y()!=q.y()||p.z()!=q.z())valid=false;
                }
            }
            var mappedBonds=new HashSet<Bond>();
            for(var b:graph.bonds())mappedBonds.add(new Bond(map.get(b.firstAtomId()),map.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name())));
            var sourceBonds=new HashSet<Bond>();
            for(var b:s.graph().structure().bonds())if(map.containsValue(b.atom1())||map.containsValue(b.atom2()))sourceBonds.add(b);
            if(!sourceBonds.equals(mappedBonds))valid=false; // Never qualify a cut chemical fragment as complete context.
            if(!valid)continue;
            for(var a:graph.atoms())if(atoms.put(map.get(a.id()),a)!=null)ambiguous.add(map.get(a.id()));
            for(var role:roles.keySet()) {
                var match=matcher.match(m.parameters().get("pattern."+role).value(),graph);
                provenance.put(component.identity()+"/"+role,new String(SystemStateView.bytes(match.evidence()),java.nio.charset.StandardCharsets.UTF_8));
                for(var occurrence:match.queryToTargetAtomIds()) {
                    String first=occurrence.get("query:0");
                    if(first==null||!map.containsKey(first))throw new IllegalArgumentException("matcher must preserve query atom IDs");
                    roles.get(role).add(map.get(first));
                    if(role.equals("carboxylate"))carboxylates.add(occurrence.values().stream().map(map::get).collect(java.util.stream.Collectors.toUnmodifiableSet()));
                }
            }
        }
        ambiguous.forEach(atoms::remove);
        return new Chemistry(atoms,roles,carboxylates,provenance);
    }
    public static Measurements collect(SystemStateView s,RuleManifest m,RuleRequest request,SubstructureMatcher matcher)throws MolecularBackendException {
        validate(m);String family=family(m.ruleId());
        if(request.first().isEmpty()||request.second().isEmpty())throw new IllegalArgumentException("explicit nonempty partners required");
        var first=s.graph().view(request.first()).toStructure();var second=s.graph().view(request.second()).toStructure();
        var selected=new TreeSet<AtomReference>();
        var residues=new HashSet<ResidueId>();residues.addAll(request.first());residues.addAll(request.second());
        s.atoms().keySet().stream().filter(a->residues.contains(SystemStateView.residue(a))).forEach(selected::add);
        var chemistry=chemistry(s,m,matcher);
        var raw=InteractionMeasurements.collect(family,first,second,s.charges(),RuleRegistry.thresholds(m),request.maximumCandidates());
        var coverage=new TreeMap<String,Boolean>();REQUIREMENTS.forEach(k->coverage.put(k,true));
        coverage.put("EXPLICIT_PARTNER_SCOPE",residues.size()==request.first().size()+request.second().size());
        boolean topology=s.graph().structure().getConnectivityMetadata().provenance()==ConnectivityProvenance.EXPLICIT;
        coverage.put("EXPLICIT_CONNECTIVITY",topology);
        coverage.put("UNAMBIGUOUS_CHEMICAL_MAPPING",chemistry.atoms().keySet().containsAll(selected));
        coverage.put("FORMAL_STATE_COVERAGE",s.charges().charges().keySet().containsAll(selected)
                &&(family.equals("HYDROPHOBIC")||family.equals("PI_STACKING")||s.protonationQualified()));
        coverage.put("SEARCH_ENUMERATION_COMPLETE",raw.limitations().stream().noneMatch(x->x.contains("budget exhausted")));
        coverage.put("FINITE_GEOMETRY",raw.candidates().stream().allMatch(c->
                c.values().values().stream().allMatch(Double::isFinite)
                        &&c.values().get("distance")>RuleRegistry.thresholds(m).minDist()));
        var refs=new IdentityHashMap<Atom,AtomReference>();s.atoms().forEach((r,a)->refs.put(a,r));
        var rings=new ArrayList<Set<AtomReference>>();var ringPerception=new AromaticRingPerception();
        for(var partner:List.of(first,second))for(var ring:ringPerception.perceive(partner)) {
            var ids=ring.atoms().stream().map(refs::get).collect(java.util.stream.Collectors.toSet());
            if(!ring.degraded()&&ids.size()==6&&ids.stream().allMatch(a->chemistry.atoms().containsKey(a)
                    &&chemistry.atoms().get(a).aromatic()&&chemistry.atoms().get(a).element().equals("C")
                    &&chemistry.atoms().get(a).formalCharge()==0))rings.add(Set.copyOf(ids));
        }
        // Bounded domain: isolated six-carbon aromatic rings; fused/other aromatic sites remain unknown.
        var ringCount=new HashMap<AtomReference,Integer>();for(var ring:rings)for(var a:ring)ringCount.merge(a,1,Integer::sum);
        rings.removeIf(ring->ring.stream().anyMatch(a->ringCount.get(a)!=1));
        var chargeGroups=new ArrayList<Set<AtomReference>>();var cp=new ChargedGroupPerception();
        for(var partner:List.of(first,second))for(var group:cp.perceive(partner,s.charges())) {
            var ids=group.atoms().stream().map(refs::get).collect(java.util.stream.Collectors.toSet());
            boolean supported=ids.stream().allMatch(chemistry.atoms()::containsKey)&&!group.degraded();
            supported &= group.type()==ChargedGroupType.AMINE&&ids.stream().anyMatch(chemistry.roles().get("ammonium")::contains)
                    ||group.type()==ChargedGroupType.CARBOXYLATE&&chemistry.carboxylates().stream().anyMatch(c->c.containsAll(ids)&&ids.size()==2);
            int charge=ids.stream().mapToInt(s.charges()::charge).sum();
            supported &= charge!=0&&(charge>0)==(group.sign()==ChargeSign.POSITIVE);
            if(supported)chargeGroups.add(Set.copyOf(ids));
        }
        var eligible=new ArrayList<Boolean>();
        for(var c:raw.candidates()) {
            var both=new ArrayList<>(c.first());both.addAll(c.second());
            boolean supported=topology&&both.stream().allMatch(chemistry.atoms()::containsKey);
            supported &= switch(family) {
                case "HYDROPHOBIC"->both.stream().allMatch(a->chemistry.atoms().containsKey(a)&&chemistry.atoms().get(a).formalCharge()==0&&nonpolarCarbon(s,a));
                case "HBOND"->s.protonationQualified()&&hbondSupported(s,c,chemistry);
                case "PI_STACKING"->rings.contains(Set.copyOf(c.first()))&&rings.contains(Set.copyOf(c.second()));
                case "SALT_BRIDGE"->s.protonationQualified()&&chargeGroups.contains(Set.copyOf(c.first()))&&chargeGroups.contains(Set.copyOf(c.second()));
                case "PI_CATION"->s.protonationQualified()&&((rings.contains(Set.copyOf(c.first()))&&chargeGroups.contains(Set.copyOf(c.second())))
                        ||(rings.contains(Set.copyOf(c.second()))&&chargeGroups.contains(Set.copyOf(c.first()))));
                default->false;
            };
            eligible.add(supported);
        }
        boolean domain=true,perception=true;
        for(var ref:selected) {
            var atom=chemistry.atoms().get(ref);
            if(atom==null){domain=false;continue;}
            if(family.equals("HBOND")) {
                if(!Set.of("C","H","O").contains(atom.element())||atom.formalCharge()!=0)domain=false;
                if(atom.element().equals("O")&&!chemistry.roles().get("alcohol").contains(ref)&&!chemistry.roles().get("carbonyl").contains(ref))domain=false;
                if(atom.element().equals("O")&&!"OA".equals(s.atoms().get(ref).getAutoDockType()))perception=false;
                if(chemistry.roles().get("alcohol").contains(ref)) {
                    var hydrogens=s.graph().structure().bonds().stream().filter(b->b.atom1().equals(ref)||b.atom2().equals(ref))
                            .map(b->b.atom1().equals(ref)?b.atom2():b.atom1()).filter(a->s.atoms().get(a).getElement()==Element.H).toList();
                    if(hydrogens.size()!=1||!"HD".equals(s.atoms().get(hydrogens.getFirst()).getAutoDockType()))perception=false;
                }
            }
            if((family.equals("PI_STACKING")||family.equals("PI_CATION"))&&atom.aromatic()
                    &&rings.stream().noneMatch(r->r.contains(ref)))domain=false;
            if((family.equals("SALT_BRIDGE")||family.equals("PI_CATION"))&&atom.formalCharge()!=0
                    &&chargeGroups.stream().noneMatch(g->g.contains(ref)))domain=false;
            if(family.equals("HYDROPHOBIC")&&atom.element().equals("C")&&atom.formalCharge()!=0)domain=false;
        }
        // Completeness includes the native candidate enumerator's prerequisites, except the deliberately retired
        // universal amine orientation guard (still retained as raw geometry and in legacy evaluation).
        perception &= raw.limitations().stream().allMatch(x->x.contains("budget exhausted")
                ||family.equals("PI_CATION")&&x.equals("amine guard unavailable"));
        // A nearest-heavy donor assignment differing from a source covalent donor must not support a negative.
        if(family.equals("HBOND"))perception &= explicitDonorsEnumerated(s,request,raw,chemistry);
        coverage.put("SUPPORTED_CHEMICAL_DOMAIN",domain);
        coverage.put("PERCEPTION_ENUMERATION_COMPLETE",perception);
        var limitations=new ArrayList<>(raw.limitations());
        coverage.forEach((k,v)->{if(!v)limitations.add("negative coverage missing: "+k);});
        return new Measurements(raw,eligible,coverage,limitations,chemistry.provenance(),COVERAGE_VERSION);
    }
    private static boolean nonpolarCarbon(SystemStateView s,AtomReference carbon) {
        if(s.atoms().get(carbon).getElement()!=Element.C)return false;
        return s.graph().structure().bonds().stream().filter(b->b.atom1().equals(carbon)||b.atom2().equals(carbon))
                .map(b->b.atom1().equals(carbon)?b.atom2():b.atom1()).allMatch(a->Set.of(Element.C,Element.H).contains(s.atoms().get(a).getElement()));
    }
    private static boolean hbondSupported(SystemStateView s,InteractionMeasurements.Candidate c,Chemistry chemistry) {
        var donor=c.first().size()==2?c.first():c.second();var acceptor=c.first().size()==1?c.first():c.second();
        if(donor.size()!=2||acceptor.size()!=1)return false;
        var d=donor.get(0);var h=donor.get(1);var a=acceptor.getFirst();
        return chemistry.roles().get("alcohol").contains(d)
                &&(chemistry.roles().get("alcohol").contains(a)||chemistry.roles().get("carbonyl").contains(a))
                &&s.graph().structure().bonds().contains(new Bond(d,h,totah.lab.gaia.chemistry.BondOrder.SINGLE));
    }
    private static boolean explicitDonorsEnumerated(SystemStateView s,RuleRequest request,InteractionMeasurements.Result raw,Chemistry chemistry) {
        for(var donor:chemistry.roles().get("alcohol")) {
            boolean first=request.first().contains(SystemStateView.residue(donor));
            boolean second=request.second().contains(SystemStateView.residue(donor));if(!first&&!second)continue;
            for(var acceptor:java.util.stream.Stream.concat(chemistry.roles().get("alcohol").stream(),chemistry.roles().get("carbonyl").stream()).distinct().toList()) {
                if(!(first?request.second():request.first()).contains(SystemStateView.residue(acceptor)))continue;
                if(raw.candidates().stream().noneMatch(c->hbondSupported(s,c,chemistry)
                        &&(c.first().contains(donor)&&c.second().contains(acceptor)||c.second().contains(donor)&&c.first().contains(acceptor))))return false;
            }
        }return true;
    }
    public static boolean qualifies(InteractionMeasurements.Candidate candidate,RuleManifest m) {
        var values=new TreeMap<>(candidate.values());
        if(family(m.ruleId()).equals("PI_CATION"))values.remove("angle"); // No universal amine guard in this scientific definition.
        return InteractionMeasurements.classify(family(m.ruleId()),values,RuleRegistry.thresholds(m))!=null;
    }
}
