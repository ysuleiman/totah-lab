package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen benchmark structures and minimal reproductions; no chemical normalization or stereo extension. */
class CorrectedOclBenchmarkChemistryRegressionTest {
    private static final Set<String> ROUNDTRIP = Set.of("CHEMBL3718162","CHEMBL3717282","CHEMBL3717763");
    private final OclMolecularBackend backend = OclMolecularBackend.forChemicalStateValidation();
    private StereoMolecule parse(String smiles) throws Exception {
        var parser = new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMILES); parser.setRandomSeed(1L);
        var m = parser.parseMolecule(smiles); m.ensureHelperArrays(Molecule.cHelperCIP); return m;
    }
    private MolecularGraph graph(StereoMolecule molecule) throws Exception {
        var ids = new LinkedHashMap<Integer,String>();
        for(int i=0;i<molecule.getAllAtoms();i++) { molecule.setAtomMapNo(i,i+1,false); ids.put(i+1,"source-atom-"+i); }
        return new OclGraphMapper().fromOcl(new OclGraphMapper.Mapping(molecule,
                new MolecularGraph(List.of(),List.of(),Map.of()),ids),molecule);
    }
    private void assertLossless(String smiles) throws Exception {
        var parsed = parse(smiles); String identity = new Canonizer(parsed).getIDCode();
        var g = backend.decodeStructure("SMILES",smiles);
        var mapped = new OclGraphMapper().toOcl(g).molecule(); mapped.ensureHelperArrays(Molecule.cHelperCIP);
        assertEquals("OCL_IDCODE:"+identity,backend.identify(g).canonicalKey());
        assertEquals(parsed.getAllAtoms(),mapped.getAllAtoms()); assertEquals(parsed.getAllBonds(),mapped.getAllBonds());
        for(int i=0;i<parsed.getAllAtoms();i++) {
            assertEquals(parsed.getAtomicNo(i),mapped.getAtomicNo(i));
            assertEquals(parsed.getAtomCharge(i),mapped.getAtomCharge(i));
            assertEquals(parsed.getAtomMass(i),mapped.getAtomMass(i));
            assertEquals(parsed.getImplicitHydrogens(i),mapped.getImplicitHydrogens(i),"H at source atom "+i+" in "+smiles);
            assertEquals(parsed.isAromaticAtom(i),mapped.isAromaticAtom(i));
            assertEquals(parsed.getAtomCIPParity(i),mapped.getAtomCIPParity(i));
        }
        for(int i=0;i<parsed.getAllBonds();i++) {
            assertEquals(parsed.getBondAtom(0,i),mapped.getBondAtom(0,i));
            assertEquals(parsed.getBondAtom(1,i),mapped.getBondAtom(1,i));
            assertEquals(parsed.isAromaticBond(i),mapped.isAromaticBond(i));
            if(!parsed.isAromaticBond(i)) assertEquals(parsed.getBondOrder(i),mapped.getBondOrder(i));
        }
        assertEquals(g,backend.sanitize(g,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)).graph());
        var codec = new ObjectMapper();
        var replay = codec.readValue(codec.writeValueAsBytes(g),MolecularGraph.class);
        assertEquals(g,replay); assertEquals(backend.identify(g).canonicalKey(),backend.identify(replay).canonicalKey());
    }
    @Test void minimalHeteroaromaticReproductionsPreserveIdentityHydrogensAndOrdering() throws Exception {
        for(String smiles:List.of("c1ccsc1","c1ccc2sccc2c1","c1cnoc1","s1cccc1","o1nccc1",
                "[13CH3]c1ccsc1","c1ccccc1","n1ccccc1")) assertLossless(smiles);
    }
    @Test void allThreeFrozenFailuresRecoverWhileAllEighteenStereoExclusionsRemain() throws Exception {
        var inputs = new TreeMap<String,String>();
        try(var stream=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/mmp/chembl32-maximal-CHEMBL3714130.csv.gz")));
            var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            reader.readLine();String line;while((line=reader.readLine())!=null) { var r=line.split(","); inputs.put(r[1],r[2]); }
        }
        var diagnostics = new ArrayList<Map<String,Object>>(); int stereoExcluded=0;
        for(var input:inputs.entrySet()) {
            var parsed=parse(input.getValue()); var g=graph(parsed);
            var rebuilt=new OclGraphMapper().toOcl(g).molecule(); rebuilt.ensureHelperArrays(Molecule.cHelperCIP);
            int external=0,internal=0;
            for(int b=0;b<parsed.getAllBonds();b++) if(parsed.getBondParity(b)!=Molecule.cBondParityNone) {
                if(parsed.isSmallRingBond(b)) { internal++;assertEquals(Molecule.cBondCIPParityNone,parsed.getBondCIPParity(b)); }
                else {
                    external++; assertEquals(2,parsed.getBondOrder(b));
                    assertEquals(Set.of("C","N"),Set.of(parsed.getAtomLabel(parsed.getBondAtom(0,b)),parsed.getAtomLabel(parsed.getBondAtom(1,b))));
                    assertEquals(Molecule.cBondCIPParityZorM,parsed.getBondCIPParity(b));
                    assertEquals("UNSPECIFIED",g.bonds().get(b).stereochemistry());
                }
            }
            if(ROUNDTRIP.contains(input.getKey())) { assertEquals(0,external); assertLossless(input.getValue()); }
            if(external>0) {
                stereoExcluded++;assertEquals(1,external);assertEquals(1,internal);
                var error=assertThrows(MolecularBackendException.class,()->backend.decodeStructure("SMILES",input.getValue()));
                assertTrue(error.getMessage().contains("source bond stereochemistry"));
                assertNotEquals(new Canonizer(parsed).getIDCode(),new Canonizer(rebuilt).getIDCode(),"unguarded mapping must expose stereo loss");
                // A serialization layer cannot recover stereo already discarded by the mapper.
                var codec=new ObjectMapper(); assertEquals(g,codec.readValue(codec.writeValueAsBytes(g),MolecularGraph.class));
            }
            if(external>0 || ROUNDTRIP.contains(input.getKey())) {
                var row=new LinkedHashMap<String,Object>();row.put("compound",input.getKey());row.put("input",input.getValue());
                row.put("parsed",snapshot(parsed));row.put("graph",g);row.put("unguardedRebuilt",snapshot(rebuilt));
                row.put("status",external>0?"REJECTED_UNSUPPORTED_EXOCYCLIC_IMINE_Z":"LOSSLESS_AFTER_AROMATIC_RESOLUTION");diagnostics.add(row);
            }
        }
        assertEquals(18,stereoExcluded);
        Files.createDirectories(Path.of("target"));
        try(var output=Files.newOutputStream(Path.of("target/mmp-chemistry-diagnostics.json"))) {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output,diagnostics);
        }
    }
    @Test void toolkitDistinguishesBothImineStereoisomersButProjectStillRejectsBoth() throws Exception {
        var z=parse("C/N=C(/S)N");var e=parse("C/N=C(\\S)N");
        assertNotEquals(new Canonizer(z).getIDCode(),new Canonizer(e).getIDCode());
        for(String smiles:List.of("C/N=C(/S)N","C/N=C(\\S)N"))
            assertThrows(MolecularBackendException.class,()->backend.decodeStructure("SMILES",smiles));
        var ordinary=backend.decodeStructure("SMILES","C=C");var bonds=new ArrayList<>(ordinary.bonds());
        for(int i=0;i<bonds.size();i++) if(bonds.get(i).order()==MolecularGraph.BondOrder.DOUBLE) {
            var b=bonds.get(i);bonds.set(i,new MolecularGraph.Bond(b.id(),b.firstAtomId(),b.secondAtomId(),b.order(),b.aromatic(),"E",b.properties()));
        }
        assertThrows(MolecularBackendException.class,()->backend.identify(new MolecularGraph(ordinary.atoms(),bonds,ordinary.properties())));
    }
    private Map<String,Object> snapshot(StereoMolecule m) {
        m.ensureHelperArrays(Molecule.cHelperCIP);var atoms=new ArrayList<Map<String,Object>>();var bonds=new ArrayList<Map<String,Object>>();
        for(int i=0;i<m.getAllAtoms();i++) atoms.add(Map.of("index",i,"element",m.getAtomLabel(i),"charge",m.getAtomCharge(i),
                "mass",m.getAtomMass(i),"implicitH",m.getImplicitHydrogens(i),"aromatic",m.isAromaticAtom(i),"parity",m.getAtomParity(i)));
        for(int i=0;i<m.getAllBonds();i++) bonds.add(Map.of("index",i,"first",m.getBondAtom(0,i),"second",m.getBondAtom(1,i),
                "type",m.getBondType(i),"order",m.getBondOrder(i),"aromatic",m.isAromaticBond(i),"parity",m.getBondParity(i),"cip",m.getBondCIPParity(i),"smallRing",m.isSmallRingBond(i)));
        return Map.of("idcode",new Canonizer(m).getIDCode(),"isomericSmiles",new IsomericSmilesCreator(m).getSmiles(),"atoms",atoms,"bonds",bonds);
    }
}
