package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.system.SystemStateView;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-query-topology engineering controls, not experimental assay validation. */
class AdvisoryAlertNativeAcceptanceTest {
    static final Path CATALOG=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/advisory-alert-v1/ocl-pains-2026.7.2.json");
    static final OclMolecularBackend BACKEND=new OclMolecularBackend();
    static final ObjectMapper JSON=new ObjectMapper();
    static final String[][] WITNESSES={
        {"113","N#CC(C#N)C(C#N)C#N","N#CC(C#N)CC#N","8"},
        {"159","N#CC(=Cc1ccccc1)C#N","N#CCC(c1ccccc1)C#N","4"},
        {"162","N#CC(=C(S)S)C#N","N#CC(=C(S)O)C#N","4"},
        {"169","C=C1C(=O)NNC1=O","CC1C(=O)NNC1=O","2"},
        {"202","C#CC(=O)C#C","CCC(=O)C#C","2"},
        {"207","C=C1SC(=S)NC1=O","CC1SC(=S)NC1=O","1"},
        {"840","CC(=S)C","CC(=O)C","2"}};
    static List<Arguments> witnesses(){return Arrays.stream(WITNESSES).map(w->Arguments.of(Integer.parseInt(w[0]),w[1],w[2],Integer.parseInt(w[3]))).toList();}
    static AdvisoryAlertCatalog catalog()throws Exception{return AdvisoryAlertCatalog.decode(Files.readAllBytes(CATALOG));}
    @ParameterizedTest @MethodSource("witnesses")
    void independentlySpecifiedPositiveAndNearMiss(int index,String positive,String negative,int embeddings)throws Exception{
        var e=catalog().entries().get(index);var graph=BACKEND.decodeStructure("SMILES",positive);byte[] original=SystemStateView.bytes(graph);
        var hit=BACKEND.match(AdvisoryAlertCatalog.FORMAT,e.query(),graph);
        assertEquals(1,hit.queryToTargetAtomIds().size());
        var msg=hit.evidence().messages().stream().filter(x->x.startsWith("occurrenceEmbeddings=")).findFirst().orElseThrow();
        assertEquals(embeddings,JSON.readTree(msg.substring("occurrenceEmbeddings=".length())).path("compiledQueryIndexToTarget").size());
        assertTrue(hit.evidence().messages().contains("query="+e.query()));
        assertTrue(hit.evidence().messages().contains("querySha256="+e.querySha256()));
        assertTrue(hit.evidence().messages().contains("sourceStateSha256="+SystemStateView.digest(graph)));
        assertArrayEquals(original,SystemStateView.bytes(graph));
        assertTrue(BACKEND.match(AdvisoryAlertCatalog.FORMAT,e.query(),BACKEND.decodeStructure("SMILES",negative)).queryToTargetAtomIds().isEmpty());
        var atoms=new ArrayList<>(graph.atoms());Collections.reverse(atoms);var bonds=new ArrayList<>(graph.bonds());Collections.reverse(bonds);
        var reordered=BACKEND.match(AdvisoryAlertCatalog.FORMAT,e.query(),new MolecularGraph(atoms,bonds,graph.properties()));
        assertEquals(hit.queryToTargetAtomIds(),reordered.queryToTargetAtomIds());
        assertEquals(hit.evidence().messages().stream().filter(x->x.startsWith("occurrenceEmbeddings=")).toList(),reordered.evidence().messages().stream().filter(x->x.startsWith("occurrenceEmbeddings=")).toList());
    }
    @Test void all890ReceiveIndividualRepresentationDisposition()throws Exception {
        int executable=0,unsupported=0;var graph=BACKEND.decodeStructure("SMILES","C");
        for(var entry:catalog().entries()){
            try{var result=BACKEND.match(AdvisoryAlertCatalog.FORMAT,entry.query(),graph);assertTrue(result.evidence().messages().contains("querySha256="+entry.querySha256()));executable++;}
            catch(MolecularBackendException e){assertTrue(e.getMessage().contains("NATIVE_QUERY_UNSUPPORTED"),entry.index()+":"+e.getMessage());unsupported++;}
        }
        assertEquals(489,executable);assertEquals(401,unsupported);
    }
    @Test void historicalDefaultDelegationExact()throws Exception {
        var g=BACKEND.decodeStructure("SMILES","CCO");assertEquals(BACKEND.match("CO",g),BACKEND.match("ATHENA_SMARTS_ENVELOPE/1","CO",g));
        SubstructureMatcher defaultOnly=(q,t)->BACKEND.match(q,t);
        assertEquals(defaultOnly.match("CO",g),defaultOnly.match("ATHENA_SMARTS_ENVELOPE/1","CO",g));
        assertThrows(MolecularBackendException.class,()->defaultOnly.match(AdvisoryAlertCatalog.FORMAT,"CO",g));
        assertThrows(MolecularBackendException.class,()->BACKEND.match("OCL_IDCODE_QUERY/2026.7.3","CO",g));
    }
    @ParameterizedTest @ValueSource(strings={"schema","catalogId","catalogVersion","sourceJarSha256","orderedCatalogSha256","index","label","queryFormat","query","querySha256","order","truncate","extra","overflow-index","fractional-index"})
    void exactCatalogIdentityRejectsMutation(String field)throws Exception {
        var d=(ObjectNode)JSON.readTree(Files.readAllBytes(CATALOG));var entries=(ArrayNode)d.get("entries");var e=(ObjectNode)entries.get(113);
        switch(field){case "index"->e.put(field,114);case "overflow-index"->e.put("index",4294967409L);case "fractional-index"->e.put("index",113.0);case "order"->{var first=entries.get(0);entries.set(0,entries.get(1));entries.set(1,first);}case "truncate"->entries.remove(889);case "extra"->d.put("unapproved",true);default->{if(e.has(field))e.put(field,e.get(field).asText()+"x");else d.put(field,"changed");}}
        assertThrows(IllegalArgumentException.class,()->AdvisoryAlertCatalog.decode(JSON.writeValueAsBytes(d)));
    }
    @Test void immutableEntriesAndNoQualificationOnLoad()throws Exception {assertEquals(890,catalog().entries().size());assertThrows(UnsupportedOperationException.class,()->catalog().entries().clear());}
    @ParameterizedTest @ValueSource(strings={"","not an IDCode","\n","\u0080"})
    void malformedNativeRejected(String query)throws Exception{assertThrows(MolecularBackendException.class,()->BACKEND.match(AdvisoryAlertCatalog.FORMAT,query,BACKEND.decodeStructure("SMILES","C")));}
    @ParameterizedTest @ValueSource(strings={"graph","atom","bond","radical","dummy","stereo","h-conflict"})
    void unsupportedTargetAssertionsFailClosed(String variant)throws Exception {
        var g=BACKEND.decodeStructure("SMILES","CC");var atoms=new ArrayList<>(g.atoms());var bonds=new ArrayList<>(g.bonds());var a=atoms.getFirst();
        if(Set.of("atom","radical","dummy","stereo","h-conflict").contains(variant))atoms.set(0,new MolecularGraph.Atom(a.id(),variant.equals("dummy")?"*":a.element(),a.isotope(),a.formalCharge(),variant.equals("h-conflict")?9:a.explicitHydrogens(),a.aromatic(),variant.equals("stereo")?"ESR_OR_1":a.stereochemistry(),a.coordinates(),variant.equals("atom")?Map.of("query","any"):variant.equals("radical")?Map.of("athena.ocl.atomRadicalState/1","D"):a.properties()));
        if(variant.equals("bond")){var b=bonds.getFirst();bonds.set(0,new MolecularGraph.Bond(b.id(),b.firstAtomId(),b.secondAtomId(),b.order(),b.aromatic(),b.stereochemistry(),Map.of("coordination","known")));}
        var target=new MolecularGraph(atoms,bonds,variant.equals("graph")?Map.of("query","fragment"):Map.of());
        assertThrows(MolecularBackendException.class,()->BACKEND.match(AdvisoryAlertCatalog.FORMAT,catalog().entries().get(840).query(),target));
    }

