package totah.lab.athena.design.backend.ocl;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import totah.lab.athena.design.backend.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** B00 regression witnesses. Exact pre-fix source and compiled suite remain in the immutable qualification records. */
public class OclQuerySemanticsQualificationTest {
    private static final OclMolecularBackend BACKEND = new OclMolecularBackend();
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    record Case(String id, String fixture, String query, String expected, String scope) { }

    static List<Case> cases() throws IOException {
        try (var input = Objects.requireNonNull(OclQuerySemanticsQualificationTest.class
                .getResourceAsStream("/b00/query-cases.tsv"));
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines().filter(s -> !s.startsWith("#") && !s.isBlank()).map(s -> {
                var a = s.split("\\|", -1); return new Case(a[0],a[1],a[2],a[3],a[4]);
            }).toList();
        }
    }
    static MolecularGraph fixture(String name) {
        String elements = switch (name) {
            case "separated" -> "CO"; case "propanol" -> "CCCO"; case "ethanol" -> "CCO"; case "propane" -> "CCC";
            case "benzene", "cyclohexane" -> "CCCCCC";
            case "carbonyl" -> "CO"; case "nitrile" -> "CN";
            case "methanol-H" -> "COH"; case "neutral-N", "positive-N" -> "N";
            default -> throw new IllegalArgumentException(name);
        };
        var atoms = new ArrayList<MolecularGraph.Atom>();
        var bonds = new ArrayList<MolecularGraph.Bond>();
        var order = name.equals("benzene") ? MolecularGraph.BondOrder.AROMATIC
                : name.equals("carbonyl") ? MolecularGraph.BondOrder.DOUBLE
                : name.equals("nitrile") ? MolecularGraph.BondOrder.TRIPLE : MolecularGraph.BondOrder.SINGLE;
        for (int i=0;i<elements.length();i++) {
            atoms.add(new MolecularGraph.Atom("id"+i, ""+elements.charAt(i), null,
                    name.equals("positive-N")?1:0,0,false,"UNSPECIFIED",
                    new MolecularGraph.Coordinates(i,0,0),Map.of("origin","synthetic-B00")));
            if(i>0 && !name.equals("separated"))bonds.add(bond("b"+i,"id"+(i-1),"id"+i,order));
        }
        if(name.equals("benzene")||name.equals("cyclohexane"))
            bonds.add(bond("close","id5","id0",order));
        return new MolecularGraph(atoms,bonds,Map.of("fixture",name,"state","B00-synthetic-v1"));
    }
    private static MolecularGraph.Bond bond(String id,String a,String b,MolecularGraph.BondOrder order) {
        return new MolecularGraph.Bond(id,a,b,order,false,"UNSPECIFIED",Map.of());
    }
    static String mappings(SubstructureMatcher.Result r) {
        return String.join(";",r.queryToTargetAtomIds().stream().map(m ->
                String.join(",",java.util.stream.IntStream.range(0,m.size())
                        .mapToObj(i -> m.get("query:"+i)).toList())).toList());
    }
    @TestFactory Stream<DynamicTest> reviewedOclDialectCases() throws IOException {
        return cases().stream().map(c -> DynamicTest.dynamicTest(c.id()+" / "+c.scope(), () -> {
            var g=fixture(c.fixture());var before=JSON.writeValueAsBytes(g);
            var result=BACKEND.match(c.query(),g);
            String expected=switch(c.id()) {
                case "ring","aromatic","uppercase-not-aliphatic"->"id0;id1;id2;id3;id4;id5";
                case "propane-atoms"->"id0;id1;id2";
                case "propane-bond"->"id0,id1;id1,id2";
                default->c.expected();
            };
            assertEquals(expected,mappings(result));
            assertArrayEquals(before,JSON.writeValueAsBytes(g),"original graph changed");
            for(var match:result.queryToTargetAtomIds())for(var id:match.values())assertTrue(g.atom(id).isPresent());
            assertEquals("2026.7.2/athena-ocl-occurrences/2",result.evidence().version());
            assertEquals("OPEN_CHEM_LIB",result.evidence().backend());
            assertEquals("substructure-match",result.evidence().operation());
            assertTrue(result.evidence().messages().contains("query="+c.query()));
            assertTrue(result.evidence().graphChanges().isEmpty());
        }));
    }
    @Test void originalStateAndMatchEvidenceRoundTripWithoutLoss() throws Exception {
        for(var c:cases()) {
            var graph=fixture(c.fixture());var result=BACKEND.match(c.query(),graph);
            assertEquals(graph,JSON.readValue(JSON.writeValueAsBytes(graph),MolecularGraph.class));
            assertEquals(result,JSON.readValue(JSON.writeValueAsBytes(result),SubstructureMatcher.Result.class));
            assertEquals(result,BACKEND.match(c.query(),JSON.readValue(JSON.writeValueAsBytes(graph),MolecularGraph.class)));
        }
    }
    @Test void repeatedCallsPreserveExactListOrderAndMappings() throws Exception {
        for(var c:cases()) {
            var expected=BACKEND.match(c.query(),fixture(c.fixture()));
            for(int i=0;i<12;i++)assertEquals(expected,BACKEND.match(c.query(),fixture(c.fixture())));
        }
    }
    @Test void stableIdsSurviveInputAtomPermutation() throws Exception {
        var g=fixture("ethanol");var atoms=new ArrayList<>(g.atoms());Collections.reverse(atoms);
        var reordered=new MolecularGraph(atoms,g.bonds(),g.properties());
        assertEquals(Map.of("query:0","id2"),BACKEND.match("[O]",reordered).queryToTargetAtomIds().getFirst());
        assertEquals(Map.of("query:0","id1","query:1","id2"),BACKEND.match("CO",reordered).queryToTargetAtomIds().getFirst());
    }
    @Test void orderingIsCanonicalAcrossAtomPermutation() throws Exception {
        var g=fixture("ethanol");var atoms=new ArrayList<>(g.atoms());Collections.reverse(atoms);
        var other=new MolecularGraph(atoms,g.bonds(),g.properties());
        var a=BACKEND.match("C",g).queryToTargetAtomIds();var b=BACKEND.match("C",other).queryToTargetAtomIds();
        assertEquals(new HashSet<>(a),new HashSet<>(b));assertEquals(a,b);
    }
    @Test void nestedMatchesDoNotConsumeOrPruneOtherQueries() throws Exception {
        var g=fixture("ethanol");var oxygen=BACKEND.match("O",g);
        assertEquals("id1,id2",mappings(BACKEND.match("CO",g)));
        assertEquals(oxygen,BACKEND.match("O",g));
    }
    @Test void resultCollectionsAreImmutable() throws Exception {
        var result=BACKEND.match("O",fixture("ethanol"));
        assertThrows(UnsupportedOperationException.class,()->result.queryToTargetAtomIds().clear());
        assertThrows(UnsupportedOperationException.class,()->result.queryToTargetAtomIds().getFirst().put("query:0","fake"));
    }
    @Test void topologyAndUnsupportedStereoFailuresAreChecked() {
        var g=fixture("ethanol");
        assertThrows(MolecularBackendException.class,()->BACKEND.match("C",new MolecularGraph(List.of(g.atoms().getFirst(),g.atoms().getFirst()),List.of(),Map.of())));
        var a=new MolecularGraph.Atom("s","C",null,0,0,false,"R",null,Map.of());
        assertThrows(MolecularBackendException.class,()->BACKEND.match("C",new MolecularGraph(List.of(a),List.of(),Map.of())));
    }
    @Test void distinctSymmetricTargetAtomsAreNotOmitted() throws Exception {
        var g=fixture("benzene");var r=BACKEND.match("[c]",g);
        assertEquals(6,r.queryToTargetAtomIds().size());
        assertEquals(g.atoms().size(),r.queryToTargetAtomIds().size());
    }
    @Test void positiveHydrogenAnnotationIsValidatedByMatch() throws Exception {
        var a=new MolecularGraph.Atom("c","C",null,0,1,false,"UNSPECIFIED",null,Map.of());
        var g=new MolecularGraph(List.of(a),List.of(),Map.of());
        assertThrows(MolecularBackendException.class,()->BACKEND.match("[CH4]",g));
        assertThrows(MolecularBackendException.class,()->new OclGraphMapper().validateHydrogenCounts(new OclGraphMapper().toOcl(g)));
    }
    @Test void invalidQueriesCannotBecomeEmptyResults() throws Exception {
        for(String q:List.of("[z2]","[C;Q]","[A]",""))
            assertThrows(MolecularBackendException.class,()->BACKEND.match(q,fixture("ethanol")));
    }
    @Test void unbalancedBranchCannotBeAccepted() throws Exception {
        assertThrows(MolecularBackendException.class,()->BACKEND.match("C(",fixture("ethanol")));
    }
    static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    /** Test-only replay artifact: binds exact query, original graph and native result, not a new production store. */
    public static void main(String[] args) throws Exception {
        var receipts=new ArrayList<Map<String,Object>>();
        for(var c:cases()) {
            var g=fixture(c.fixture());var result=BACKEND.match(c.query(),g);
            receipts.add(Map.of("case",c.id(),"query",c.query(),"querySha256",digest(c.query().getBytes(StandardCharsets.UTF_8)),
                    "sourceState",g,"sourceStateSha256",digest(JSON.writeValueAsBytes(g)),"result",result,
                    "adapterCountMode","Rigorous; canonical distinct target sets","scope",c.scope()));
        }
        Files.write(Path.of(args[0]),JSON.writeValueAsBytes(receipts));
    }
}
