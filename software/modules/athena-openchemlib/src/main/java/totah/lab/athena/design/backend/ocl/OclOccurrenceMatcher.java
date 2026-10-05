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