    @ParameterizedTest @CsvSource(delimiter='|',value={
        "[C,N]|C|O", "[!#6]|N|C", "[c]|c1ccccc1|C", "[C;H3]|CC|C1CC1", "[C;H0]|C(C)(C)(C)C|C", "[C;R]|C1CC1|CCC", "[C;!R]|CCC|C1CC1", "C=C|C=C|CC", "C#C|C#C|CC", "[N+]|C[NH3+]|CN"})
    void nativeFeatureEngineeringControls(String sourceQuery,String positive,String negative)throws Exception {
        // New engineering queries only: no catalog entry is converted or re-encoded.
        var fragment=new com.actelion.research.chem.StereoMolecule();
        new com.actelion.research.chem.SmilesParser(com.actelion.research.chem.SmilesParser.SMARTS_MODE_IS_SMARTS).parse(fragment,sourceQuery);
        String nativeQuery=new com.actelion.research.chem.Canonizer(fragment).getIDCode();
        assertFalse(BACKEND.match(AdvisoryAlertCatalog.FORMAT,nativeQuery,BACKEND.decodeStructure("SMILES",positive)).queryToTargetAtomIds().isEmpty());
        assertTrue(BACKEND.match(AdvisoryAlertCatalog.FORMAT,nativeQuery,BACKEND.decodeStructure("SMILES",negative)).queryToTargetAtomIds().isEmpty());
    }
    @Test void knownUnqualifiedBondParityRemainsUnsupported()throws Exception {
        assertThrows(MolecularBackendException.class,()->BACKEND.match(AdvisoryAlertCatalog.FORMAT,catalog().entries().get(167).query(),BACKEND.decodeStructure("SMILES","C=C1SC=NC1=O")));
    }
    @Test void disconnectedExactOccurrencesRemainSeparate()throws Exception {
        var e=catalog().entries().get(840);
        var result=BACKEND.match(AdvisoryAlertCatalog.FORMAT,e.query(),disconnectedThione());
        assertEquals(2,result.queryToTargetAtomIds().size());
        assertEquals(2,result.evidence().messages().stream().filter(x->x.startsWith("occurrenceEmbeddings=")).count());
    }

