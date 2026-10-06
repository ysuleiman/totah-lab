package totah.lab.athena.system;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.gaia.graph.*;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.util.*;

/** Read-only composition, not a second molecular representation. Source objects and atom order survive. */
public final class SystemStateView {
    private static final ObjectMapper JSON = com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .addModule(new Jdk8Module()).addModule(new JavaTimeModule()).addModule(orderedSets())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(MapperFeature.AUTO_DETECT_IS_GETTERS).build();
    // Same canonical set ordering used by the existing fragment request journal; list/atom order is preserved.
    @SuppressWarnings("rawtypes")
    private static com.fasterxml.jackson.databind.Module orderedSets() {
        var module=new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(Set.class,new JsonSerializer<Set>() {
            @Override public void serialize(Set value,com.fasterxml.jackson.core.JsonGenerator generator,SerializerProvider provider)throws java.io.IOException {
                var nodes=new ArrayList<JsonNode>();
                for(Object item:value)nodes.add(((ObjectMapper)generator.getCodec()).valueToTree(item));
                nodes.sort(Comparator.comparing(JsonNode::toString));
                generator.writeStartArray();for(var node:nodes)generator.writeTree(node);generator.writeEndArray();
            }
        });return module;
    }
    public record Component(ScientificReference identity, MolecularGraph chemistry,
                            List<Map<String,AtomReference>> correspondenceAlternatives, List<String> limitations) {
        public Component {
            Objects.requireNonNull(identity); Objects.requireNonNull(chemistry);
            correspondenceAlternatives = correspondenceAlternatives.stream().map(m -> Collections.unmodifiableMap(new TreeMap<>(m))).toList();
            limitations = List.copyOf(limitations);
        }
    }
    public record Binding(ScientificReference state, String stateSha256, String chemicalSha256,
                          String coordinateSha256, String correspondenceSha256) { }
    private final ScientificReference identity;
    private final ResidueGraph graph;
    private final List<Component> components;
    private final List<ScientificReference> sources;
    private final Set<ResidueId> cofactors;
    private final FormalChargeAssignments charges;
    private final boolean frameQualified, protonationQualified;
    private final List<String> limitations;
    private final Map<AtomReference,Atom> atoms;
    private final Binding binding;

