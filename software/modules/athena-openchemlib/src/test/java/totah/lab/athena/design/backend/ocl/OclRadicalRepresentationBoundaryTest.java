package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** V03 synthetic representation witnesses; no radical reactivity or stability claim. */
class OclRadicalRepresentationBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings={"[CH3]","[CH2]","[O]"})
    void radicalSourceIsRejectedRatherThanSilentlyLosingState(String source)throws Exception {
        var molecule=new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMILES).parseMolecule(source);
        assertNotEquals(Molecule.cAtomRadicalStateNone,molecule.getAtomRadical(0));
        String original=new Canonizer(molecule).getIDCode();
        var backend=new OclMolecularBackend();
        var error=assertThrows(MolecularBackendException.class,()->backend.decodeStructure("SMILES",source));
        assertEquals("source chemistry cannot be faithfully represented by current backend",error.getMessage());
        assertEquals(original,new Canonizer(molecule).getIDCode());
        // Demonstrate why the public decoder's existing round-trip guard is necessary.
        molecule.setAtomMapNo(0,1,false);
        var mapper=new OclGraphMapper();
        var graph=mapper.fromOcl(new OclGraphMapper.Mapping(molecule,new MolecularGraph(List.of(),List.of(),Map.of()),Map.of(1,"s0")),molecule);
        assertNotEquals(original,new Canonizer(mapper.toOcl(graph).molecule()).getIDCode());
    }

    @ParameterizedTest
    @ValueSource(strings={"C","[13CH4]","[Na+]"})
    void ordinarySourceRemainsRepresentable(String source)throws Exception {
        var backend=new OclMolecularBackend();var graph=backend.decodeStructure("SMILES",source);
        var original=new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMILES).parseMolecule(source);
        assertEquals("OCL_IDCODE:"+new Canonizer(original).getIDCode(),backend.identify(graph).canonicalKey());
        assertEquals(source.equals("[13CH4]")?Integer.valueOf(13):null,graph.atoms().getFirst().isotope());
        assertEquals(source.equals("[Na+]")?1:0,graph.atoms().getFirst().formalCharge());
    }
}
