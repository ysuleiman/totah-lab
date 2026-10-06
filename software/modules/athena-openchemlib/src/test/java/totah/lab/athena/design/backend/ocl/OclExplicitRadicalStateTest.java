package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Molecule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OclExplicitRadicalStateTest {
    private static final String KEY=OclGraphMapper.RADICAL;
    private final OclMolecularBackend backend=OclMolecularBackend.forExplicitRadicalState();
    private static MolecularGraph graph(String state) {
        var props=new TreeMap<String,String>();props.put("source","synthetic");if(state!=null)props.put(KEY,state);
        return new MolecularGraph(List.of(new MolecularGraph.Atom("stable-C","C",13,1,0,false,"UNSPECIFIED",new MolecularGraph.Coordinates(1,2,0),props)),List.of(),Map.of("source","fixture"));
    }
    @ParameterizedTest @ValueSource(strings={"NONE","S","D","T"})
    void exactSourceStateSurvives(String token)throws Exception {
        var source=graph(token);var mapper=new OclGraphMapper(true);var mapped=mapper.toOcl(source);
        int expected=switch(token){case "NONE"->Molecule.cAtomRadicalStateNone;case "S"->Molecule.cAtomRadicalStateS;case "D"->Molecule.cAtomRadicalStateD;default->Molecule.cAtomRadicalStateT;};
        assertEquals(expected,mapped.molecule().getAtomRadical(0));
        assertEquals(source,mapper.fromOcl(mapped,mapped.molecule()));
        var json=new ObjectMapper();assertEquals(source,json.readValue(json.writeValueAsBytes(source),MolecularGraph.class));
        assertTrue(backend.identify(source).evidence().version().endsWith("/explicit-radical-representation/1"));
    }
    @Test void absentIsNotNoneAndDeltaPreservesTheDifference()throws Exception {
        assertFalse(backend.decodeStructure("SMILES","C").atoms().getFirst().properties().containsKey(KEY));
        var absent=graph(null);var none=graph("NONE");var mapper=new OclGraphMapper(true);var mapping=mapper.toOcl(absent);
        assertEquals(absent,mapper.fromOcl(mapping,mapping.molecule()));assertFalse(absent.equals(none));
        var delta=MolecularGraph.Delta.between(absent,none);assertTrue(delta.chemicalGraphChanged());assertEquals(none,delta.replay(absent));
    }
    @ParameterizedTest @ValueSource(strings={"unknown","","DOUBLEt","32"})
    void invalidValueFailsWithoutLosingOriginal(String token)throws Exception {
        var source=graph(token);var before=new ObjectMapper().writeValueAsBytes(source);
        assertThrows(MolecularBackendException.class,()->backend.identify(source));
        assertArrayEquals(before,new ObjectMapper().writeValueAsBytes(source));assertEquals(token,source.atoms().getFirst().properties().get(KEY));
    }
    @Test void contradictionAndInferenceAreRejected()throws Exception {
        for(String token:Arrays.asList(null,"NONE","D")) {
            var source=graph(token);var mapper=new OclGraphMapper(true);var mapping=mapper.toOcl(source);
            mapping.molecule().setAtomRadical(0,Molecule.cAtomRadicalStateT);
            assertThrows(MolecularBackendException.class,()->mapper.fromOcl(mapping,mapping.molecule()));
            assertEquals(token,source.atoms().getFirst().properties().get(KEY));
        }
    }
    @ParameterizedTest @ValueSource(strings={"[CH3]","[CH2]","[O]","[13CH3]","C[C@H](F)C[O]"})
    void decodingIsOptInAndHistoricalRejectionSurvives(String smiles)throws Exception {
        var decoded=backend.decodeStructure("SMILES",smiles);
        assertTrue(decoded.atoms().stream().anyMatch(a->!"NONE".equals(a.properties().get(KEY))));
        assertThrows(MolecularBackendException.class,()->new OclMolecularBackend().decodeStructure("SMILES",smiles));
        assertThrows(MolecularBackendException.class,()->OclMolecularBackend.forChemicalStateValidation().decodeStructure("SMILES",smiles));
        assertEquals(decoded,backend.decodeStructure("SMILES",smiles));
    }
    @Test void explicitHydrogenAndAtomPermutationKeepStableState()throws Exception {
        var atoms=List.of(new MolecularGraph.Atom("h","H",null,0,0,false,"UNSPECIFIED",null,Map.of(KEY,"NONE")),new MolecularGraph.Atom("c","C",13,0,0,false,"UNSPECIFIED",null,Map.of(KEY,"D")));
        var source=new MolecularGraph(atoms,List.of(new MolecularGraph.Bond("b","c","h",MolecularGraph.BondOrder.SINGLE,false,"UNSPECIFIED",Map.of())),Map.of());
        var mapper=new OclGraphMapper(true);var mapping=mapper.toOcl(source);mapping.molecule().swapAtoms(0,1);
        assertEquals(source,mapper.fromOcl(mapping,mapping.molecule()));
    }
    @Test void suppliedHydrogenConstraintRemainsIndependent()throws Exception {
        for(int h:new int[]{3,4}) {
            var source=new MolecularGraph(List.of(new MolecularGraph.Atom("c","C",null,0,h,false,"UNSPECIFIED",null,Map.of(KEY,"D"))),List.of(),Map.of());
            if(h==3)assertNotNull(backend.identify(source));
            else assertThrows(MolecularBackendException.class,()->backend.identify(source));
            assertEquals(h,source.atoms().getFirst().explicitHydrogens());
        }
    }
    @Test void allUnqualifiedOperationsFailExplicitly()throws Exception {
        var source=graph("D");
        assertAll(
            ()->assertThrows(MolecularBackendException.class,()->backend.absoluteStereo(source)),
            ()->assertThrows(MolecularBackendException.class,()->backend.absoluteStereo(source,new OclGraphMapper(true).toOcl(source))),
            ()->assertThrows(MolecularBackendException.class,()->backend.sanitize(source,null)),
            ()->assertThrows(MolecularBackendException.class,()->backend.validate(source)),
            ()->assertThrows(MolecularBackendException.class,()->backend.validateDimensions(source,null)),
            ()->assertThrows(MolecularBackendException.class,()->backend.generate(source,null)),
            ()->assertThrows(MolecularBackendException.class,()->backend.minimize(source,null)),
            ()->assertThrows(MolecularBackendException.class,()->backend.match("C",source)),
            ()->assertThrows(MolecularBackendException.class,()->backend.correspondence(source,source)),
            ()->assertThrows(MolecularBackendException.class,()->backend.associate(source,source)));
    }
    private static totah.lab.mnemosyne.ScientificReference ref(totah.lab.mnemosyne.ScientificReference.Kind k,String id) {
        return new totah.lab.mnemosyne.ScientificReference(k,"v03-synthetic",id,"1");
    }
    static byte[] journal(java.nio.file.Path directory)throws Exception {
        var exchange=new totah.lab.mnemosyne.EvidenceExchange();var history=new totah.lab.mnemosyne.EvidenceHistory();
        var method=ref(totah.lab.mnemosyne.ScientificReference.Kind.METHOD,"explicit-radical-representation-1");
        var context=ref(totah.lab.mnemosyne.ScientificReference.Kind.CONTEXT,"source");
        for(String token:List.of("NONE","S","D","T","unknown")) {
            byte[] bytes=totah.lab.athena.system.SystemStateView.bytes(graph(token));
            var provenance=new totah.lab.mnemosyne.Observation.Provenance(ref(totah.lab.mnemosyne.ScientificReference.Kind.SOURCE,token),ref(totah.lab.mnemosyne.ScientificReference.Kind.ARTIFACT,token),ref(totah.lab.mnemosyne.ScientificReference.Kind.RECEIPT,token),method,"synthetic source graph",List.of());
            history=history.append(new totah.lab.mnemosyne.EvidenceEnvelope(ref(totah.lab.mnemosyne.ScientificReference.Kind.EVIDENCE_ENVELOPE,token),"athena:source-graph","application/json","1",Optional.of(Base64.getEncoder().encodeToString(bytes)),Optional.empty(),totah.lab.mnemosyne.EvidenceExchange.sha256(bytes),provenance,method,context,List.of(new totah.lab.mnemosyne.EvidenceSubject(context,"atom","stable-C",List.of())),List.of(),List.of("synthetic representation only"),java.time.Instant.parse("2026-10-06T00:00:00Z")));
        }
        var snapshot=exchange.snapshot(ref(totah.lab.mnemosyne.ScientificReference.Kind.SNAPSHOT,"radicals"),ref(totah.lab.mnemosyne.ScientificReference.Kind.ACTIVITY,"test"),java.time.Instant.parse("2026-10-06T00:00:00Z"),Optional.empty(),history);
        var bytes=exchange.encode(snapshot);var pin=new totah.lab.mnemosyne.EvidenceAdmission.Pin(snapshot.manifest().reference(),totah.lab.mnemosyne.EvidenceExchange.sha256(bytes));
        java.nio.file.Files.createDirectories(directory);var catalog=new totah.lab.mnemosyne.EvidenceSnapshotCatalog(directory);
        assertEquals(totah.lab.mnemosyne.EvidenceSnapshotCatalog.Status.STORED,catalog.seed(snapshot,pin).status());
        assertThrows(MolecularBackendException.class,()->OclMolecularBackend.forExplicitRadicalState().identify(graph("unknown")));
        var read=catalog.read(pin).orElseThrow();assertArrayEquals(bytes,exchange.encode(read));
        for(var e:history.envelopes().values())assertArrayEquals(e.readPayload(),read.history().envelopes().get(e.reference()).readPayload());
        return bytes;
    }
    @Test void unsupportedOriginalEvidenceSurvivesDurableReadBack(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)throws Exception { journal(directory); }
    public static void main(String[] args)throws Exception { java.nio.file.Files.write(java.nio.file.Path.of(args[1]),journal(java.nio.file.Path.of(args[0]))); }

}
