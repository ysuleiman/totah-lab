package totah.lab.gaia.chemistry;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormalChargeTest {

    @Test
    void shouldCreateNeutralCharge() {
        FormalCharge charge = FormalCharge.of(0);

        assertSame(FormalCharge.NEUTRAL, charge);
        assertTrue(charge.isNeutral());
        assertFalse(charge.isPositive());
        assertFalse(charge.isNegative());
    }

    @Test
    void shouldCreatePositiveCharge() {
        FormalCharge charge = FormalCharge.of(1);

        assertSame(FormalCharge.POSITIVE_ONE, charge);
        assertTrue(charge.isPositive());
        assertFalse(charge.isNeutral());
        assertFalse(charge.isNegative());
    }

    @Test
    void shouldCreateNegativeCharge() {
        FormalCharge charge = FormalCharge.of(-1);

        assertSame(FormalCharge.NEGATIVE_ONE, charge);
        assertTrue(charge.isNegative());
        assertFalse(charge.isNeutral());
        assertFalse(charge.isPositive());
    }

    @Test
    void shouldSupportChargesGreaterThanOne() {
        FormalCharge charge = FormalCharge.of(2);

        assertEquals(2, charge.value());
        assertTrue(charge.isPositive());
    }

    @Test
    void shouldAddCharges() {
        FormalCharge result =
                FormalCharge.of(-1)
                        .add(FormalCharge.of(2));

        assertEquals(FormalCharge.POSITIVE_ONE, result);
    }

    @Test
    void shouldRejectNullWhenAdding() {
        assertThrows(
                NullPointerException.class,
                () -> FormalCharge.NEUTRAL.add(null));
    }

    @Test
    void shouldNegateCharge() {
        assertEquals(
                FormalCharge.NEGATIVE_ONE,
                FormalCharge.POSITIVE_ONE.negate());

        assertEquals(
                FormalCharge.POSITIVE_ONE,
                FormalCharge.NEGATIVE_ONE.negate());

        assertEquals(
                FormalCharge.NEUTRAL,
                FormalCharge.NEUTRAL.negate());
    }

    @Test
    void shouldFormatCharge() {
        assertEquals("0", FormalCharge.NEUTRAL.toString());
        assertEquals("+1", FormalCharge.POSITIVE_ONE.toString());
        assertEquals("-1", FormalCharge.NEGATIVE_ONE.toString());
        assertEquals("+2", FormalCharge.of(2).toString());
    }
}