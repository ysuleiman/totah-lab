package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.*;
import com.actelion.research.chem.mmp.MMPFragmenter;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.knowledge.MatchedPairExtractor.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OclMatchedPairExtractorTest {
    private final OclMolecularBackend backend = new OclMolecularBackend();
    private final OclMatchedPairExtractor extractor = new OclMatchedPairExtractor();
    private Source source(String id, String smiles) throws Exception {
        return new Source(id, "fixture/v1", backend.decodeStructure("SMILES", smiles), List.of("observation:"+id));
    }
    @Test void singleCutPairsPreservePartitionsAnchorsAndEveryCoreMapping() throws Exception {
        var result = extractor.extract(List.of(source("a", "Oc1ccccc1"), source("b", "Nc1ccccc1")), 8);
        assertFalse(result.pairs().isEmpty(), result.issues().toString());
        for (var p : result.pairs()) {
            assertEquals(p.left().graph().atoms().size(), p.leftFragment().constantAtoms().size()+p.leftFragment().variableAtoms().size());
            assertTrue(p.coreCorrespondence().exhaustive());
            for (var map : p.coreCorrespondence().alternatives()) {
                assertEquals(p.rightFragment().constantAnchor(), map.atoms().get(p.leftFragment().constantAnchor()));
                assertEquals(p.leftFragment().constantAtoms(), map.atoms().keySet());
                assertEquals(p.rightFragment().constantAtoms(), new HashSet<>(map.atoms().values()));
            }
            assertTrue(p.coreCorrespondence().ambiguous(), "phenyl mirror symmetry must survive");
        }
    }
    @Test void orderAndIdentityAreNotLineage() throws Exception {
        var a=source("a","Oc1ccccc1"); var b=source("b","Nc1ccccc1");
        var atoms=new ArrayList<>(a.graph().atoms()); Collections.reverse(atoms);
        var bonds=new ArrayList<>(a.graph().bonds()); Collections.reverse(bonds);
        var reordered=new Source(a.id(),a.dataset(),new MolecularGraph(atoms,bonds,a.graph().properties()),a.observationReferences());
        assertEquals(extractor.extract(List.of(a,b),8),extractor.extract(List.of(a,b),8));
        var p=extractor.extract(List.of(a,b),8).pairs(); var q=extractor.extract(List.of(b,reordered),8).pairs();
        assertEquals(p.stream().map(Pair::transformation).toList(),q.stream().map(Pair::transformation).toList());
        assertEquals(p.getFirst().leftFragment(),q.getFirst().leftFragment());
        assertTrue(extractor.extract(List.of(a,new Source("duplicate",a.dataset(),a.graph(),List.of())),8).pairs().isEmpty());
    }
    @Test void toolkitCharacterizationCoversDoubleCutsAndSensitiveChemistry() throws Exception {
        for(String smiles:List.of("c1ccccc1CCc1ccccc1","[13CH3]c1ccccc1","[NH3+]CCc1ccccc1","[nH]1cccc1","N[C@@H](C)c1ccccc1","F/C=C/c1ccccc1")) {
            var m=new SmilesParser().parseMolecule(smiles); String before=new Canonizer(m).getIDCode();
            var f=new MMPFragmenter(new StereoMolecule(m)); var fragments=f.getMoleculeIndexesID(false);
            assertEquals(before,new Canonizer(m).getIDCode(),"source must not be mutated");
            if (smiles.equals("[nH]1cccc1")) assertTrue(fragments.isEmpty(),"ring bonds are not eligible cuts");
            else assertFalse(fragments.isEmpty(),smiles);
            if(smiles.equals("c1ccccc1CCc1ccccc1")) assertTrue(fragments.stream().anyMatch(x->x.getKeysID().length==2));
            var pieces = new ArrayList<StereoMolecule>();
            for (var row : fragments) {
                pieces.add(new IDCodeParser().getCompactMolecule(row.getValueID()));
                for (String key : row.getKeysID()) pieces.add(new IDCodeParser().getCompactMolecule(key));
            }
            if(smiles.contains("NH3+")) assertTrue(pieces.stream().anyMatch(v -> java.util.stream.IntStream.range(0,v.getAtoms()).anyMatch(i -> v.getAtomCharge(i)==1)));
            if(smiles.contains("@@")) assertTrue(pieces.stream().anyMatch(v -> {v.ensureHelperArrays(Molecule.cHelperCIP);return java.util.stream.IntStream.range(0,v.getAtoms()).anyMatch(i -> v.getAtomParity(i)!=Molecule.cAtomParityNone);}));
            if(smiles.contains("F/C")) assertTrue(pieces.stream().anyMatch(v -> {v.ensureHelperArrays(Molecule.cHelperCIP);return java.util.stream.IntStream.range(0,v.getBonds()).anyMatch(i -> v.getBondParity(i)!=Molecule.cBondParityNone);}));
            if(smiles.contains("13CH3")) assertTrue(fragments.stream().anyMatch(x->{var v=new IDCodeParser().getCompactMolecule(x.getValueID());for(int i=0;i<v.getAtoms();i++)if(v.getAtomMass(i)==13)return true;return false;}));
        }
    }
    @Test void pinnedMmpdbOverlappingSingleCutScopeAgreesOnMolecularPairs() throws Exception {
        var sources=new ArrayList<Source>();
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/mmp/mmpdb-test_data.smi"),java.nio.charset.StandardCharsets.UTF_8))) {
            String line;while((line=reader.readLine())!=null){var x=line.split(" ",2);sources.add(source(x[1],x[0]));}
        }
        var expected=new TreeSet<String>();
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/mmp/mmpdb-single-cut-pairs.tsv"),java.nio.charset.StandardCharsets.UTF_8))) {
            reader.readLine();String line;while((line=reader.readLine())!=null)expected.add(line);
        }
        var result=extractor.extract(sources,8);var actual=new TreeSet<String>();
        for(var p:result.pairs()){var ids=new ArrayList<>(List.of(p.left().id(),p.right().id()));Collections.sort(ids);actual.add(String.join("\t",ids));}
        assertEquals(expected,actual,result.issues().toString());
    }
    @Test void unsupportedChemistryAndLimitsAreExplicit() throws Exception {
        assertThrows(MolecularBackendException.class,()->backend.decodeStructure("SMILES","F/C=C/c1ccccc1"));
        var a=source("a","Oc1ccccc1");
        var disconnected=new MolecularGraph(a.graph().atoms(),List.of(),Map.of());
        assertTrue(extractor.extract(List.of(new Source("salt","test",disconnected,List.of())),8).issues().stream().anyMatch(x->x.reason().contains("disconnected")));
        assertThrows(IllegalArgumentException.class,()->extractor.extract(List.of(a,a),8));
        assertThrows(IllegalArgumentException.class,()->extractor.extract(List.of(a),17));
        var large=source("large","c1ccccc1CCCCC");
        assertTrue(extractor.extract(List.of(large),1).issues().stream().anyMatch(i->i.reason().equals("VARIABLE_SIZE_EXCLUDED")));
        var linker=source("linker","c1ccccc1CCc1ccccc1");
        assertTrue(extractor.extract(List.of(linker),8).issues().stream().anyMatch(i->i.reason().equals("DOUBLE_CUT_AVAILABLE_BUT_MAPPING_NOT_QUALIFIED")));
        var annotated=new ArrayList<>(a.graph().atoms());var x=annotated.getFirst();
        annotated.set(0,new MolecularGraph.Atom(x.id(),x.element(),x.isotope(),x.formalCharge(),9,x.aromatic(),x.stereochemistry(),x.coordinates(),x.properties()));
        assertTrue(extractor.extract(List.of(new Source("h","test",new MolecularGraph(annotated,a.graph().bonds(),Map.of()),List.of())),8).issues().stream().anyMatch(i->i.reason().contains("hydrogen")));
    }
}
