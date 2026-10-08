package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.SSSearcher;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.system.SystemStateView;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Internal B00 adapter. Chemical parsing and matching remain exclusively OCL operations. */
final class OclOccurrenceMatcher {
    private OclOccurrenceMatcher() { }
    static final String IMPLEMENTATION = "athena-ocl-occurrences/2";

    static SubstructureMatcher.Result match(String query, OclGraphMapper.Mapping mapping)
            throws MolecularBackendException {
        try {
            validateEnvelope(query);
            if(!RuntimeIdentity.HASH.equals("2e1642f29f09c1def25dc405a77973c28bae02355a6807320e42f1dc4cd157c9"))
                throw new IllegalArgumentException("OCL runtime artifact outside qualified pin: "+RuntimeIdentity.HASH);
            new OclGraphMapper().validateHydrogenCounts(mapping);
            var parser = new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMARTS | SmilesParser.MODE_CREATE_SMARTS_WARNING);
            var fragment = new StereoMolecule();
            parser.parse(fragment, query);
            if (fragment.getAllAtoms() == 0 || !parser.getSmartsWarning().isEmpty())
                throw new IllegalArgumentException("empty or unresolved OCL query: " + parser.getSmartsWarning());
            for (int atom=0;atom<fragment.getAllAtoms();atom++)
                if(fragment.isExcludeGroupAtom(atom))throw new IllegalArgumentException("excluded query atoms unsupported by B00 correspondence");
            var searcher = new SSSearcher();
            searcher.setMol(fragment, mapping.molecule());
            // Enumerate embeddings first: Overlapping chooses an incidental representative.
            // Canonicalize ourselves by stable target IDs, retaining every alternative as provenance.
            searcher.findFragmentInMolecule(SSSearcher.cCountModeRigorous, SSSearcher.cDefaultMatchMode);
            Comparator<List<String>> order = (a,b) -> {
                for (int i=0;i<Math.min(a.size(),b.size());i++) {
                    int c=a.get(i).compareTo(b.get(i)); if(c!=0)return c;
                }
                return Integer.compare(a.size(),b.size());
            };
            var occurrences = new TreeMap<List<String>,SortedSet<List<String>>>(order);
            for (int[] embedding : searcher.getMatchList()) {
                var ids = new ArrayList<String>();
                for (int target : embedding) {
                    if (target < 0) throw new IllegalArgumentException("excluded query atoms are outside B00 correspondence domain");
                    var id = mapping.idByMapNumber().get(mapping.molecule().getAtomMapNo(target));
                    if(id==null)throw new IllegalArgumentException("unmapped OCL target atom");
                    ids.add(id);
                }
                var key = ids.stream().distinct().sorted().toList();
                occurrences.computeIfAbsent(key,k->new TreeSet<>(order)).add(List.copyOf(ids));
            }
            var results = new ArrayList<Map<String,String>>();
            var messages = new ArrayList<String>();
            messages.add("query="+query);
            messages.add("implementation="+IMPLEMENTATION);
            messages.add("compiler=SmilesParser/SMARTS_MODE_IS_SMARTS;warnings=reject;envelope=athena-query-envelope/1");
            messages.add("oclArtifactSha256=2e1642f29f09c1def25dc405a77973c28bae02355a6807320e42f1dc4cd157c9");
            messages.add("querySha256="+SystemStateView.digest(query));
            messages.add("sourceStateSha256="+SystemStateView.digest(mapping.source()));
            messages.add("occurrence=distinct-target-atom-set;representative=lexicographic-stable-ID-vector-in-compiled-query-index-order");
            messages.add("hydrogens=source-zero-unspecified;positive-source-count-validated-against-OCL-implicit-H;explicit-graph-H-separate;OCL-query-H-dialect-only");
            for (var occurrence : occurrences.entrySet()) {
                var canonical = occurrence.getValue().first();
                var result = new LinkedHashMap<String,String>();
                for (int i=0;i<canonical.size();i++) result.put("query:"+i,canonical.get(i));
                results.add(result);
                messages.add("occurrenceEmbeddings="+new String(SystemStateView.bytes(Map.of(
                        "targetAtomSet",occurrence.getKey(),"compiledQueryIndexToTarget",List.copyOf(occurrence.getValue()))),StandardCharsets.UTF_8));
            }
            var lineage = new TreeMap<String,String>();
            mapping.source().atoms().forEach(a->lineage.put(a.id(),a.id()));
            return new SubstructureMatcher.Result(results,new BackendEvidence(OclMolecularBackend.BACKEND,
                    OclMolecularBackend.VERSION+"/"+IMPLEMENTATION,"substructure-match",lineage,List.of(),messages));
        } catch (Exception failure) {
            throw new MolecularBackendException(IMPLEMENTATION+" query="+query+": "+failure.getMessage(),failure);
        }
    }

    static final String NATIVE_IMPLEMENTATION="athena-ocl-idcode-occurrences/1";
    static void validateNativeTarget(MolecularGraph graph)throws MolecularBackendException {
        if(!graph.properties().isEmpty())throw new MolecularBackendException("NATIVE_TARGET_UNSUPPORTED: opaque graph assertions have no admitted native interpretation");
        for(var a:graph.atoms()){
            for(var key:a.properties().keySet())if(!Set.of("origin","athena.ocl.atomRadicalState/1").contains(key))throw new MolecularBackendException("NATIVE_TARGET_UNSUPPORTED: opaque atom assertion "+key);
            if(a.properties().containsKey("athena.ocl.atomRadicalState/1")&&!a.properties().get("athena.ocl.atomRadicalState/1").equals("NONE"))throw new MolecularBackendException("NATIVE_TARGET_UNSUPPORTED: radical state");
            if(!Set.of("NONE","UNSPECIFIED","UNKNOWN","PARITY_1","PARITY_2").contains(a.stereochemistry()))throw new MolecularBackendException("NATIVE_TARGET_UNSUPPORTED: unrepresented stereo");
            if(a.element().equals("*")||a.element().equals("R")||a.element().equals("?"))throw new MolecularBackendException("NATIVE_TARGET_UNSUPPORTED: dummy/query atom");
        }
        for(var b:graph.bonds())if(!Set.of("origin").containsAll(b.properties().keySet()))throw new MolecularBackendException("NATIVE_TARGET_UNSUPPORTED: opaque bond assertions");
    }

    static SubstructureMatcher.Result matchNative(String query, OclGraphMapper.Mapping mapping)
            throws MolecularBackendException {
        try {
            if(query==null||query.isEmpty()||query.chars().anyMatch(c->c<32||c>127))
                throw new IllegalArgumentException("NATIVE_QUERY_MALFORMED: nonempty original native bytes required");
            if(!RuntimeIdentity.HASH.equals("2e1642f29f09c1def25dc405a77973c28bae02355a6807320e42f1dc4cd157c9"))
                throw new IllegalArgumentException("OCL runtime artifact outside qualified pin: "+RuntimeIdentity.HASH);
            new OclGraphMapper().validateHydrogenCounts(mapping);
            mapping.molecule().ensureHelperArrays(com.actelion.research.chem.Molecule.cHelperRings);
            for(int atom=0;atom<mapping.molecule().getAllAtoms();atom++) {
                if(mapping.molecule().getAtomicNo(atom)==0)throw new IllegalArgumentException("NATIVE_TARGET_UNSUPPORTED: unmapped chemical element");
                String id=mapping.idByMapNumber().get(mapping.molecule().getAtomMapNo(atom));
                if(id==null||mapping.source().atom(id).orElseThrow().aromatic()!=mapping.molecule().isAromaticAtom(atom))throw new IllegalArgumentException("NATIVE_TARGET_CONFLICT: declared aromaticity differs from literal mapped bonds");
            }
            var parser=new com.actelion.research.chem.IDCodeParser(false);
            if(parser.getIDCodeVersion(query)!=9)throw new IllegalArgumentException("NATIVE_QUERY_UNSUPPORTED: native encoding version");
            // Entire original string, including embedded coordinates, is preserved and compiled.
            var fragment=parser.getCompactMolecule(query);
            if(fragment==null||fragment.getAllAtoms()==0||!fragment.isFragment())throw new IllegalArgumentException("NATIVE_QUERY_MALFORMED: nonempty fragment query required");
            long atomMask=116735L;int bondMask=398;
            for(int atom=0;atom<fragment.getAllAtoms();atom++){
                if(fragment.isExcludeGroupAtom(atom))throw new IllegalArgumentException("NATIVE_QUERY_UNSUPPORTED: excluded query atoms have no total correspondence");
                if(fragment.getAtomESRType(atom)!=com.actelion.research.chem.Molecule.cESRTypeAbs||fragment.getAtomParity(atom)!=com.actelion.research.chem.Molecule.cAtomParityNone)
                    throw new IllegalArgumentException("NATIVE_QUERY_UNSUPPORTED: atom parity/ESR outside reviewed profile");
                if(fragment.getAtomicNo(atom)<=1||(fragment.getAtomQueryFeatures(atom)&~atomMask)!=0)
                    throw new IllegalArgumentException("NATIVE_QUERY_UNSUPPORTED: query atom/H/dummy/feature outside reviewed profile");
            }
            for(int bond=0;bond<fragment.getAllBonds();bond++)
                if(fragment.getBondParity(bond)!=com.actelion.research.chem.Molecule.cBondParityNone||(fragment.getBondQueryFeatures(bond)&~bondMask)!=0)
                    throw new IllegalArgumentException("NATIVE_QUERY_UNSUPPORTED: bond parity/feature outside reviewed profile");
            var searcher = new SSSearcher();
            searcher.setMol(fragment, mapping.molecule());
            // Enumerate embeddings first: Overlapping chooses an incidental representative.
            // Canonicalize ourselves by stable target IDs, retaining every alternative as provenance.
            searcher.findFragmentInMolecule(SSSearcher.cCountModeRigorous, SSSearcher.cDefaultMatchMode);
            Comparator<List<String>> order = (a,b) -> {
                for (int i=0;i<Math.min(a.size(),b.size());i++) {
                    int c=a.get(i).compareTo(b.get(i)); if(c!=0)return c;
                }
                return Integer.compare(a.size(),b.size());
            };
            var occurrences = new TreeMap<List<String>,SortedSet<List<String>>>(order);
            for (int[] embedding : searcher.getMatchList()) {
                var ids = new ArrayList<String>();
                for (int target : embedding) {
                    if (target < 0) throw new IllegalArgumentException("excluded query atoms are outside B00 correspondence domain");
                    var id = mapping.idByMapNumber().get(mapping.molecule().getAtomMapNo(target));
                    if(id==null)throw new IllegalArgumentException("unmapped OCL target atom");
                    ids.add(id);
                }
                var key = ids.stream().distinct().sorted().toList();
                occurrences.computeIfAbsent(key,k->new TreeSet<>(order)).add(List.copyOf(ids));
            }
            var results = new ArrayList<Map<String,String>>();
            var messages = new ArrayList<String>();
            messages.add("query="+query);
            messages.add("queryFormat=OCL_IDCODE_QUERY/2026.7.2");
            messages.add("supportedFeatureProfile=ATHENA_OCL_NATIVE_QUERY_PROFILE/1;atomMask=116735;bondMask=398;lists=literal;parity=NONE;ESR=ABS;excluded=reject;queryH=dummy=reject");
            messages.add("implementation="+NATIVE_IMPLEMENTATION);
            messages.add("compiler=IDCodeParser(false);nativeEncoding=9;originalEmbeddedCoordinates=retained;noSMARTS;noCanonicalReplacement");
            messages.add("oclArtifactSha256=2e1642f29f09c1def25dc405a77973c28bae02355a6807320e42f1dc4cd157c9");
            messages.add("querySha256="+totah.lab.mnemosyne.EvidenceExchange.sha256(query.getBytes(StandardCharsets.UTF_8)));
            messages.add("sourceStateSha256="+SystemStateView.digest(mapping.source()));
            messages.add("occurrence=distinct-target-atom-set;representative=lexicographic-stable-ID-vector-in-compiled-query-index-order");
            messages.add("hydrogens=source-zero-unspecified;positive-source-count-validated-against-OCL-implicit-H;explicit-graph-H-separate;native-query-H-features-only");
            for (var occurrence : occurrences.entrySet()) {
                var canonical = occurrence.getValue().first();
                var result = new LinkedHashMap<String,String>();
                for (int i=0;i<canonical.size();i++) result.put("query:"+i,canonical.get(i));
                results.add(result);
                messages.add("occurrenceEmbeddings="+new String(SystemStateView.bytes(Map.of(
                        "targetAtomSet",occurrence.getKey(),"compiledQueryIndexToTarget",List.copyOf(occurrence.getValue()))),StandardCharsets.UTF_8));
            }
            var lineage = new TreeMap<String,String>();
            mapping.source().atoms().forEach(a->lineage.put(a.id(),a.id()));
            return new SubstructureMatcher.Result(results,new BackendEvidence(OclMolecularBackend.BACKEND,
                    OclMolecularBackend.VERSION+"/"+NATIVE_IMPLEMENTATION,"substructure-match",lineage,List.of(),messages));
        } catch (Exception failure) {
            throw new MolecularBackendException(NATIVE_IMPLEMENTATION+" query="+query+": "+failure.getMessage(),failure);
        }
    }

    private static final class RuntimeIdentity {
        static final String HASH = hash();
        private static String hash() {
            try {
                var path=java.nio.file.Path.of(SSSearcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                var digest=java.security.MessageDigest.getInstance("SHA-256");
                try(var input=java.nio.file.Files.newInputStream(path)) {
                    byte[] buffer=new byte[16384];int n;
                    while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch(Exception unavailable) { return "UNAVAILABLE"; }
        }
    }

    /** Guard only lexical boundaries OCL does not reliably reject; this is not a SMARTS parser. */
    private static void validateEnvelope(String query) {
        if(query==null||query.isEmpty())throw new IllegalArgumentException("nonempty query required");
        var stack=new ArrayDeque<Character>();
        for(int i=0;i<query.length();i++) {
            char c=query.charAt(i);
            if(c<=32||c>=127)throw new IllegalArgumentException("whitespace, names, extensions and non-ASCII queries unsupported");
            if(stack.stream().noneMatch(x->x=='[')) {
                if(c=='.'&&(i==0||".(".indexOf(query.charAt(i-1))>=0))
                    throw new IllegalArgumentException("empty query component");
                if("-=#:~".indexOf(c)>=0&&i>0&&"-=#:~".indexOf(query.charAt(i-1))>=0)
                    throw new IllegalArgumentException("compound/repeated bond operators outside B00 envelope");
            }
            if(c=='['||c=='(')stack.push(c);
            if(c==']'||c==')') {
                if(stack.isEmpty()||stack.pop()!=(c==']'?'[':'('))throw new IllegalArgumentException("unbalanced query delimiters");
                if(i==0||query.charAt(i-1)=='('||query.charAt(i-1)=='['||(c==')'&&"-=#:~.,;!&/\\".indexOf(query.charAt(i-1))>=0))
                    throw new IllegalArgumentException("empty or unfinished query expression");
            }
        }
        if(!stack.isEmpty())throw new IllegalArgumentException("unclosed query delimiters");
        if("-=#:~.,;!&/\\%".indexOf(query.charAt(query.length()-1))>=0)
            throw new IllegalArgumentException("unfinished query suffix");
    }
}