    public SystemStateView(ScientificReference identity, ResidueGraph graph, List<Component> components,
                           List<ScientificReference> sources, Set<ResidueId> cofactors,
                           FormalChargeAssignments charges, boolean frameQualified, boolean protonationQualified,
                           List<String> limitations) {
        this.identity=Objects.requireNonNull(identity); this.graph=Objects.requireNonNull(graph);
        this.components=List.copyOf(components); this.sources=List.copyOf(sources); this.cofactors=Set.copyOf(cofactors);
        this.charges=Objects.requireNonNull(charges); this.frameQualified=frameQualified; this.protonationQualified=protonationQualified;
        this.limitations=List.copyOf(limitations);
        if (sources.isEmpty()) throw new IllegalArgumentException("source references required");
        var index=new LinkedHashMap<AtomReference,Atom>();
        for (var c:graph.structure().getChains()) for (var r:c.residues()) for (var a:r.getAtoms())
            index.put(new AtomReference(c.id(),r.getNumber(),r.getInsertionCode()==null?' ':r.getInsertionCode(),a.getName()),a);
        atoms=Collections.unmodifiableMap(index);
        var coordinates=atoms.entrySet().stream().map(e -> Map.of("atom",e.getKey(),"position",e.getValue().getPosition())).toList();
        var chemistry=components.stream().map(c -> Map.of("identity",c.identity(),"graph",new MolecularGraph(c.chemistry().atoms().stream()
                .map(a -> new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),null,a.properties())).toList(),c.chemistry().bonds(),c.chemistry().properties()))).toList();
        binding=new Binding(identity,digest(snapshot()),digest(chemistry),digest(coordinates),digest(components.stream().map(Component::correspondenceAlternatives).toList()));
    }
    public ScientificReference identity() { return identity; }
    public ResidueGraph graph() { return graph; }
    public List<Component> components() { return components; }
    public List<ScientificReference> sources() { return sources; }
    public Set<ResidueId> cofactors() { return cofactors; }
    public FormalChargeAssignments charges() { return charges; }
    public boolean frameQualified() { return frameQualified; }
    public boolean protonationQualified() { return protonationQualified; }
    public List<String> limitations() { return limitations; }
    public Map<AtomReference,Atom> atoms() { return atoms; }
    public Binding binding() { return binding; }
    public EvidenceSubject subject() { return new EvidenceSubject(identity,"system","complete",List.of()); }
    public static ResidueId residue(AtomReference a) { return new ResidueId(a.chainId(),a.residueNumber(),a.insertionCode()); }

    /** Exact frame snapshot for evidence persistence, including source atom metadata and connectivity. */
    public Map<String,Object> snapshot() {
        var rows=new ArrayList<Map<String,Object>>();
        for (var c:graph.structure().getChains()) for (var r:c.residues()) {
            var row=new TreeMap<String,Object>(); row.put("chain",c.id());row.put("name",r.getName()); row.put("number",r.getNumber());
            row.put("insertion",r.getInsertionCode());row.put("classification",r.getClassificationEvidence());row.put("atoms",r.getAtoms());rows.add(row);
        }
        var out=new TreeMap<String,Object>();out.put("identity",identity);out.put("residues",rows);out.put("bonds",graph.structure().bonds());
        out.put("connectivity",graph.structure().getConnectivityMetadata());out.put("sequenceEdges",graph.sequenceEdges());out.put("components",components);
        out.put("sources",sources);out.put("cofactors",cofactors.stream().sorted(Comparator.comparing(ResidueId::toString)).toList());
        out.put("formalCharges",charges.charges().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> Map.of("atom",e.getKey(),"charge",e.getValue())).toList());
        out.put("frameQualified",frameQualified);out.put("protonationQualified",protonationQualified);out.put("limitations",limitations);return Collections.unmodifiableMap(out);
    }
    public static byte[] bytes(Object value) {
        try { return JSON.writeValueAsBytes(value); } catch (java.io.IOException e) { throw new IllegalArgumentException("cannot encode state/evidence",e); }
    }
    public static String digest(Object value) { return EvidenceExchange.sha256(bytes(value)); }

    public record Traversal(List<AtomReference> visited, List<AtomPairDistance> spatial,
                            List<Bond> covalent, List<Interaction> interactions, boolean complete,
                            List<String> limitations, AtomDistanceCriterion criterion, int maximumHops, int maximumNodes) {
        public Traversal { visited=List.copyOf(visited); spatial=List.copyOf(spatial); covalent=List.copyOf(covalent);
            interactions=List.copyOf(interactions);limitations=List.copyOf(limitations); }
    }
    public Traversal neighborhood(ResidueId seed, AtomDistanceCriterion criterion, int hops, int budget) {
        graph.node(seed);
        return neighborhood(atoms.keySet().stream().filter(a -> residue(a).equals(seed)).toList(),criterion,hops,budget,true,List.of());
    }
    /** Existing relation records stay distinct. Traversal asserts no interaction or physical coupling. */
    public Traversal neighborhood(Collection<AtomReference> seeds, AtomDistanceCriterion criterion, int hops, int budget,
                                  boolean includeCovalent, List<Interaction> interactionRelations) {
        Objects.requireNonNull(criterion);
        if (hops<0 || budget<1 || seeds.isEmpty() || !atoms.keySet().containsAll(seeds)) throw new IllegalArgumentException("invalid traversal request");
        var spatial=new ArrayList<AtomPairDistance>();
        graph.atomProximities(criterion).forEach(p -> spatial.addAll(p.atomPairs()));
        // ResidueGraph deliberately excludes intra-residue spatial pairs. Complete atom neighborhoods include them here.
        for (var node:graph.nodes()) {
            var refs=atoms.keySet().stream().filter(a -> residue(a).equals(node.id()) && criterion.atomSelection().includes(atoms.get(a))).toList();
            for(int i=0;i<refs.size();i++) for(int j=i+1;j<refs.size();j++) {
                var a=refs.get(i);var b=refs.get(j);double distance=atoms.get(a).getPosition().distance(atoms.get(b).getPosition());
                if(distance<=criterion.cutoffAngstroms())spatial.add(new AtomPairDistance(a,b,distance));
            }
        }
        spatial.sort(Comparator.comparing(AtomPairDistance::first).thenComparing(AtomPairDistance::second));
        var adjacency=new TreeMap<AtomReference,Set<AtomReference>>();atoms.keySet().forEach(a -> adjacency.put(a,new TreeSet<>()));
        spatial.forEach(p -> link(adjacency,p.first(),p.second()));
        if(includeCovalent)graph.structure().bonds().forEach(b -> link(adjacency,b.atom1(),b.atom2()));
        var atomRefs=new IdentityHashMap<Atom,AtomReference>();atoms.forEach((r,a)->atomRefs.put(a,r));
        for(var interaction:interactionRelations) for(var a:interaction.proteinAtoms())for(var b:interaction.ligandAtoms()) {
            if(!atomRefs.containsKey(a)||!atomRefs.containsKey(b))throw new IllegalArgumentException("interaction not bound to source atoms");
            link(adjacency,atomRefs.get(a),atomRefs.get(b));
        }
        var visited=new TreeSet<AtomReference>();var frontier=new TreeSet<AtomReference>();
        boolean limited=false;
        for(var seed:new TreeSet<>(seeds)) { if(visited.size()==budget){limited=true;break;} visited.add(seed);frontier.add(seed); }
        for(int depth=0;!frontier.isEmpty();depth++) {
            var next=new TreeSet<AtomReference>();
            for(var a:frontier)for(var b:adjacency.get(a)) if(!visited.contains(b)) {
                if(depth>=hops || visited.size()>=budget)limited=true;
                else {visited.add(b);next.add(b);}
            }
            frontier=next;
        }
        var selectedInteractions=interactionRelations.stream().filter(i -> i.proteinAtoms().stream().anyMatch(a -> visited.contains(atomRefs.get(a)))
                && i.ligandAtoms().stream().anyMatch(a -> visited.contains(atomRefs.get(a)))).toList();
        return new Traversal(List.copyOf(visited),spatial.stream().filter(p->visited.contains(p.first())&&visited.contains(p.second())).toList(),
                includeCovalent?graph.structure().bonds().stream().filter(b->visited.contains(b.atom1())&&visited.contains(b.atom2())).toList():List.of(),
                selectedInteractions,!limited,limited?List.of("hop/node bound reached; incomplete coverage, not absence"):List.of(),criterion,hops,budget);
    }
    private static void link(Map<AtomReference,Set<AtomReference>> adjacency,AtomReference a,AtomReference b){adjacency.get(a).add(b);adjacency.get(b).add(a);}
}
