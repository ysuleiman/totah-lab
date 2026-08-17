package totah.lab.athena.tmt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SulfurStateTest {
    @Test
    void protonationStatesAreDistinctChemicalStates() {
        SulfurState thiol = new SulfurState("captopril", SulfurSpecies.RSH, "captopril-rsh");
        SulfurState thiolate = new SulfurState("captopril", SulfurSpecies.RS_MINUS, "captopril-rs-minus");

        assertNotEquals(thiol, thiolate);
        assertEquals(0, thiol.species().formalCharge());
        assertEquals(-1, thiolate.species().formalCharge());
    }
}
