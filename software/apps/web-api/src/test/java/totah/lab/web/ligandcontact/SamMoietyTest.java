package totah.lab.web.ligandcontact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamMoietyTest {

    @Test
    void classifiesEveryLigandAtomName() {
        assertEquals(SamMoiety.ADENINE,
                SamMoiety.classify("N1").orElseThrow());
        assertEquals(SamMoiety.ADENINE,
                SamMoiety.classify("C8").orElseThrow());
        assertEquals(SamMoiety.RIBOSE,
                SamMoiety.classify("C5'").orElseThrow());
        assertEquals(SamMoiety.RIBOSE,
                SamMoiety.classify("O2'").orElseThrow());
        assertEquals(SamMoiety.SULFONIUM,
                SamMoiety.classify("SD").orElseThrow());
        assertEquals(SamMoiety.SULFONIUM,
                SamMoiety.classify("CE").orElseThrow());
        assertEquals(SamMoiety.METHIONINE,
                SamMoiety.classify("CA").orElseThrow());
        assertEquals(SamMoiety.METHIONINE,
                SamMoiety.classify("CG").orElseThrow());
    }

    @Test
    void trimsAndRejectsUnknownNames() {
        assertEquals(SamMoiety.METHIONINE,
                SamMoiety.classify(" CA ").orElseThrow());
        assertTrue(SamMoiety.classify("P").isEmpty());
        assertTrue(SamMoiety.classify(null).isEmpty());
    }
}
