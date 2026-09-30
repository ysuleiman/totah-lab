package totah.lab.aether;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.integral.BoysF0;
import totah.lab.aether.provenance.ContentHash;

import static org.junit.jupiter.api.Assertions.*;

class AetherBoysF0Test {
    @ParameterizedTest
    @CsvFileSource(resources = "/totah/lab/aether/reference/boys-f0.csv", numLinesToSkip = 1)
    void independent80DigitReference(double t, double expected) {
        double actual = BoysF0.value(t);
        assertEquals(expected, actual, expected * 3e-15);
        assertTrue(actual > 0 && actual <= 1);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, -0.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidArgumentsFail(double t) {
        assertThrows(IllegalArgumentException.class, () -> BoysF0.value(t));
    }

    @Test
    void limitsAndBranchContinuity() {
        assertEquals(1, BoysF0.value(0));
        assertEquals(1, BoysF0.value(-0.0));
        assertEquals(1, BoysF0.value(Double.MIN_VALUE));
        // First-order limit: allow one ulp for rounding plus the omitted t^2/10 term.
        assertEquals(1 - 1e-8 / 3, BoysF0.value(1e-8), StrictMath.ulp(1.0));
        for (double t : new double[]{0.5, 36}) {
            double below = BoysF0.value(StrictMath.nextDown(t));
            double above = BoysF0.value(StrictMath.nextUp(t));
            assertEquals(below, above, 3e-15 * below);
        }
        double previous = 1;
        for (double t : new double[]{1e-12, 0.01, 0.5, 1, 10, 35, 36, 100, 1e6}) {
            double current = BoysF0.value(t);
            assertTrue(current <= previous);
            previous = current;
        }
        assertEquals(StrictMath.sqrt(StrictMath.PI) / 2, BoysF0.value(1e100) * 1e50, 2e-16);
    }

    @Test
    void referenceFixtureHash() throws IOException {
        try (var input = getClass().getResourceAsStream("reference/boys-f0.csv")) {
            assertNotNull(input);
            assertEquals("ea3194a585afe0e6f981dacdccde781d7def5c925a51213d2e12ae0d4a04cfa5", ContentHash.sha256(input.readAllBytes()));
        }
    }
}
