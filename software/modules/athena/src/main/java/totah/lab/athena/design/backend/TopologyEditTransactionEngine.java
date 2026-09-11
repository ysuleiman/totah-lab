package totah.lab.athena.design.backend;

import java.util.*;

/** Target-neutral, fail-closed topology transaction engine with deterministic replay evidence. */
public final class TopologyEditTransactionEngine {
    private final CanonicalIdentityService identities;
    public TopologyEditTransactionEngine(CanonicalIdentityService identities){this.identities=Objects.requireNonNull(identities);}

    public Result apply(MolecularGraph parent, TopologyEdit edit, Authorization auth) throws MolecularBackendException {
        Objects.requireNonNull(parent);Objects.requireNonNull(edit);Objects.requireNonNull(auth);
        if(!auth.allowedTypes().contains(edit.type()))throw new IllegalArgumentException("unauthorized topology operation");
        if(!auth.permittedAtomIds().containsAll(edit.removedAtomIds()))throw new IllegalArgumentException("removed atom outside permitted region");
        overlap(edit.removedAtomIds(),auth.protectedAtomIds(),"protected atom");overlap(edit.openedBondIds(),auth.protectedBondIds(),"protected bond");
        String before=identities.identify(parent).canonicalKey();
        var atoms=new LinkedHashMap<String,MolecularGraph.Atom>();parent.atoms().forEach(a->atoms.put(a.id(),a));
        var bonds=new LinkedHashMap<String,MolecularGraph.Bond>();parent.bonds().forEach(b->bonds.put(b.id(),b));
        validateAuthorizationReferences(parent, edit, auth);
        Set<String> deletedAtoms=sortedSet(edit.removedAtomIds()),deletedBonds=new LinkedHashSet<>(),addedAtoms=new LinkedHashSet<>(),addedBonds=new LinkedHashSet<>();
        for(String id:deletedAtoms)if(atoms.remove(id)==null)throw new IllegalArgumentException("missing removed atom: "+id);
        bonds.values().stream().filter(b->deletedAtoms.contains(b.firstAtomId())||deletedAtoms.contains(b.secondAtomId())).map(MolecularGraph.Bond::id).toList().forEach(id->{bonds.remove(id);deletedBonds.add(id);});
        for(String id:edit.openedBondIds().stream().sorted().toList()){if(bonds.remove(id)==null)throw new IllegalArgumentException("missing opened bond: "+id);deletedBonds.add(id);}
        if(edit.replacementFragment()!=null){for(var a:edit.replacementFragment().atoms().stream().sorted(Comparator.comparing(MolecularGraph.Atom::id)).toList()){if(atoms.putIfAbsent(a.id(),a)!=null)throw new IllegalArgumentException("duplicate replacement atom: "+a.id());addedAtoms.add(a.id());}for(var b:edit.replacementFragment().bonds().stream().sorted(Comparator.comparing(MolecularGraph.Bond::id)).toList()){if(bonds.putIfAbsent(b.id(),b)!=null)throw new IllegalArgumentException("duplicate replacement bond: "+b.id());addedBonds.add(b.id());}}
        Map<String,String> attachmentMap=new LinkedHashMap<>();for(var a:edit.attachments().stream().sorted(Comparator.comparing(TopologyEdit.Attachment::bondId)).toList()){requireAtom(atoms,a.retainedParentAtomId());requireAtom(atoms,a.fragmentAtomId());addBond(bonds,a.bondId(),a.retainedParentAtomId(),a.fragmentAtomId(),a.order());addedBonds.add(a.bondId());attachmentMap.put(a.retainedParentAtomId()+"|"+a.bondId(),a.fragmentAtomId());}
        for(var c:edit.closures().stream().sorted(Comparator.comparing(TopologyEdit.Closure::bondId)).toList()){requireAtom(atoms,c.firstAtomId());requireAtom(atoms,c.secondAtomId());if(bondBetween(bonds.values(),c.firstAtomId(),c.secondAtomId()))throw new IllegalArgumentException("closure duplicates existing bond");addBond(bonds,c.bondId(),c.firstAtomId(),c.secondAtomId(),c.order());addedBonds.add(c.bondId());}
        MolecularGraph child=new MolecularGraph(List.copyOf(atoms.values()),List.copyOf(bonds.values()),parent.properties());
        validateConnected(child);validateOperation(parent,child,edit);validateSimpleValence(child);
        auth.protectedAtomIds().forEach(id->{if(!child.atom(id).equals(parent.atom(id)))throw new IllegalStateException("protected atom changed: "+id);});
        auth.protectedBondIds().forEach(id->{if(!child.bond(id).equals(parent.bond(id)))throw new IllegalStateException("protected bond changed: "+id);});
        Map<String,String> atomLineage=validatedAtomLineage(parent, child, edit);
        String after=identities.identify(child).canonicalKey();
        Map<String,String> bondLineage=new LinkedHashMap<>();parent.bonds().stream().filter(b->bonds.containsKey(b.id())).forEach(b->bondLineage.put(b.id(),b.id()));
        var receipt=new TopologyEditReceipt(edit.editId(),edit.type().name(),before,after,atomLineage,bondLineage,attachmentMap,addedAtoms,deletedAtoms,addedBonds,deletedBonds,edit.stereoDisposition(),List.of("AUTHORIZATION_PASS","PROTECTED_NEIGHBORHOOD_PASS","ATTACHMENT_MAPPING_PASS","CONNECTED_PRODUCT_PASS","SIMPLE_VALENCE_PASS","CANONICAL_IDENTITY_PASS"));
        return new Result(child,receipt);
    }
    public Result replay(MolecularGraph parent,TopologyEdit edit,Authorization auth,TopologyEditReceipt expected)throws MolecularBackendException{Result r=apply(parent,edit,auth);if(!r.receipt().equals(expected))throw new IllegalStateException("deterministic replay mismatch");return r;}
    private static void validateOperation(MolecularGraph p,MolecularGraph c,TopologyEdit e){
        boolean replacement=e.type()==TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT||e.type()==TopologyEdit.Type.LINKER_REPLACEMENT||e.type()==TopologyEdit.Type.SCAFFOLD_CORE_REPLACEMENT||e.type()==TopologyEdit.Type.RING_REPLACEMENT;
        if(replacement&&(e.replacementFragment()==null||e.removedAtomIds().isEmpty()))throw new IllegalArgumentException("replacement requires source and replacement fragment");
        if(replacement&&e.attachments().isEmpty())throw new IllegalArgumentException("replacement requires mapped attachment points");
        if(e.type()==TopologyEdit.Type.RING_REPLACEMENT&&(!cyclicSubgraph(p,e.removedAtomIds())||!cyclicSubgraph(e.replacementFragment(),e.replacementFragment().atoms().stream().map(MolecularGraph.Atom::id).collect(java.util.stream.Collectors.toSet()))))throw new IllegalArgumentException("ring replacement requires cyclic source and replacement");
        if(e.type()==TopologyEdit.Type.LINKER_REPLACEMENT&&(cyclicSubgraph(p,e.removedAtomIds())||e.attachments().size()!=2))throw new IllegalArgumentException("linker replacement requires an acyclic source and exactly two mapped attachments");
        if(e.type()==TopologyEdit.Type.SCAFFOLD_CORE_REPLACEMENT&&e.attachments().size()<2)throw new IllegalArgumentException("scaffold replacement requires at least two pharmacophore attachment mappings");
        if(e.type()==TopologyEdit.Type.BOUNDED_RING_CLOSURE){if(e.closures().size()!=1)throw new IllegalArgumentException("bounded closure requires exactly one bond");var x=e.closures().getFirst();int path=shortestPath(p,x.firstAtomId(),x.secondAtomId(),Set.of());if(path<2||path>7)throw new IllegalArgumentException("closure ring size outside bounded 3-8 range");}
        if(e.type()==TopologyEdit.Type.BOUNDED_RING_OPENING){if(e.openedBondIds().size()!=1)throw new IllegalArgumentException("bounded opening requires exactly one bond");String id=e.openedBondIds().iterator().next();var b=p.bond(id).orElseThrow(()->new IllegalArgumentException("missing opened bond: "+id));if(shortestPath(p,b.firstAtomId(),b.secondAtomId(),Set.of(id))<2)throw new IllegalArgumentException("opening bond is not cyclic");}
    }
    private static boolean cyclicSubgraph(MolecularGraph g,Set<String>s){long edges=g.bonds().stream().filter(b->s.contains(b.firstAtomId())&&s.contains(b.secondAtomId())).count();return !s.isEmpty()&&edges>=s.size();}
    private static Map<String,String> validatedAtomLineage(MolecularGraph parent,MolecularGraph child,TopologyEdit edit){
        Map<String,String> preserved=new LinkedHashMap<>();
        parent.atoms().stream().filter(a->child.atom(a.id()).isPresent()).sorted(Comparator.comparing(MolecularGraph.Atom::id))
                .forEach(a->preserved.put(a.id(),a.id()));
        boolean replacement=switch(edit.type()){
            case INDEXED_SUBGRAPH_REPLACEMENT,RING_REPLACEMENT,LINKER_REPLACEMENT,SCAFFOLD_CORE_REPLACEMENT->true;
            default->false;
        };
        if(replacement&&edit.replacementAtomLineage().isEmpty())
            throw new IllegalArgumentException("replacement operation requires atom lineage");
        Set<String>fragmentAtoms=edit.replacementFragment()==null?Set.of():edit.replacementFragment().atoms().stream()
                .map(MolecularGraph.Atom::id).collect(java.util.stream.Collectors.toSet());
        Set<String>targets=new HashSet<>();
        var validated=new TreeMap<String,String>();
        edit.replacementAtomLineage().forEach((source,target)->{
            if(parent.atom(source).isEmpty())throw new IllegalArgumentException("lineage source missing from parent: "+source);
            if(source.equals(target)&&preserved.containsKey(source))throw new IllegalArgumentException("lineage conflicts with preserved identity: "+source);
            if(!edit.removedAtomIds().contains(source))throw new IllegalArgumentException("lineage source outside replaced region: "+source);
            if(child.atom(target).isEmpty())throw new IllegalArgumentException("lineage target missing from child: "+target);
            if(!fragmentAtoms.contains(target))throw new IllegalArgumentException("lineage target outside replacement fragment: "+target);
            if(!targets.add(target))throw new IllegalArgumentException("multiple lineage sources map to target: "+target);
            validated.put(source,target);
        });
        var result=new LinkedHashMap<String,String>();result.putAll(preserved);result.putAll(validated);
        return Map.copyOf(result);
    }
    private static void validateAuthorizationReferences(MolecularGraph parent,TopologyEdit edit,Authorization auth){
        edit.removedAtomIds().forEach(id->parent.atom(id).orElseThrow(()->new IllegalArgumentException("missing removed atom: "+id)));
        edit.openedBondIds().forEach(id->parent.bond(id).orElseThrow(()->new IllegalArgumentException("missing opened bond: "+id)));
        edit.attachments().forEach(a->{if(!auth.permittedAtomIds().contains(a.retainedParentAtomId())&&!auth.protectedAtomIds().contains(a.retainedParentAtomId()))throw new IllegalArgumentException("attachment parent outside authorized/protected region: "+a.retainedParentAtomId());});
        edit.closures().forEach(c->{if(!auth.permittedAtomIds().contains(c.firstAtomId())||!auth.permittedAtomIds().contains(c.secondAtomId()))throw new IllegalArgumentException("closure endpoint outside permitted region");});
    }
    private static int shortestPath(MolecularGraph graph,String start,String end,Set<String>excludedBondIds){
        Map<String,Set<String>> adj=new HashMap<>();graph.atoms().forEach(a->adj.put(a.id(),new TreeSet<>()));
        graph.bonds().stream().filter(b->!excludedBondIds.contains(b.id())).forEach(b->{adj.get(b.firstAtomId()).add(b.secondAtomId());adj.get(b.secondAtomId()).add(b.firstAtomId());});
        var queue=new ArrayDeque<String>();var distance=new HashMap<String,Integer>();queue.add(start);distance.put(start,0);
        while(!queue.isEmpty()){String current=queue.remove();if(current.equals(end))return distance.get(current);for(String next:adj.getOrDefault(current,Set.of()))if(!distance.containsKey(next)){distance.put(next,distance.get(current)+1);queue.add(next);}}
        return -1;
    }
    private static boolean bondBetween(Collection<MolecularGraph.Bond>bonds,String x,String y){return bonds.stream().anyMatch(b->(b.firstAtomId().equals(x)&&b.secondAtomId().equals(y))||(b.firstAtomId().equals(y)&&b.secondAtomId().equals(x)));}
    private static void validateSimpleValence(MolecularGraph graph){
        Map<String,Integer> order=new HashMap<>();graph.atoms().forEach(a->order.put(a.id(),a.explicitHydrogens()));
        for(var b:graph.bonds()){int contribution=switch(b.order()){case SINGLE,AROMATIC->1;case DOUBLE->2;case TRIPLE->3;};order.compute(b.firstAtomId(),(k,v)->v+contribution);order.compute(b.secondAtomId(),(k,v)->v+contribution);}
        for(var atom:graph.atoms()){int maximum=switch(atom.element()){case "H","F","Cl","Br","I"->1;case "O"->atom.formalCharge()>0?3:2;case "N"->atom.formalCharge()>0?4:3;case "C"->4;case "P"->5;case "S"->6;default->8;};if(order.get(atom.id())>maximum)throw new IllegalArgumentException("valence exceeded for "+atom.id());}
    }
    private static <T extends Comparable<? super T>> LinkedHashSet<T> sortedSet(Collection<T> values){return values.stream().sorted().collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));}
    private static void addBond(Map<String,MolecularGraph.Bond>b,String id,String x,String y,MolecularGraph.BondOrder o){if(id==null||b.containsKey(id))throw new IllegalArgumentException("invalid/duplicate bond id");b.put(id,new MolecularGraph.Bond(id,x,y,o,o==MolecularGraph.BondOrder.AROMATIC,"UNSPECIFIED",Map.of()));}
    private static void requireAtom(Map<String,MolecularGraph.Atom>a,String id){if(!a.containsKey(id))throw new IllegalArgumentException("attachment atom missing: "+id);}
    private static void overlap(Set<String>a,Set<String>b,String label){Set<String>x=new HashSet<>(a);x.retainAll(b);if(!x.isEmpty())throw new IllegalArgumentException(label+": "+x);}
    private static void validateConnected(MolecularGraph g){if(g.atoms().isEmpty())throw new IllegalArgumentException("empty product");Map<String,Set<String>>adj=new HashMap<>();g.atoms().forEach(a->adj.put(a.id(),new HashSet<>()));g.bonds().forEach(b->{requireAtom(new LinkedHashMap<>(g.atoms().stream().collect(java.util.stream.Collectors.toMap(MolecularGraph.Atom::id,a->a))),b.firstAtomId());adj.get(b.firstAtomId()).add(b.secondAtomId());adj.get(b.secondAtomId()).add(b.firstAtomId());});Set<String>seen=new HashSet<>();Deque<String>q=new ArrayDeque<>();q.add(g.atoms().getFirst().id());while(!q.isEmpty()){String x=q.remove();if(seen.add(x))q.addAll(adj.get(x));}if(seen.size()!=g.atoms().size())throw new IllegalArgumentException("disconnected product");}
    public record Authorization(Set<TopologyEdit.Type>allowedTypes,Set<String>permittedAtomIds,Set<String>protectedAtomIds,Set<String>protectedBondIds){public Authorization{allowedTypes=Set.copyOf(allowedTypes);permittedAtomIds=Set.copyOf(permittedAtomIds);protectedAtomIds=Set.copyOf(protectedAtomIds);protectedBondIds=Set.copyOf(protectedBondIds);}}
    public record Result(MolecularGraph product,TopologyEditReceipt receipt){}
}
