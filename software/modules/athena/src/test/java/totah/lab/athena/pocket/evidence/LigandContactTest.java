package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.compare.residue.ResidueReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LigandContactTest {

    private static final ResidueReference RESIDUE =
            new ResidueReference("A", 145, ' ', "LEU");

    @Test
    void availableContactCarriesAllDimensions() {
        LigandContact contact = LigandContact.available(
                "42",
                "SAM",
                RESIDUE,
                2.54,
                LigandContactType.DIRECT,
                "BIOHUB"
        );

        assertEquals(LigandContactStatus.AVAILABLE, contact.status());
        assertEquals("42", contact.pocketReference());
        assertEquals("SAM", contact.ligandCcd());
        assertEquals(RESIDUE, contact.residue());
        assertEquals(2.54, contact.minimumDistance());
        assertEquals(LigandContactType.DIRECT, contact.contactType());
        assertEquals("BIOHUB", contact.evidenceSource());
    }

    @Test
    void availableContactAllowsAMissingDistance() {
        LigandContact contact = LigandContact.available(
                "42",
                "SAM",
                RESIDUE,
                null,
                LigandContactType.SHELL,
                "BIOHUB"
        );

        assertNull(contact.minimumDistance());
    }

    @Test
    void availableContactRequiresResidueAndContactType() {
        assertThrows(
                NullPointerException.class,
                () -> LigandContact.available(
                        "42",
                        "SAM",
                        null,
                        2.5,
                        LigandContactType.DIRECT,
                        "BIOHUB"
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> LigandContact.available(
                        "42",
                        "SAM",
                        RESIDUE,
                        2.5,
                        null,
                        "BIOHUB"
                )
        );
    }

    @Test
    void notAvailableIsAnExplicitMarkerWithoutMeasuredValues() {
        LigandContact contact = LigandContact.notAvailable(
                "42",
                "SAM",
                "BIOHUB"
        );

        assertEquals(
                LigandContactStatus.NOT_AVAILABLE,
                contact.status()
        );
        assertEquals("42", contact.pocketReference());
        assertEquals("SAM", contact.ligandCcd());
        assertEquals("BIOHUB", contact.evidenceSource());
        assertNull(contact.residue());
        assertNull(contact.minimumDistance());
        assertNull(contact.contactType());
    }

    @Test
    void notAvailableAllowsAnUnknownLigand() {
        LigandContact contact = LigandContact.notAvailable(
                "42",
                null,
                "BIOHUB"
        );

        assertEquals(
                LigandContactStatus.NOT_AVAILABLE,
                contact.status()
        );
        assertNull(contact.ligandCcd());
    }

    @Test
    void rejectsNegativeOrNonFiniteDistances() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LigandContact.available(
                        "42",
                        "SAM",
                        RESIDUE,
                        -0.1,
                        LigandContactType.DIRECT,
                        "BIOHUB"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> LigandContact.available(
                        "42",
                        "SAM",
                        RESIDUE,
                        Double.NaN,
                        LigandContactType.DIRECT,
                        "BIOHUB"
                )
        );
    }
}
