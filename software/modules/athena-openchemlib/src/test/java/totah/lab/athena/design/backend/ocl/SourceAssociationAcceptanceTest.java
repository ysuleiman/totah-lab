package totah.lab.athena.design.backend.ocl;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularBackendException;
import static org.junit.jupiter.api.Assertions.*;
class SourceAssociationAcceptanceTest {
    private final OclMolecularBackend backend=new OclMolecularBackend();
    @Test void uniqueIdentityUsesExistingMapperAndCorrespondence()throws Exception {
        var a=backend.decodeStructure("SMILES","CCO");var b=backend.decodeStructure("SMILES","OCC");
        assertTrue(backend.associate(a,b).proven());assertTrue(backend.associate(a,b).correspondence().selected().completeFor(a,b));
    }
    @Test void mismatchAndSymmetryFailClosed()throws Exception {
        assertFalse(backend.associate(backend.decodeStructure("SMILES","CCO"),backend.decodeStructure("SMILES","CCS")).proven());
        var symmetric=backend.decodeStructure("SMILES","CCC");assertFalse(backend.associate(symmetric,symmetric).proven());
    }
    @Test void chargeIsotopeAndStereoAreNotErased()throws Exception {
        for(String smiles:new String[]{"[13CH3]CO","CC[O-]","[NH3+]CC[O-]","C[C@H](O)F"}) {
            var a=backend.decodeStructure("SMILES",smiles);assertTrue(backend.associate(a,a).proven());
            assertNotEquals(backend.identify(a).canonicalKey(),backend.identify(backend.decodeStructure("SMILES","CCO")).canonicalKey());
        }
        assertNotEquals(backend.identify(backend.decodeStructure("SMILES","C[C@H](O)F")).canonicalKey(),backend.identify(backend.decodeStructure("SMILES","C[C@@H](O)F")).canonicalKey());
    }
    @Test void concreteChargeIsotopeAndAromaticAnnotationsSurviveDeterministically() throws Exception {
        var ion=backend.decodeStructure("SMILES","CC[O-]");
        assertEquals(-1,ion.atoms().stream().mapToInt(a->a.formalCharge()).sum());
        assertTrue(backend.decodeStructure("SMILES","[13CH3]CO").atoms().stream().anyMatch(a->Integer.valueOf(13).equals(a.isotope())));
        var aromatic=backend.decodeStructure("SMILES","c1ccccc1O");
        assertEquals(6,aromatic.atoms().stream().filter(a->a.aromatic()).count());
        assertEquals(aromatic,backend.decodeStructure("SMILES","c1ccccc1O"));
    }
    @Test void unsupportedFormatsStereoAndDisconnectedInputDecline() {
        assertThrows(MolecularBackendException.class,()->backend.decodeStructure("UNKNOWN","CCO"));
        assertThrows(MolecularBackendException.class,()->backend.decodeStructure("SMILES","C.C"));
        assertThrows(MolecularBackendException.class,()->backend.decodeStructure("SMILES","F/C=C/F"));
    }
}