    static MolecularGraph disconnectedThione()throws Exception {
        var g=BACKEND.decodeStructure("SMILES","CC(=S)C");var atoms=new ArrayList<MolecularGraph.Atom>(g.atoms());var bonds=new ArrayList<MolecularGraph.Bond>(g.bonds());
        for(var a:g.atoms())atoms.add(new MolecularGraph.Atom("second:"+a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties()));
        for(var b:g.bonds())bonds.add(new MolecularGraph.Bond("second:"+b.id(),"second:"+b.firstAtomId(),"second:"+b.secondAtomId(),b.order(),b.aromatic(),b.stereochemistry(),b.properties()));
        return new MolecularGraph(atoms,bonds,Map.of());
    }
    @Test void nativeThreeHydrogenFeatureIsOclThreeOrMoreBucket()throws Exception {
        var fragment=new com.actelion.research.chem.StereoMolecule();new com.actelion.research.chem.SmilesParser(com.actelion.research.chem.SmilesParser.SMARTS_MODE_IS_SMARTS).parse(fragment,"[C;H3]");
        assertFalse(BACKEND.match(AdvisoryAlertCatalog.FORMAT,new com.actelion.research.chem.Canonizer(fragment).getIDCode(),BACKEND.decodeStructure("SMILES","C")).queryToTargetAtomIds().isEmpty());
    }

    @Test void overlappingOccurrencesRetainSeparateAtomSets()throws Exception {
        var result=BACKEND.match(AdvisoryAlertCatalog.FORMAT,catalog().entries().get(840).query(),BACKEND.decodeStructure("SMILES","CC(=S)CC(=S)C"));assertEquals(2,result.queryToTargetAtomIds().size());var a=new HashSet<>(result.queryToTargetAtomIds().get(0).values());a.retainAll(result.queryToTargetAtomIds().get(1).values());assertFalse(a.isEmpty());
    }
    @Test void committedEntryDispositionsAreCompleteAndDoNotUpgradeParseSuccess()throws Exception {
        var d=JSON.readTree(Files.readAllBytes(CATALOG.resolveSibling("entry-dispositions.json")));assertFalse(d.path("catalogWideAbsenceAvailable").asBoolean(true));assertEquals(890,d.path("entries").size());var counts=new TreeMap<String,Integer>();
        for(int i=0;i<890;i++){var e=d.path("entries").get(i);assertEquals(i,e.path("index").asInt());assertEquals(catalog().entries().get(i).querySha256(),e.path("querySha256").asText());counts.merge(e.path("disposition").asText(),1,Integer::sum);}
        assertEquals(Map.of("REPRESENTATION_EXECUTION_QUALIFIED",7,"EXECUTABLE_INCOMPLETELY_QUALIFIED",482,"UNSUPPORTED_NATIVE_PROFILE",401),counts);
    }
    public static void main(String[] args)throws Exception {
        var rows=new ArrayList<Map<String,Object>>();var target=BACKEND.decodeStructure("SMILES","C");
        var admitted=Arrays.stream(WITNESSES).map(w->Integer.parseInt(w[0])).collect(java.util.stream.Collectors.toSet());
        for(var e:catalog().entries()){
            String disposition,reason;
            try{BACKEND.match(AdvisoryAlertCatalog.FORMAT,e.query(),target);disposition=admitted.contains(e.index())?"REPRESENTATION_EXECUTION_QUALIFIED":"EXECUTABLE_INCOMPLETELY_QUALIFIED";reason=admitted.contains(e.index())?"Independent original-query positive/near-miss and correspondence tests; bounded engineering qualification only":"Execution succeeds; no complete entry-specific acceptance matrix";}
            catch(MolecularBackendException ex){disposition=ex.getMessage().contains("NATIVE_QUERY_UNSUPPORTED")?"UNSUPPORTED_NATIVE_PROFILE":"MALFORMED_REJECTED";reason=ex.getCause()==null?ex.getMessage():ex.getCause().getMessage();}
            rows.add(Map.of("index",e.index(),"label",e.label(),"querySha256",e.querySha256(),"disposition",disposition,"reason",reason));
        }
        var witnessResults=new ArrayList<Object>();
        for(var w:WITNESSES){var e=catalog().entries().get(Integer.parseInt(w[0]));for(int i=1;i<=2;i++)witnessResults.add(Map.of("index",e.index(),"source",w[i],"result",BACKEND.match(AdvisoryAlertCatalog.FORMAT,e.query(),BACKEND.decodeStructure("SMILES",w[i]))));}
        byte[] bytes=SystemStateView.bytes(Map.of("entryDispositions",rows,"witnessResults",witnessResults,"catalogWideAbsenceAvailable",false));
        if(args.length>0)Files.write(Path.of(args[0]),bytes);else System.out.println(totah.lab.mnemosyne.EvidenceExchange.sha256(bytes));
    }

}
