package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.IDCodeParser;
import com.actelion.research.chem.mmp.MMPEnumerator;
import com.actelion.research.chem.mmp.MMPFragmenter;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.knowledge.MatchedPairExtractor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** Bounded adapter of OCL extraction/enumeration, not a second fragmenter or molecular editor. */
public final class OclMatchedPairExtractor implements MatchedPairExtractor {
    public static final String ALGORITHM = "OCL/" + OclMolecularBackend.VERSION
            + "/MMPFragmenter(false)+MMPEnumerator(1.1)/single-cut/source-map-v3";
    private final OclMolecularBackend backend = new OclMolecularBackend();
    private record Occurrence(Source source, String identity, Fragment fragment, int size) { }

    @Override public Extraction extract(List<Source> input, int maximumVariableAtoms) {
        if (input.size() > 128 || maximumVariableAtoms < 1 || maximumVariableAtoms > 16)
            throw new IllegalArgumentException("bounded extraction: <=128 sources, variable size 1..16");
        var sources = input.stream().sorted(Comparator.comparing(Source::id)).toList();
        if (sources.stream().map(Source::id).distinct().count() != sources.size())
            throw new IllegalArgumentException("duplicate source IDs");
        var issues = new ArrayList<Issue>(); var occurrences = new ArrayList<Occurrence>();
        for (var source : sources) {
            var local = new ArrayList<Occurrence>();
            try {
                source.graph().validateTopology(true);
                if (source.graph().atoms().size() > 80) throw new IllegalArgumentException("MAXIMUM_80_ATOMS");
                if (source.graph().atoms().stream().anyMatch(a -> a.element().equals("H")))
                    throw new IllegalArgumentException("EXPLICIT_H_ATOMS_UNQUALIFIED_OCL_REMOVAL");
                backend.sanitize(source.graph(), new MolecularSanitizer.SanitizationPolicy(Set.of(), true));
                String identity = backend.identify(source.graph()).canonicalKey();
                var mapper = new OclGraphMapper(); var mapping = mapper.toOcl(source.graph());
                mapper.validateHydrogenCounts(mapping);
                var molecule = fragmentationMolecule(mapping);
                var contextGraph = backend.absoluteStereo(source.graph());
                // OCL removes explicit H in its constructor. Input graph is immutable; map numbers survive helper reordering.
                var fragmenter = new MMPFragmenter(molecule);
                boolean doubleCut = false, sizeExcluded = false;
                for (var cut : fragmenter.getMoleculeIndexesID(false)) {
                    if (cut.getKeysID().length != 1) { doubleCut = true; continue; }
                    if (cut.getValueIDAtoms() < 1 || cut.getValueIDAtoms() > maximumVariableAtoms) { sizeExcluded = true; continue; }
                    int bondIndex = cut.getBondIndexes()[0], variableIndex = cut.getValueAtomIndexes()[0];
                    String variableAnchor = mapping.idByMapNumber().get(molecule.getAtomMapNo(variableIndex));
                    String a = mapping.idByMapNumber().get(molecule.getAtomMapNo(molecule.getBondAtom(0, bondIndex)));
                    String b = mapping.idByMapNumber().get(molecule.getAtomMapNo(molecule.getBondAtom(1, bondIndex)));
                    var bond = source.graph().bonds().stream().filter(x -> endpoints(x, a, b)).findFirst().orElseThrow();
                    // OCL's valueAtomIndexes assumes fragment traversal order follows bond endpoint order.
                    // Prove membership against its actual canonical fragments instead of trusting that hint.
                    var sideA = component(source.graph(), a, bond.id());
                    var sideB = component(source.graph(), b, bond.id());
                    String idA = attachedIdentity(molecule, mapping.idByMapNumber(), sideA, a);
                    String idB = attachedIdentity(molecule, mapping.idByMapNumber(), sideB, b);
                    String anchor;
                    Set<String> variableAtoms;
                    if (idA.equals(cut.getKeysID()[0]) && idB.equals(cut.getValueID())) {
                        anchor = a; variableAnchor = b; variableAtoms = sideB;
                    } else if (idB.equals(cut.getKeysID()[0]) && idA.equals(cut.getValueID())) {
                        anchor = b; variableAnchor = a; variableAtoms = sideA;
                    } else throw new IllegalArgumentException("FRAGMENT_MEMBERSHIP_UNPROVEN");
                    var constantAtoms = source.graph().atoms().stream().map(MolecularGraph.Atom::id)
                            .filter(x -> !variableAtoms.contains(x)).collect(Collectors.toCollection(TreeSet::new));
                    var fragment = new Fragment(cut.getKeysID()[0], cut.getValueID(), bond.id(), anchor, variableAnchor,
                            constantAtoms, variableAtoms, context(contextGraph, anchor, bond.id()));
                    local.add(new Occurrence(source, identity, fragment, cut.getValueIDAtoms()));
                }
                if (doubleCut) issues.add(new Issue(source.id(), "DOUBLE_CUT_AVAILABLE_BUT_MAPPING_NOT_QUALIFIED"));
                if (sizeExcluded) issues.add(new Issue(source.id(), "VARIABLE_SIZE_EXCLUDED"));
                if (local.isEmpty()) issues.add(new Issue(source.id(), "NO_ELIGIBLE_SINGLE_CUT"));
                occurrences.addAll(local);
            } catch (MolecularBackendException | RuntimeException error) {
                issues.add(new Issue(source.id(), "SOURCE_REJECTED:" + error.getMessage()));
            }
        }
        if (occurrences.size() > 1024) {
            issues.add(new Issue("dataset", "FRAGMENT_BUDGET_EXCEEDED"));
            return new Extraction(List.of(), issues, ALGORITHM);
        }
        occurrences.sort(Comparator.comparing((Occurrence x) -> x.source().id())
                .thenComparing(x -> x.fragment().constant()).thenComparing(x -> x.fragment().variable())
                .thenComparing(x -> x.fragment().cutBond()));
        var variables = occurrences.stream().map(x -> x.fragment().variable()).distinct().sorted().toList();
        var groups = new TreeMap<Integer, HashMap<String, ArrayList<int[]>>>();
        for (int i = 0; i < occurrences.size(); i++) {
            var o = occurrences.get(i);
            groups.computeIfAbsent(o.size(), k -> new HashMap<>()).computeIfAbsent(o.fragment().constant(), k -> new ArrayList<>())
                    .add(new int[]{Collections.binarySearch(variables, o.fragment().variable()), i});
        }
        var pairs = new TreeMap<String, Pair>();
        try {
            for (int a : groups.keySet()) for (int b : groups.keySet()) {
                if (a > b) continue;
                var enumeration = new MMPEnumerator(new int[]{a, b}, groups.get(a), groups.get(b), "1.1").getMMPEnumeration();
                for (var entries : enumeration.values()) for (var entry : entries) {
                    var left = occurrences.get(Integer.parseInt(entry[0])); var right = occurrences.get(Integer.parseInt(entry[1]));
                    if (left.identity().equals(right.identity())) continue;
                    if (left.fragment().variable().compareTo(right.fragment().variable()) > 0) {
                        var swap = left; left = right; right = swap;
                    }
                    String key = left.source().id()+"\n"+right.source().id()+"\n"+left.fragment().cutBond()+"\n"+right.fragment().cutBond()+"\n"+left.fragment().constant();
                    if (pairs.containsKey(key)) continue;
                    var lc = attachedCore(left.source(), left.fragment());
                    var rc = attachedCore(right.source(), right.fragment());
                    // Identity was proved against the exact toolkit cap; match its absolute stereo
                    // through the existing bounded attributed-graph search, without removing the cap.
                    CanonicalIdentityService matcher = backend::identify;
                    var proof = matcher.correspondence(lc, rc);
                    final String la = left.fragment().constantAnchor(), ra = right.fragment().constantAnchor();
                    var leftAtoms = left.fragment().constantAtoms();
                    var rightAtoms = right.fragment().constantAtoms();
                    var alternatives = proof.alternatives().stream().filter(m -> ra.equals(m.atoms().get(la)))
                            .map(m -> retainedMapping(m, lc, leftAtoms, rightAtoms)).distinct().toList();
                    if (!proof.exhaustive() || alternatives.isEmpty()) {
                        issues.add(new Issue(left.source().id()+"/"+right.source().id(), "CORE_CORRESPONDENCE_UNPROVEN")); continue;
                    }
                    pairs.put(key, new Pair(left.source(), right.source(), left.identity(), right.identity(), left.fragment(), right.fragment(),
                            new CanonicalIdentityService.Correspondence(alternatives, true, proof.visitedStates()), ALGORITHM));
                    if (pairs.size() > 4096) throw new IllegalArgumentException("PAIR_BUDGET_EXCEEDED");
                }
            }
        } catch (IOException | MolecularBackendException | RuntimeException error) {
            // A partial enumeration must never masquerade as complete knowledge.
            issues.add(new Issue("dataset", "EXTRACTION_FAILED:"+error.getMessage())); pairs.clear();
        }
        issues.sort(Comparator.comparing(Issue::source).thenComparing(Issue::reason));
        return new Extraction(new ArrayList<>(pairs.values()), issues, ALGORITHM);
    }
    /** A toolkit-local representation, never a replacement for the ordered source graph. */
    private static StereoMolecule fragmentationMolecule(OclGraphMapper.Mapping mapping) {
        var source = mapping.molecule();
        source.ensureHelperArrays(Molecule.cHelperCIP);
        var canonicalizer = new Canonizer(source);
        String identity = canonicalizer.getIDCode();
        var indexes = canonicalizer.getGraphIndexes();
        var molecule = new IDCodeParser().getCompactMolecule(identity);
        if (molecule.getAllAtoms() != source.getAllAtoms() || indexes.length != source.getAllAtoms()
                || !identity.equals(new Canonizer(molecule).getIDCode()))
            throw new IllegalArgumentException("FRAGMENTATION_REPRESENTATION_UNPROVEN");
        var seen = new HashSet<Integer>();
        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] < 0 || indexes[i] >= indexes.length || !seen.add(indexes[i]))
                throw new IllegalArgumentException("FRAGMENTATION_SOURCE_MAPPING_UNPROVEN");
            molecule.setAtomMapNo(indexes[i], source.getAtomMapNo(i), false);
        }
        // MMP's cut representation depends on index-relative stereo and its 2D drawing.
        // Decode the same canonical identity for every input ordering, then restore source
        // map numbers through OCL's own canonical permutation. No source atoms are reordered.
        molecule.ensureHelperArrays(Molecule.cHelperCIP);
        return molecule;
    }
    private static String attachedIdentity(StereoMolecule source, Map<Integer,String> ids,
                                           Set<String> atoms, String anchor) {
        return new Canonizer(attachedMolecule(source, ids, atoms, anchor), Canonizer.ENCODE_ATOM_CUSTOM_LABELS).getIDCode();
    }
    private static StereoMolecule attachedMolecule(StereoMolecule source, Map<Integer,String> ids,
                                                  Set<String> atoms, String anchor) {
        source.ensureHelperArrays(Molecule.cHelperParities);
        boolean[] include = new boolean[source.getAllAtoms()];
        int anchorIndex = -1;
        for (int i=0;i<source.getAllAtoms();i++) {
            String id = ids.get(source.getAtomMapNo(i)); include[i] = atoms.contains(id);
            if (anchor.equals(id)) anchorIndex=i;
        }
        if (anchorIndex < 0 || !include[anchorIndex]) throw new IllegalArgumentException("fragment anchor missing");
        int boundaryBond = -1, outsideEnd = -1;
        for (int bond = 0; bond < source.getAllBonds(); bond++) {
            int a = source.getBondAtom(0, bond), b = source.getBondAtom(1, bond);
            if (include[a] == include[b]) continue;
            if (boundaryBond >= 0 || (include[a] ? a : b) != anchorIndex)
                throw new IllegalArgumentException("fragment requires one cut at its anchor");
            boundaryBond = bond; outsideEnd = include[a] ? 1 : 0;
        }
        if (boundaryBond < 0) throw new IllegalArgumentException("fragment cut missing");
        var fragment = new StereoMolecule(); int[] map = new int[include.length];
        source.copyMoleculeByAtoms(fragment, include, true, map);
        int dummy = fragment.addAtom(MMPFragmenter.FRAGMENT_ATOMIC_NO);
        fragment.setAtomCustomLabel(dummy, MMPFragmenter.FRAGMENT_DELIMITER);
        fragment.addBond(map[anchorIndex], dummy, Molecule.cBondTypeSingle);
        fragment.setFragment(false); // OCL MMP getFragments() returns concrete capped fragments.
        // The cap takes the removed neighbor's place in the parity permutation. Ask OCL
        // to translate source parity, not copy index-relative numbers or trust a lost wedge.
        map[source.getBondAtom(outsideEnd, boundaryBond)] = dummy;
        for (int i = 0; i < include.length; i++) if (include[i]) {
            fragment.setAtomParity(map[i], source.translateTHParity(i, map), false);
        }
        fragment.setParitiesValid(0);
        fragment.setStereoBondsFromParity();
        return fragment;
    }
    private static boolean endpoints(MolecularGraph.Bond bond, String a, String b) {
        return bond.firstAtomId().equals(a) && bond.secondAtomId().equals(b)
                || bond.firstAtomId().equals(b) && bond.secondAtomId().equals(a);
    }
    private static Set<String> component(MolecularGraph graph, String seed, String cut) {
        var seen = new TreeSet<String>(); var queue = new ArrayDeque<String>(); queue.add(seed);
        while (!queue.isEmpty()) {
            String x = queue.removeFirst(); if (!seen.add(x)) continue;
            for (var b : graph.bonds()) if (!b.id().equals(cut)) {
                if (b.firstAtomId().equals(x)) queue.add(b.secondAtomId());
                if (b.secondAtomId().equals(x)) queue.add(b.firstAtomId());
            }
        }
        return seen;
    }
    private MolecularGraph attachedCore(Source source, Fragment occurrence) throws MolecularBackendException {
        var mapper = new OclGraphMapper();
        var mapping = mapper.toOcl(source.graph());
        var molecule = attachedMolecule(fragmentationMolecule(mapping), mapping.idByMapNumber(),
                occurrence.constantAtoms(), occurrence.constantAnchor());
        if (!new Canonizer(molecule, Canonizer.ENCODE_ATOM_CUSTOM_LABELS).getIDCode().equals(occurrence.constant()))
            throw new MolecularBackendException("CORE_ATTACHMENT_IDENTITY_UNPROVEN");
        // Give the temporary cap a collision-free local ID; it never enters returned lineage.
        String cap = "attachment-cap";
        while (source.graph().atom(cap).isPresent()) cap += ":";
        var ids = new HashMap<>(mapping.idByMapNumber());
        int capMap = source.graph().atoms().size() + 1;
        ids.put(capMap, cap);
        molecule.setAtomMapNo(molecule.getAllAtoms() - 1, capMap, false);
        molecule.ensureHelperArrays(Molecule.cHelperCIP);
        var graph = mapper.fromOcl(new OclGraphMapper.Mapping(molecule, source.graph(), ids), molecule);
        return backend.absoluteStereo(graph, new OclGraphMapper.Mapping(molecule, graph, ids));
    }
    private static CanonicalIdentityService.Mapping retainedMapping(CanonicalIdentityService.Mapping mapping,
            MolecularGraph capped, Set<String> left, Set<String> right) {
        var atoms = new TreeMap<String, String>();
        mapping.atoms().forEach((a, b) -> { if (left.contains(a)) atoms.put(a, b); });
        if (!atoms.keySet().equals(left) || !new HashSet<>(atoms.values()).equals(right))
            throw new IllegalArgumentException("CORE_ATTACHMENT_MAPPING_UNPROVEN");
        var bonds = new TreeMap<String, String>();
        capped.bonds().stream().filter(b -> left.contains(b.firstAtomId()) && left.contains(b.secondAtomId()))
                .forEach(b -> bonds.put(b.id(), mapping.bonds().get(b.id())));
        return new CanonicalIdentityService.Mapping(atoms, bonds);
    }
    /** Deliberately narrow radius-one retained-side signature, NOT mmpdb/Morgan environment equivalence. */
    private static String context(MolecularGraph graph, String anchor, String cut) {
        var neighbors = new ArrayList<String>();
        for (var b : graph.bonds()) if (!b.id().equals(cut) && (b.firstAtomId().equals(anchor) || b.secondAtomId().equals(anchor))) {
            String other = b.firstAtomId().equals(anchor) ? b.secondAtomId() : b.firstAtomId();
            neighbors.add(b.order()+":"+b.aromatic()+":"+label(graph.atom(other).orElseThrow()));
        }
        Collections.sort(neighbors);
        return "retained-shell1/v2:"+label(graph.atom(anchor).orElseThrow())+neighbors;
    }
    private static String label(MolecularGraph.Atom a) {
        return a.element()+":"+a.isotope()+":"+a.formalCharge()+":"+a.explicitHydrogens()+":"+a.aromatic()+":"+a.stereochemistry();
    }
}
